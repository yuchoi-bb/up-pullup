package com.pullup.tracker.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pullup.tracker.PullupApplication
import com.pullup.tracker.ai.CoachPrompts
import com.pullup.tracker.ai.GeneratedPlan
import com.pullup.tracker.data.AppSettings
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanGenerator
import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.data.SetEntry
import com.pullup.tracker.data.TaskListRef
import com.pullup.tracker.data.TrainingPlan
import com.pullup.tracker.reminder.ReminderScheduler
import com.pullup.tracker.update.ReleaseInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class UiMessage(val text: String, val isError: Boolean = false, val id: Long = System.nanoTime())

data class UpdateUiState(
    val checking: Boolean = false,
    val release: ReleaseInfo? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedFile: File? = null,
    val error: String? = null,
    val checkedOnce: Boolean = false
)

data class CoachUiState(
    val busy: Boolean = false,
    val generated: GeneratedPlan? = null,
    val answer: String = "",
    val error: String? = null,
    val availableModels: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PullupApplication).container

    val data = container.repository.data
    val settings = container.settings.settings
    val googleStatus = container.google.status

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _repsInput = MutableStateFlow<List<Int>>(emptyList())
    val repsInput: StateFlow<List<Int>> = _repsInput.asStateFlow()

    private val _setDone = MutableStateFlow<List<Boolean>>(emptyList())
    val setDone: StateFlow<List<Boolean>> = _setDone.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _restRemaining = MutableStateFlow(0)
    val restRemaining: StateFlow<Int> = _restRemaining.asStateFlow()
    private var restJob: Job? = null

    private val _taskLists = MutableStateFlow<List<TaskListRef>>(emptyList())
    val taskLists: StateFlow<List<TaskListRef>> = _taskLists.asStateFlow()

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    private val _coach = MutableStateFlow(CoachUiState())
    val coach: StateFlow<CoachUiState> = _coach.asStateFlow()

    private var loadedSessionKey: String? = null

    // ------------------------------------------------------------ 오늘 화면

    val plan: TrainingPlan? get() = container.repository.planOf(data.value)

    fun currentSession(): PlanSession? = container.repository.currentSession(data.value, plan)

    fun sessionPosition(): Int = container.repository.currentSessionPosition(data.value, plan)

    fun lastLog(): SessionLog? = data.value.logs.maxByOrNull { it.recordedAt }

    fun todayLog(): SessionLog? {
        val today = LocalDate.now().toString()
        return data.value.logs.filter { it.date == today }.maxByOrNull { it.recordedAt }
    }

    /** 목표 세션이 바뀌면 입력값을 목표치로 초기화한다. */
    fun prepareInputs(session: PlanSession?) {
        val key = "${plan?.id}#${session?.index}#${sessionPosition()}"
        if (key == loadedSessionKey) return
        loadedSessionKey = key
        _repsInput.value = session?.targets.orEmpty()
        _setDone.value = List(session?.targets?.size ?: 0) { false }
        _note.value = ""
    }

    fun setReps(index: Int, value: Int) {
        _repsInput.value = _repsInput.value.toMutableList().also {
            if (index in it.indices) it[index] = value.coerceIn(0, 999)
        }
    }

    fun setNote(value: String) {
        _note.value = value
    }

    fun toggleSet(index: Int) {
        val list = _setDone.value.toMutableList()
        if (index !in list.indices) return
        val nowDone = !list[index]
        list[index] = nowDone
        _setDone.value = list
        if (nowDone && list.any { !it }) startRest() else if (!nowDone) stopRest()
    }

    fun startRest(seconds: Int = settings.value.restSeconds) {
        restJob?.cancel()
        _restRemaining.value = seconds
        restJob = viewModelScope.launch {
            while (_restRemaining.value > 0) {
                delay(1000)
                _restRemaining.value = _restRemaining.value - 1
            }
        }
    }

    fun stopRest() {
        restJob?.cancel()
        _restRemaining.value = 0
    }

    /** 세션을 기록하고, 설정에 따라 Google Tasks에 완료 항목으로 올린다. */
    fun completeSession() {
        val currentPlan = plan ?: return
        val session = currentSession() ?: return
        val reps = _repsInput.value
        if (reps.isEmpty()) return
        val sets = session.targets.mapIndexed { i, target -> SetEntry(target, reps.getOrElse(i) { 0 }) }

        viewModelScope.launch {
            _busy.value = true
            val log = container.repository.recordSession(
                plan = currentPlan,
                session = session,
                sets = sets,
                note = _note.value,
                autoRegulate = settings.value.autoRegulate
            )
            stopRest()
            loadedSessionKey = null
            _message.value = UiMessage("총 ${log.total}개 기록 완료!")

            if (settings.value.autoSync && googleStatus.value.signedIn && settings.value.taskListId != null) {
                syncLogInternal(log)
            }
            _busy.value = false
        }
    }

    fun syncLog(logId: String) {
        val log = data.value.logs.firstOrNull { it.id == logId } ?: return
        viewModelScope.launch {
            _busy.value = true
            syncLogInternal(log)
            _busy.value = false
        }
    }

    private suspend fun syncLogInternal(log: SessionLog) {
        val current = settings.value
        val listId = current.taskListId
        if (listId == null) {
            container.repository.attachTask(log.id, null, null, null, "할 일 목록이 선택되지 않았습니다.")
            _message.value = UiMessage("설정에서 Google 할 일 목록을 먼저 골라 주세요.", isError = true)
            return
        }
        runCatching {
            val token = container.google.accessToken()
            val title = renderTitle(current.taskTitleTemplate, log)
            val notes = buildString {
                append("세트: ${log.sets.joinToString(" / ") { "${it.done}/${it.target}" }}\n")
                append("총 ${log.total}개 (목표 ${log.targetTotal}개)")
                if (log.note.isNotBlank()) append("\n메모: ${log.note}")
                append("\n\n업풀업 앱에서 기록")
            }
            container.tasks.createCompletedTask(
                accessToken = token,
                taskListId = listId,
                title = title,
                notes = notes,
                date = runCatching { LocalDate.parse(log.date) }.getOrDefault(LocalDate.now())
            )
        }.onSuccess { taskId ->
            container.repository.attachTask(log.id, taskId, listId, current.taskListTitle, null)
            _message.value = UiMessage("Google 할 일 '${current.taskListTitle ?: listId}'에 완료로 저장했습니다.")
        }.onFailure { error ->
            container.repository.attachTask(log.id, null, listId, current.taskListTitle, error.message)
            _message.value = UiMessage(error.message ?: "Google 동기화에 실패했습니다.", isError = true)
        }
    }

    private fun renderTitle(template: String, log: SessionLog): String = template
        .replace("{exercise}", log.exercise)
        .replace("{total}", log.total.toString())
        .replace("{sets}", log.repsText)
        .replace("{date}", log.date)
        .replace("{session}", log.sessionIndex.toString())

    fun deleteLog(logId: String) {
        container.repository.deleteLog(logId)
        loadedSessionKey = null
        _message.value = UiMessage("기록을 삭제했습니다.")
    }

    // ------------------------------------------------------------ Google 연동

    val googleConfigured: Boolean get() = container.google.isConfigured

    fun authorizationIntent(): Intent = container.google.authorizationIntent()

    fun onAuthorizationResult(data: Intent?) {
        if (data == null) {
            _message.value = UiMessage("Google 로그인이 취소되었습니다.", isError = true)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            container.google.handleAuthorizationResult(data)
                .onSuccess {
                    _message.value = UiMessage("Google 계정을 연결했습니다.")
                    refreshTaskLists()
                }
                .onFailure { _message.value = UiMessage(it.message ?: "로그인 실패", isError = true) }
            _busy.value = false
        }
    }

    fun signOut() {
        container.google.signOut()
        _taskLists.value = emptyList()
        _message.value = UiMessage("Google 연결을 해제했습니다.")
    }

    fun refreshTaskLists() {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val token = container.google.accessToken()
                container.tasks.listTaskLists(token)
            }.onSuccess { lists ->
                _taskLists.value = lists
                if (settings.value.taskListId == null && lists.isNotEmpty()) {
                    val guess = lists.firstOrNull { it.title.contains("운동") } ?: lists.first()
                    selectTaskList(guess)
                }
            }.onFailure {
                _message.value = UiMessage(it.message ?: "할 일 목록을 불러오지 못했습니다.", isError = true)
            }
            _busy.value = false
        }
    }

    fun selectTaskList(ref: TaskListRef) =
        container.settings.update { it.copy(taskListId = ref.id, taskListTitle = ref.title) }

    // ------------------------------------------------------------ 설정

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        container.settings.update(transform)
        val current = settings.value
        if (current.reminderEnabled) {
            ReminderScheduler.schedule(getApplication<Application>(), current.reminderHour, current.reminderMinute)
        } else {
            ReminderScheduler.cancel(getApplication<Application>())
        }
    }

    fun setTrainingDays(days: List<Int>) {
        val sorted = days.distinct().sorted()
        container.settings.update { it.copy(trainingDays = sorted) }
        container.repository.updatePlanTrainingDays(sorted)
    }

    fun exportCsv(): File = container.repository.exportCsv()

    fun setActivePlan(planId: String) {
        container.repository.setActivePlan(planId)
        loadedSessionKey = null
    }

    fun deletePlan(planId: String) {
        container.repository.deletePlan(planId)
        loadedSessionKey = null
    }

    // ------------------------------------------------------------ AI 코치

    fun generatePlan(
        exercise: String,
        currentSets: List<Int>,
        goalPerSet: Int,
        daysPerWeek: Int,
        weeks: Int?,
        extraNote: String
    ) {
        val key = settings.value.geminiApiKey
        if (key.isBlank()) {
            _coach.value = _coach.value.copy(error = "설정에서 Gemini API 키를 먼저 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _coach.value = _coach.value.copy(busy = true, error = null, generated = null)
            runCatching {
                val raw = container.gemini.generateText(
                    apiKey = key,
                    model = settings.value.geminiModel,
                    prompt = CoachPrompts.planPrompt(
                        exercise = exercise,
                        currentSets = currentSets,
                        goalPerSet = goalPerSet,
                        setCount = currentSets.size,
                        daysPerWeek = daysPerWeek,
                        weeks = weeks,
                        extraNote = extraNote
                    ),
                    systemInstruction = CoachPrompts.SYSTEM,
                    jsonOutput = true
                )
                CoachPrompts.parsePlan(raw, currentSets.size)
            }.onSuccess {
                _coach.value = _coach.value.copy(busy = false, generated = it)
            }.onFailure {
                _coach.value = _coach.value.copy(busy = false, error = it.message ?: "플랜 생성에 실패했습니다.")
            }
        }
    }

    /** AI 없이 같은 규칙으로 로컬 생성. 키가 없거나 오프라인일 때 쓴다. */
    fun generatePlanLocally(
        name: String,
        exercise: String,
        currentSets: List<Int>,
        goalPerSet: Int,
        weeks: Int?
    ) {
        val plan = PlanGenerator.planFor(
            name = name.ifBlank { "$exercise 플랜" },
            exercise = exercise,
            start = currentSets,
            goalPerSet = goalPerSet,
            trainingDays = settings.value.trainingDays,
            startDate = LocalDate.now(),
            targetWeeks = weeks
        )
        _coach.value = _coach.value.copy(
            generated = GeneratedPlan(
                name = plan.name,
                exercise = exercise,
                goal = plan.goal,
                advice = "앱 내장 규칙으로 만든 플랜입니다. 세션마다 조금씩 늘리고 5번째 세션마다 다지기 날을 둡니다.",
                sessions = plan.sessions
            ),
            error = null
        )
    }

    fun applyGeneratedPlan() {
        val generated = _coach.value.generated ?: return
        val plan = TrainingPlan(
            id = UUID.randomUUID().toString(),
            name = generated.name,
            exercise = generated.exercise.ifBlank { "운동" },
            goal = generated.goal,
            startDate = LocalDate.now().toString(),
            trainingDays = settings.value.trainingDays,
            sessions = generated.sessions,
            createdAt = System.currentTimeMillis(),
            source = TrainingPlan.SOURCE_GEMINI
        )
        container.repository.addPlan(plan)
        loadedSessionKey = null
        _coach.value = _coach.value.copy(generated = null)
        _message.value = UiMessage("'${plan.name}' 플랜을 적용했습니다.")
    }

    fun askCoach(question: String) {
        val key = settings.value.geminiApiKey
        if (key.isBlank()) {
            _coach.value = _coach.value.copy(error = "설정에서 Gemini API 키를 먼저 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _coach.value = _coach.value.copy(busy = true, error = null, answer = "")
            runCatching {
                container.gemini.generateText(
                    apiKey = key,
                    model = settings.value.geminiModel,
                    prompt = CoachPrompts.coachPrompt(
                        plan = plan,
                        next = currentSession(),
                        recentLogs = data.value.logs.sortedByDescending { it.date },
                        question = question
                    ),
                    systemInstruction = CoachPrompts.SYSTEM
                )
            }.onSuccess {
                _coach.value = _coach.value.copy(busy = false, answer = it)
            }.onFailure {
                _coach.value = _coach.value.copy(busy = false, error = it.message ?: "요청에 실패했습니다.")
            }
        }
    }

    fun verifyGeminiKey() {
        val key = settings.value.geminiApiKey
        if (key.isBlank()) {
            _message.value = UiMessage("Gemini API 키를 입력해 주세요.", isError = true)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.gemini.listModels(key) }
                .onSuccess { models ->
                    _coach.value = _coach.value.copy(availableModels = models)
                    _message.value = UiMessage("키 확인 완료 — 사용 가능한 모델 ${models.size}개")
                }
                .onFailure { _message.value = UiMessage(it.message ?: "키 확인 실패", isError = true) }
            _busy.value = false
        }
    }

    fun clearCoachError() {
        _coach.value = _coach.value.copy(error = null)
    }

    // ------------------------------------------------------------ 업데이트

    fun checkUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            _update.value = _update.value.copy(checking = true, error = null)
            runCatching { container.updates.fetchLatest(settings.value.includePrerelease) }
                .onSuccess { release ->
                    _update.value = _update.value.copy(
                        checking = false,
                        release = release,
                        checkedOnce = true,
                        downloadedFile = null,
                        progress = 0f
                    )
                    if (!silent && !release.isNewer) {
                        _message.value = UiMessage("이미 최신 버전입니다.")
                    }
                }
                .onFailure {
                    _update.value = _update.value.copy(
                        checking = false,
                        checkedOnce = true,
                        error = it.message
                    )
                    if (!silent) _message.value = UiMessage(it.message ?: "업데이트 확인 실패", isError = true)
                }
        }
    }

    fun downloadUpdate() {
        val release = _update.value.release ?: return
        viewModelScope.launch {
            _update.value = _update.value.copy(downloading = true, progress = 0f, error = null)
            runCatching {
                container.updates.download(release) { progress ->
                    _update.value = _update.value.copy(progress = progress)
                }
            }.onSuccess { file ->
                _update.value = _update.value.copy(downloading = false, downloadedFile = file, progress = 1f)
                _message.value = UiMessage("다운로드 완료 — 설치를 눌러 주세요.")
            }.onFailure {
                _update.value = _update.value.copy(downloading = false, error = it.message)
                _message.value = UiMessage(it.message ?: "다운로드 실패", isError = true)
            }
        }
    }

    fun canInstallApk(): Boolean = container.updates.canInstall()

    fun installIntent(file: File): Intent = container.updates.installIntent(file)

    fun unknownSourcesIntent(): Intent = container.updates.unknownSourcesSettingsIntent()

    // ------------------------------------------------------------ 공통

    fun consumeMessage() {
        _message.value = null
    }

    fun stats() = container.repository.stats(data.value)

    fun projectedDate(sessionIndex: Int): LocalDate {
        val currentPlan = plan ?: return LocalDate.now()
        val start = runCatching { LocalDate.parse(currentPlan.startDate) }.getOrDefault(LocalDate.now())
        val done = sessionPosition()
        val base = if (sessionIndex <= done) start else LocalDate.now()
        val offset = if (sessionIndex <= done) sessionIndex else sessionIndex - done
        return DateUtils.projectedDate(base, currentPlan.trainingDays, offset)
    }
}
