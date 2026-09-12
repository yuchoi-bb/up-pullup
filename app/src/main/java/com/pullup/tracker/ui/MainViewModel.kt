package com.pullup.tracker.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pullup.tracker.PullupApplication
import com.pullup.tracker.ai.CoachPrompts
import com.pullup.tracker.ai.GeneratedPlan
import com.pullup.tracker.data.AppSettings
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PendingTask
import com.pullup.tracker.data.PlanGenerator
import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.data.SetEntry
import com.pullup.tracker.data.TaskListRef
import com.pullup.tracker.data.TrainingPlan
import com.pullup.tracker.health.HealthConnectRepository
import com.pullup.tracker.health.HealthStatus
import com.pullup.tracker.health.WatchSession
import com.pullup.tracker.reminder.ReminderScheduler
import com.pullup.tracker.update.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * "키 확인" 결과. 스낵바는 4초면 사라져서 놓치기 쉬우니 카드에 그대로 남겨 둔다.
 */
sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data class Ok(val models: Int) : KeyCheck
    data class Failed(val reason: String) : KeyCheck
}

/** 같은 세션을 다시 하는 중일 때 오늘 화면에 띄울 정보. */
data class RetryState(val attempt: Int, val failures: List<SessionLog>) {
    val lastFailure: SessionLog get() = failures.last()
}

/**
 * 워치(가민 등)에서 읽어 온 오늘 운동 기록.
 *
 * 가민은 "운동을 했다"는 세션만 남기고 세트별 횟수는 안 쓴다. 그래서 이건
 * 개수를 채워 주는 기능이 아니라 "오늘 한 거 기록하셨나요" 하고 짚어 주는
 * 용도다. 개수는 사용자가 넣는다.
 */
data class WatchUiState(
    val status: HealthStatus = HealthStatus.UNSUPPORTED,
    val granted: Boolean = false,
    val loading: Boolean = false,
    val sessions: List<WatchSession> = emptyList(),
    val error: String? = null,
    val checkedOnce: Boolean = false
)

