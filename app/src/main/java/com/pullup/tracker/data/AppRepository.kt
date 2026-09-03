package com.pullup.tracker.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** 통계 요약. */
data class Stats(
    val totalReps: Int,
    val sessionCount: Int,
    val streak: Int,
    val bestSession: Int,
    val last7Days: Int,
    val last30Days: Int
)

class AppRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "pullup-data.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData = runCatching {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
    }.getOrElse { AppData() }

    private fun mutate(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        scope.launch {
            runCatching { file.writeText(json.encodeToString(next)) }
        }
    }

    // ---------------------------------------------------------------- 초기 데이터

    /**
     * 첫 실행 시 "풀업 100 프로젝트"를 만들고, 오늘 이미 마친 6/5/5/4/4를 1번 세션 기록으로 넣는다.
     * (기록 화면에서 지우거나 고칠 수 있다.)
     */
    fun seedIfNeeded(trainingDays: List<Int>) {
        if (_data.value.seeded) return
        val today = LocalDate.now()
        val plan = PlanGenerator.defaultPullupPlan(today, trainingDays)
        val first = plan.sessions.first()
        val seedLog = SessionLog(
            id = UUID.randomUUID().toString(),
            planId = plan.id,
            sessionIndex = first.index,
            exercise = plan.exercise,
            date = today.toString(),
            sets = first.targets.map { SetEntry(it, it) },
            note = "초기 기록 — 6/5/5/4/4",
            recordedAt = System.currentTimeMillis(),
            advanceBy = 1
        )
        mutate {
            it.copy(
                plans = listOf(plan),
                logs = listOf(seedLog),
                activePlanId = plan.id,
                seeded = true
            )
        }
    }

    // ---------------------------------------------------------------- 플랜

    val activePlan: TrainingPlan?
        get() = _data.value.let { d -> d.plans.firstOrNull { it.id == d.activePlanId } ?: d.plans.firstOrNull() }

    fun planOf(data: AppData): TrainingPlan? =
        data.plans.firstOrNull { it.id == data.activePlanId } ?: data.plans.firstOrNull()

    fun logsOf(data: AppData, plan: TrainingPlan?): List<SessionLog> =
        if (plan == null) emptyList() else data.logs.filter { it.planId == plan.id }

    /** 완료 기록의 advanceBy 합 = 다음에 해야 할 세션의 0-based 위치. */
    fun currentSessionPosition(data: AppData, plan: TrainingPlan?): Int =
        logsOf(data, plan).sumOf { it.advanceBy }

    fun currentSession(data: AppData, plan: TrainingPlan?): PlanSession? {
        plan ?: return null
        val pos = currentSessionPosition(data, plan)
        return plan.sessions.getOrNull(pos) ?: plan.sessions.lastOrNull()
    }

    fun addPlan(plan: TrainingPlan, makeActive: Boolean = true) = mutate { d ->
        d.copy(
            plans = d.plans + plan,
            activePlanId = if (makeActive) plan.id else d.activePlanId,
            seeded = true
        )
    }

    fun setActivePlan(planId: String) = mutate { it.copy(activePlanId = planId) }

    fun deletePlan(planId: String) = mutate { d ->
        val plans = d.plans.filterNot { it.id == planId }
        d.copy(
            plans = plans,
            logs = d.logs.filterNot { it.planId == planId },
            activePlanId = if (d.activePlanId == planId) plans.firstOrNull()?.id else d.activePlanId
        )
    }

    fun updatePlanTrainingDays(days: List<Int>) = mutate { d ->
        d.copy(plans = d.plans.map { if (it.id == d.activePlanId) it.copy(trainingDays = days) else it })
    }

    // ---------------------------------------------------------------- 기록

    fun recordSession(
        plan: TrainingPlan,
        session: PlanSession,
        sets: List<SetEntry>,
        note: String,
        autoRegulate: Boolean,
        date: LocalDate = LocalDate.now(),
        source: String = SessionLog.SOURCE_APP
    ): SessionLog {
        val done = sets.sumOf { it.done }
        val target = sets.sumOf { it.target }
        val advance = when {
            !autoRegulate -> 1
            done < target -> 0            // 목표 미달 -> 같은 세션 한 번 더
            done >= target + 8 -> 2       // 크게 초과 -> 한 세션 건너뛰기
            else -> 1
        }
        val log = SessionLog(
            id = UUID.randomUUID().toString(),
            planId = plan.id,
            sessionIndex = session.index,
            exercise = plan.exercise,
            date = date.toString(),
            sets = sets,
            note = note,
            recordedAt = System.currentTimeMillis(),
            advanceBy = advance,
            source = source
        )
        mutate { it.copy(logs = it.logs + log) }
        return log
    }

    fun deleteLog(logId: String) = mutate { d -> d.copy(logs = d.logs.filterNot { it.id == logId }) }

    fun updateLog(log: SessionLog) = mutate { d ->
        d.copy(logs = d.logs.map { if (it.id == log.id) log else it })
    }

    fun attachTask(logId: String, taskId: String?, listId: String?, listTitle: String?, error: String?) =
        mutate { d ->
            d.copy(logs = d.logs.map {
                if (it.id == logId) {
                    it.copy(taskId = taskId, taskListId = listId, taskListTitle = listTitle, syncError = error)
                } else {
                    it
                }
            })
        }

    // ---------------------------------------------------------------- Google Tasks 미할 일

    fun setPendingTask(pending: PendingTask?) = mutate { it.copy(pendingTask = pending) }

    /**
     * 올려 둔 항목이 지금 플랜 상태와 맞는지. 계획을 바꿨거나 기록을 지워서
     * 진행 위치가 달라졌으면 낡은 것이므로 새로 만들어야 한다.
     */
    fun isPendingTaskStale(data: AppData, taskListId: String?): Boolean {
        val pending = data.pendingTask ?: return true
        val plan = planOf(data) ?: return true
        return pending.taskListId != taskListId ||
            pending.planId != plan.id ||
            pending.position != currentSessionPosition(data, plan)
    }

    // ---------------------------------------------------------------- 통계

    fun stats(data: AppData): Stats {
        val logs = data.logs.sortedByDescending { it.date }
        if (logs.isEmpty()) return Stats(0, 0, 0, 0, 0, 0)
        val today = LocalDate.now()
        val dates = logs.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.distinct().sortedDescending()

        var streak = 0
        if (dates.isNotEmpty()) {
            // 오늘 또는 어제부터 이어지는 연속 운동일 수 (하루 쉬는 건 허용하지 않음)
            var cursor = if (dates.first() == today || dates.first() == today.minusDays(1)) dates.first() else null
            while (cursor != null && dates.contains(cursor)) {
                streak++
                cursor = cursor.minusDays(1)
            }
        }

        fun sumSince(days: Long): Int {
            val from = today.minusDays(days - 1)
            return logs.filter {
                val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
                d != null && !d.isBefore(from)
            }.sumOf { it.total }
        }

        return Stats(
            totalReps = logs.sumOf { it.total },
            sessionCount = logs.size,
            streak = streak,
            bestSession = logs.maxOf { it.total },
            last7Days = sumSince(7),
            last30Days = sumSince(30)
        )
    }

    // ---------------------------------------------------------------- 백업 / 복원

    /** 플랜·기록·진행도까지 전부 담긴 백업 문자열. 앱을 지웠다 깔아도 이걸로 그대로 돌아온다. */
    fun exportBackupText(): String = json.encodeToString(_data.value)

    /** 백업 문자열로 전체 데이터를 덮어쓴다. 복원한 기록 수를 돌려준다. */
    fun importBackupText(text: String): Int {
        val incoming = json.decodeFromString<AppData>(text)
        require(incoming.plans.isNotEmpty() || incoming.logs.isNotEmpty()) {
            "백업 파일에 플랜도 기록도 없습니다."
        }
        val restored = incoming.copy(
            seeded = true,
            activePlanId = incoming.activePlanId ?: incoming.plans.firstOrNull()?.id
        )
        mutate { restored }
        return restored.logs.size
    }

    fun backupFileName(): String = "up-pullup-backup-${LocalDate.now()}.json"

    // ---------------------------------------------------------------- 내보내기

    fun exportCsv(): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "pullup-${LocalDate.now()}.csv")
        val sb = StringBuilder("date,exercise,session,sets,total,target_total,note,google_task\n")
        _data.value.logs.sortedBy { it.date }.forEach { log ->
            sb.append(log.date).append(',')
                .append(log.exercise).append(',')
                .append(log.sessionIndex).append(',')
                .append('"').append(log.repsText).append('"').append(',')
                .append(log.total).append(',')
                .append(log.targetTotal).append(',')
                .append('"').append(log.note.replace("\"", "'")).append('"').append(',')
                .append(if (log.synced) "synced" else "-")
                .append('\n')
        }
        out.writeText(sb.toString())
        return out
    }

    companion object {
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)")
    }
}