data class CoachUiState(
    val busy: Boolean = false,
    val generated: GeneratedPlan? = null,
    val answer: String = "",
    val error: String? = null,
    val availableModels: List<String> = emptyList(),
    val keyCheck: KeyCheck = KeyCheck.Idle
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

    /**
     * busy 플래그를 반드시 되돌린다.
     *
     * 예전에는 블록 끝에서 false로 내렸는데, 중간에 예외가 나면 true로 굳어
     * 버렸다. 설정 화면 버튼이 전부 `enabled = !busy`라서, 그때부터는 눌러도
     * 아무 반응이 없었다(앱을 다시 켜야 풀림).
     */
    private suspend fun <T> withBusy(block: suspend () -> T): T {
        _busy.value = true
        try {
            return block()
        } finally {
            _busy.value = false
        }
    }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

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

    private val _watch = MutableStateFlow(WatchUiState())
    val watch: StateFlow<WatchUiState> = _watch.asStateFlow()

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

    /** "오늘 못 했음" — 전 세트를 0으로. 손으로 다섯 칸 지우지 않게. */
    fun clearReps() {
        _repsInput.value = _repsInput.value.map { 0 }
        _setDone.value = _setDone.value.map { false }
        stopRest()
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
            withBusy {
                val pending = data.value.pendingTask
                val log = container.repository.recordSession(
                    plan = currentPlan,
                    session = session,
                    sets = sets,
                    note = _note.value,
                    autoRegulate = settings.value.autoRegulate
                )
                stopRest()
                loadedSessionKey = null
                _message.value = if (log.failed) {
                    UiMessage(
                        "총 ${log.total}개 — 목표 ${log.targetTotal}개에 ${log.shortfall}개 부족합니다. " +
                            "같은 세션을 다시 합니다.",
                        isError = true
                    )
                } else {
                    UiMessage("총 ${log.total}개 기록 완료!")
                }

                if (settings.value.autoSync && googleStatus.value.signedIn && settings.value.taskListId != null) {
                    _syncing.value = true
                    try {
                        closePendingTask(pending, log)
                        runCatching { ensurePendingTask() }
                    } finally {
                        _syncing.value = false
                    }
                }
            }
        }
    }

    /**
     * 앱에서 운동을 마쳤을 때: Tasks에 올려 둔 "할 일"을 새로 만들지 않고
     * 완료로 바꾼다. 실제로 한 개수로 제목도 고쳐 준다.
     */
    private suspend fun closePendingTask(pending: PendingTask?, log: SessionLog) {
        val current = settings.value
        val listId = current.taskListId ?: return
        runCatching {
            val token = container.google.accessToken()
            val title = completedTitle(current.taskTitleTemplate, log)
            val notes = buildNotes(log)
            if (pending != null && pending.taskListId == listId) {
                container.tasks.completeTask(token, listId, pending.taskId, title, notes)
                pending.taskId
            } else {
                // 올려 둔 항목이 없으면(연동 직후 등) 완료 상태로 새로 만든다.
                container.tasks.createCompletedTask(
                    accessToken = token,
                    taskListId = listId,
                    title = title,
                    notes = notes,
                    date = runCatching { LocalDate.parse(log.date) }.getOrDefault(LocalDate.now())
                )
            }
        }.onSuccess { taskId ->
            container.repository.attachTask(log.id, taskId, listId, current.taskListTitle, null)
            container.repository.setPendingTask(null)
        }.onFailure { error ->
            container.repository.attachTask(log.id, null, listId, current.taskListTitle, error.message)
            _message.value = UiMessage(error.message ?: "Google 동기화에 실패했습니다.", isError = true)
        }
    }

    private fun buildNotes(log: SessionLog): String = buildString {
        append("세트: ${log.sets.joinToString(" / ") { "${it.done}/${it.target}" }}\n")
        append("총 ${log.total}개 (목표 ${log.targetTotal}개)")
        if (log.failed) {
            append("\n결과: 실패 — ${log.shortfall}개 부족. 같은 세션을 다시 합니다.")
        }
        if (log.note.isNotBlank()) append("\n메모: ${log.note}")
        append("\n\n업풀업 앱에서 기록")
    }

    /**
     * 완료로 닫는 항목의 제목. 실패한 시도도 "시도했다"는 사실은 완료로 남기되,
     * 제목만 봐도 실패인 줄 알도록 표시한다. 재도전 항목은 따로 새로 올라간다.
     */
    private fun completedTitle(template: String, log: SessionLog): String {
        val base = renderTitle(template, log)
        return if (log.failed) "❌ $base — 실패 (목표 ${log.targetTotal}개, ${log.shortfall}개 부족)" else base
    }

    // ------------------------------------------------------------ Tasks 양방향 동기화

    /**
     * Google Tasks와 앱 상태를 맞춘다.
     *
     *  1. 올려 둔 할 일이 Tasks에서 체크됐으면 -> 앱에도 완료로 기록하고 플랜을 넘긴다
     *  2. Tasks에서 지워졌으면 -> 참조를 버린다
     *  3. 다음에 할 세션이 아직 안 올라가 있으면 -> 미완료 상태로 하나 올린다
     *
     * Google Tasks는 변경 알림을 보내주지 않아서, 앱을 열 때마다 이 함수를 부른다.
     */
    fun syncWithGoogleTasks(silent: Boolean = true) {
        if (!googleStatus.value.signedIn || settings.value.taskListId == null) return
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching {
                pullCompletionFromTasks()
                ensurePendingTask()
            }.onFailure {
                if (!silent) _message.value = UiMessage(it.message ?: "동기화 실패", isError = true)
            }
            _syncing.value = false
        }
    }

    /** Tasks 쪽에서 체크된 걸 앱 기록으로 가져온다. */
    private suspend fun pullCompletionFromTasks() {
        val pending = data.value.pendingTask ?: return
        val listId = settings.value.taskListId ?: return
        if (pending.taskListId != listId) {
            container.repository.setPendingTask(null)
            return
        }
        val token = container.google.accessToken()
        val remote = container.tasks.fetchTask(token, listId, pending.taskId)
        if (remote == null) {                       // Tasks에서 지움
            container.repository.setPendingTask(null)
            return
        }
        if (!remote.completed) return

        val currentPlan = plan ?: return
        val session = currentSession() ?: return
        if (session.index != pending.sessionIndex) {  // 그새 플랜이 바뀜
            container.repository.setPendingTask(null)
            return
        }
        // Tasks에서는 실제 개수를 알 수 없으므로 목표를 채운 것으로 본다.
        val sets = session.targets.map { SetEntry(it, it) }
        val date = remote.completedAt
            ?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
            ?: LocalDate.now()
        val log = container.repository.recordSession(
            plan = currentPlan,
            session = session,
            sets = sets,
            note = "Google 할 일에서 완료 체크",
            autoRegulate = settings.value.autoRegulate,
            date = date,
            source = SessionLog.SOURCE_TASKS
        )
        container.repository.attachTask(
            log.id, pending.taskId, listId, settings.value.taskListTitle, null
        )
        container.repository.setPendingTask(null)
        loadedSessionKey = null
        _message.value = UiMessage("Google 할 일에서 체크한 ${log.total}개를 기록에 반영했습니다.")
    }

    /** 다음에 할 세션을 Tasks에 미완료 항목으로 올려 둔다(이미 맞는 게 있으면 그대로). */
    /**
     * 다음 할 일의 기한.
     *
     * 오늘 뭐라도 했으면 내일, 아직 안 했으면 오늘. 이 한 줄로 세 가지가 다 풀린다.
     *  - 방금 끝낸 세션 때문에 다음 것이 오늘로 다시 잡히는 문제
     *  - 하루에 여러 세션을 해도 기한이 모레·글피로 밀려나지 않는 것(내일에서 멈춤)
     *  - 며칠 건너뛰어 기한이 지난 항목은 오늘 한 게 없으므로 자동으로 오늘로 당겨짐
     *
     * 운동 요일 설정은 일부러 보지 않는다. 쉬는 요일에도 "내일"로 잡아 달라는 선택.
     */
    /**
     * 다음 세션을 할 날. 오늘 뭐라도 했으면 내일, 아직 안 했으면 오늘.
     *
     * 할 일 기한과 오늘 화면의 카드 제목이 이걸 같이 쓴다. 예전엔 카드가
     * 무조건 "오늘 목표"라, 방금 끝냈는데도 다음 세션을 오늘 또 해야 하는
     * 것처럼 보였다.
     */
    fun nextDueDate(): LocalDate {
        val today = LocalDate.now()
        return DateUtils.nextTaskDue(today, data.value.logs.any { it.date == today.toString() })
    }

    private suspend fun ensurePendingTask() {
        val listId = settings.value.taskListId ?: return
        val currentPlan = plan ?: return
        val session = currentSession() ?: return
        val snapshot = data.value
        val position = container.repository.currentSessionPosition(snapshot, currentPlan)
        val due = nextDueDate()

        // 이 세션을 이미 시도한 적이 있으면 다음은 재도전이다.
        val attempt = container.repository.attemptsOf(snapshot, currentPlan.id, session.index) + 1
        val pastFailures = container.repository.failedAttempts(snapshot, currentPlan.id, session.index)
        val title = renderPlannedTitle(settings.value.taskTitleTemplate, currentPlan, session, due, attempt)
        val notes = plannedNotes(session, pastFailures)

        val existing = snapshot.pendingTask
        val token = container.google.accessToken()

        if (!container.repository.isPendingTaskStale(snapshot, listId) && existing != null) {
            // 목표가 바뀌었거나 기한이 달라졌으면 고쳐 쓴다.
            // 기한 비교가 곧 "밀린 항목을 오늘로 당겨오는" 처리다.
            if (existing.title != title || existing.dueDate != due.toString()) {
                container.tasks.updatePendingTask(token, listId, existing.taskId, title, notes, due)
                container.repository.setPendingTask(existing.copy(title = title, dueDate = due.toString()))
            }
            return
        }

        // 낡은 항목이 남아 있으면 지운다 (사용자 목록에 쓰레기를 남기지 않는다)
        if (existing != null) {
            runCatching { container.tasks.deleteTask(token, existing.taskListId, existing.taskId) }
        }

        val taskId = container.tasks.createPendingTask(
            accessToken = token,
            taskListId = listId,
            title = title,
            notes = notes,
            due = due
        )
        container.repository.setPendingTask(
            PendingTask(
                taskId = taskId,
                taskListId = listId,
                planId = currentPlan.id,
                sessionIndex = session.index,
                position = position,
                title = title,
                dueDate = due.toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 올려 둘 "다음 세션" 제목. 2회차부터는 재도전이라는 걸 제목에 붙인다.
     * 할 일 목록만 봐도 같은 운동을 다시 하는 중이라는 게 보여야 한다.
     */
    private fun renderPlannedTitle(
        template: String,
        plan: TrainingPlan,
        session: PlanSession,
        due: LocalDate,
        attempt: Int
    ): String {
        val base = template
            .replace("{exercise}", plan.exercise)
            .replace("{total}", session.total.toString())
            .replace("{sets}", session.targets.joinToString("/"))
            .replace("{date}", due.toString())
            .replace("{session}", session.index.toString())
        return if (attempt > 1) "$base (재도전 ${attempt}회차)" else base
    }

    private fun plannedNotes(session: PlanSession, pastFailures: List<SessionLog>): String = buildString {
        append("목표: ${session.targets.joinToString(" / ") { "${it}개" }}\n")
        append("합계 ${session.total}개")
        if (session.note.isNotBlank()) append("\n${session.note}")
        if (pastFailures.isNotEmpty()) {
            append("\n\n지난 시도 ${pastFailures.size}번 모두 목표 미달")
            pastFailures.takeLast(5).forEach { log ->
                append("\n- ${DateUtils.short(log.date)} ${log.total}개 (${log.repsText}) — ${log.shortfall}개 부족")
            }
            append("\n채울 때까지 목표는 그대로 둡니다.")
        }
        append("\n\n여기서 체크하면 업풀업 앱 기록에도 반영됩니다.")
    }

    fun syncLog(logId: String) {
        val log = data.value.logs.firstOrNull { it.id == logId } ?: return
        viewModelScope.launch {
            withBusy { syncLogInternal(log) }
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
            val title = completedTitle(current.taskTitleTemplate, log)
            val notes = buildNotes(log)
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

    /** 기록 날짜를 옮긴다(몰아서 입력한 걸 실제 날짜로 되돌릴 때). */
    fun changeLogDate(logId: String, newDate: LocalDate) {
        val log = data.value.logs.firstOrNull { it.id == logId } ?: return
        container.repository.updateLog(log.copy(date = newDate.toString()))
        _message.value = UiMessage("${DateUtils.label(newDate)}로 옮겼습니다.")
    }

    /**
     * 같은 날짜에 몰려 있는 기록을 하루 간격으로 뒤로 펼친다.
     * 한 번에 몰아 입력했지만 실제로는 며칠에 걸쳐 한 경우를 정리하는 용도.
     * 각 묶음에서 가장 최근 세션은 날짜를 그대로 두고, 앞선 세션들을 하루씩 당긴다.
     */
    fun spreadSameDayLogs() {
        val groups = data.value.logs.groupBy { it.date }.filterValues { it.size > 1 }
        if (groups.isEmpty()) {
            _message.value = UiMessage("같은 날짜에 몰린 기록이 없습니다.")
            return
        }
        var moved = 0
        groups.forEach { (date, sameDay) ->
            val base = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@forEach
            val ordered = sameDay.sortedWith(compareBy({ it.sessionIndex }, { it.recordedAt }))
            ordered.forEachIndexed { i, log ->
                val shift = (ordered.size - 1 - i).toLong()
                if (shift > 0L) {
                    container.repository.updateLog(log.copy(date = base.minusDays(shift).toString()))
                    moved++
                }
            }
        }
        _message.value = UiMessage("기록 ${moved}건을 하루 간격으로 펼쳤습니다.")
    }

    /** 같은 날짜에 두 건 이상 몰려 있는지 (기록 화면에서 정리 버튼을 띄울지 판단). */
    fun hasSameDayClusters(): Boolean =
        data.value.logs.groupBy { it.date }.any { it.value.size > 1 }

    fun deleteLog(logId: String) {
        container.repository.deleteLog(logId)
        loadedSessionKey = null
        _message.value = UiMessage("기록을 삭제했습니다.")
        syncWithGoogleTasks()          // 진행 위치가 되돌아갔으니 할 일도 다시 만든다
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
            withBusy {
                container.google.handleAuthorizationResult(data)
                    .onSuccess {
                        _message.value = UiMessage("Google 계정을 연결했습니다.")
                        refreshTaskLists()
                    }
                    .onFailure { _message.value = UiMessage(it.message ?: "로그인 실패", isError = true) }
            }
        }
    }

    fun signOut() {
        container.google.signOut()
        _taskLists.value = emptyList()
        _message.value = UiMessage("Google 연결을 해제했습니다.")
    }

    fun refreshTaskLists() {
        viewModelScope.launch {
            withBusy {
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
            }
        }
    }

    fun selectTaskList(ref: TaskListRef) {
        container.settings.update { it.copy(taskListId = ref.id, taskListTitle = ref.title) }
        syncWithGoogleTasks()          // 고른 목록에 오늘 할 일을 바로 올린다
    }

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

    // ------------------------------------------------------------ 백업 / 복원

    fun backupFileName(): String = container.repository.backupFileName()

    fun writeBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            withBusy {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val text = container.repository.exportBackupText()
                        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                            ?: throw IllegalStateException("파일을 열 수 없습니다.")
                        text.length
                    }
                }.onSuccess {
                    _message.value = UiMessage("백업을 저장했습니다. 앱을 다시 설치한 뒤 이 파일로 복원하세요.")
                }.onFailure {
                    _message.value = UiMessage(it.message ?: "백업 저장에 실패했습니다.", isError = true)
                }
            }
        }
    }

    fun readBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            withBusy {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: throw IllegalStateException("파일을 열 수 없습니다.")
                        container.repository.importBackupText(text)
                    }
                }.onSuccess { count ->
                    loadedSessionKey = null
                    _message.value = UiMessage("기록 ${count}개를 복원했습니다.")
                }.onFailure {
                    _message.value = UiMessage(it.message ?: "백업 파일을 읽지 못했습니다.", isError = true)
                }
            }
        }
    }

    fun setActivePlan(planId: String) {
        container.repository.setActivePlan(planId)
        loadedSessionKey = null
        syncWithGoogleTasks()
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
        syncWithGoogleTasks()
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

    /**
     * 키가 살아있는지 확인한다.
     *
     * 전역 busy를 쓰지 않고 자체 상태를 둔다. 다른 작업이 busy를 붙들고 있어도
     * 이 버튼만은 눌리게 하려는 것이고, 결과도 스낵바 대신 카드에 남겨서
     * 4초 뒤에 사라지지 않게 한다.
     */
    fun verifyGeminiKey() {
        if (_coach.value.keyCheck == KeyCheck.Checking) return
        val key = settings.value.geminiApiKey
        if (key.isBlank()) {
            _coach.value = _coach.value.copy(keyCheck = KeyCheck.Failed("API 키를 먼저 입력해 주세요."))
            return
        }
        viewModelScope.launch {
            _coach.value = _coach.value.copy(keyCheck = KeyCheck.Checking)
            val result = runCatching { container.gemini.listModels(key) }
            _coach.value = result.fold(
                onSuccess = { models ->
                    _coach.value.copy(
                        availableModels = models,
                        keyCheck = if (models.isEmpty()) {
                            // 200은 왔는데 목록이 비었다면 키가 이 API에 묶여 있지 않은 경우다.
                            KeyCheck.Failed("응답은 왔지만 쓸 수 있는 Gemini 모델이 없습니다. Google AI Studio에서 만든 키가 맞는지 확인해 주세요.")
                        } else {
                            KeyCheck.Ok(models.size)
                        }
                    )
                },
                onFailure = { error ->
                    _coach.value.copy(keyCheck = KeyCheck.Failed(describeKeyFailure(error)))
                }
            )
        }
    }

    /** 확인 실패 원인을 그대로 보여 준다. 원인 없이 "실패"만 뜨면 손쓸 방법이 없다. */
    private fun describeKeyFailure(error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() }
        return when {
            error is java.net.UnknownHostException ->
                "인터넷에 연결되어 있지 않거나 generativelanguage.googleapis.com에 닿지 못했습니다."
            error is java.net.SocketTimeoutException ->
                "응답이 없어 시간이 초과됐습니다. 잠시 뒤 다시 눌러 주세요."
            error is javax.net.ssl.SSLException ->
                "보안 연결에 실패했습니다 (${raw ?: "SSL 오류"})."
            error is java.io.IOException ->
                "네트워크 오류: ${raw ?: error.javaClass.simpleName}"
            raw != null -> raw
            else -> "확인 실패: ${error.javaClass.simpleName}"
        }
    }

    fun clearKeyCheck() {
        _coach.value = _coach.value.copy(keyCheck = KeyCheck.Idle)
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

    /**
     * 지금 세션이 재도전인지. 회차와 지난 실패 기록을 함께 준다.
     * 아직 한 번도 시도 안 한 세션이면 null.
     */
    fun retryState(): RetryState? {
        val currentPlan = plan ?: return null
        val session = currentSession() ?: return null
        val snapshot = data.value
        val failures = container.repository.failedAttempts(snapshot, currentPlan.id, session.index)
        if (failures.isEmpty()) return null
        val attempt = container.repository.attemptsOf(snapshot, currentPlan.id, session.index) + 1
        return RetryState(attempt = attempt, failures = failures)
    }

    // ------------------------------------------------------------ 워치(Health Connect)

    val healthPermissions: Set<String> get() = container.health.permissions

    fun healthPermissionContract() = container.health.permissionContract()

    /** 상태와 권한만 확인한다. 권한이 있으면 오늘 기록까지 읽는다. */
    fun refreshWatch(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val status = container.health.status()
            if (status != HealthStatus.AVAILABLE) {
                _watch.value = WatchUiState(status = status, checkedOnce = true)
                return@launch
            }

            val granted = container.health.hasPermission()
            if (!granted) {
                _watch.value = WatchUiState(status = status, granted = false, checkedOnce = true)
                return@launch
            }

            _watch.value = _watch.value.copy(
                status = status,
                granted = true,
                loading = true,
                error = null,
                checkedOnce = true
            )
            runCatching { container.health.sessionsOn(date) }
                .onSuccess { sessions ->
                    _watch.value = _watch.value.copy(loading = false, sessions = sessions)
                }
                .onFailure { error ->
                    _watch.value = _watch.value.copy(
                        loading = false,
                        error = error.message ?: "건강 기록을 읽지 못했습니다."
                    )
                }
        }
    }

    /**
     * 워치 기록을 메모에 붙인다. 개수는 건드리지 않는다 —
     * 가민이 안 알려 주는 값을 지어내지 않는다.
     */
    fun useWatchSession(session: WatchSession) {
        val range = HealthConnectRepository.timeRange(session)
        val line = "가민 ${session.title} $range (${session.minutes}분)"
        _note.value = if (_note.value.isBlank()) line else "${_note.value}\n$line"

        if (session.reps != null) {
            // 혹시 횟수가 들어 있으면 그건 그대로 쓴다.
            val total = session.reps
            _message.value = UiMessage("워치 기록 ${total}개를 메모에 넣었습니다. 세트별 숫자는 확인해 주세요.")
        } else {
            _message.value = UiMessage("메모에 넣었습니다. 개수는 직접 입력해 주세요.")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun stats() = container.repository.stats(data.value)

    /**
     * 플랜 화면에 보여줄 세션 날짜.
     *
     * 이미 끝낸 세션은 실제로 한 날짜를 그대로 쓴다(예보로 덮어쓰지 않는다).
     * 앞으로 할 세션은 다음 할 일 기한부터 하루에 하나씩 잡는다.
     */
    fun projectedDate(sessionIndex: Int): LocalDate {
        val currentPlan = plan ?: return LocalDate.now()
        val done = sessionPosition()

        if (sessionIndex <= done) {
            val actual = data.value.logs
                .filter { it.planId == currentPlan.id && it.sessionIndex == sessionIndex }
                .maxByOrNull { it.recordedAt }
                ?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            if (actual != null) return actual
        }

        return DateUtils.forecastDate(nextDueDate(), sessionIndex - done - 1)
    }
}
