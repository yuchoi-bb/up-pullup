package com.pullup.tracker.data

import kotlinx.serialization.Serializable

/** 플랜 안의 한 세션(=하루치 운동) 목표. */
@Serializable
data class PlanSession(
    val index: Int,
    val targets: List<Int>,
    val kind: String = KIND_PROGRESS,
    val note: String = ""
) {
    val total: Int get() = targets.sum()

    companion object {
        const val KIND_START = "start"
        const val KIND_PROGRESS = "progress"
        const val KIND_CONSOLIDATE = "consolidate"
        const val KIND_GOAL = "goal"
    }
}

/**
 * 루틴 하나. 개수를 세는 운동일 수도, 그냥 했는지만 체크하는 습관일 수도 있다.
 *
 * 필드를 덧붙이기만 했기 때문에 예전에 저장된 JSON도 그대로 읽힌다.
 * 기존 "풀업 100 프로젝트"는 기본값을 받아 횟수형 루틴이 된다.
 */
@Serializable
data class TrainingPlan(
    val id: String,
    val name: String,
    val exercise: String,
    val goal: String,
    val startDate: String,
    val trainingDays: List<Int>,
    val sessions: List<PlanSession>,
    val createdAt: Long,
    val source: String = SOURCE_BUILTIN,
    /** [KIND_COUNTED] 면 세트·개수를 세고, [KIND_CHECK] 면 했는지만 본다. */
    val kind: String = KIND_COUNTED,
    /** 오늘 화면에 뜨는 순서. 작을수록 위. */
    val order: Int = 0,
    /** 목록에서 감춘다. 기록은 남는다. */
    val archived: Boolean = false,
    /** Google 할 일에 "다음에 할 것"으로 올릴 루틴인지. 하나만 켤 수 있다. */
    val syncToTasks: Boolean = false
) {
    val goalTotal: Int get() = sessions.lastOrNull()?.total ?: 0
    val isCheck: Boolean get() = kind == KIND_CHECK
    val isCounted: Boolean get() = !isCheck

    /** 이 요일에 하기로 한 루틴인지. 비어 있으면 매일. */
    fun runsOn(day: Int): Boolean = trainingDays.isEmpty() || trainingDays.contains(day)

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_GEMINI = "gemini"
        const val SOURCE_MANUAL = "manual"

        const val KIND_COUNTED = "counted"
        const val KIND_CHECK = "check"
    }
}

/** 한 세트의 목표/실제 개수. */
@Serializable
data class SetEntry(val target: Int, val done: Int)

/** 완료 기록. Google Tasks 동기화 결과도 함께 들고 있다. */
@Serializable
data class SessionLog(
    val id: String,
    val planId: String,
    val sessionIndex: Int,
    val exercise: String,
    val date: String,
    val sets: List<SetEntry>,
    val note: String = "",
    val recordedAt: Long,
    /** 이 기록이 플랜을 몇 칸 전진시키는지. 0이면 같은 세션을 다시 한다. */
    val advanceBy: Int = 1,
    val taskId: String? = null,
    val taskListId: String? = null,
    val taskListTitle: String? = null,
    val syncError: String? = null,
    /** 어디서 완료 처리됐는지. Tasks에서 체크한 기록은 실제 개수를 알 수 없어 목표치로 채운다. */
    val source: String = SOURCE_APP
) {
    val total: Int get() = sets.sumOf { it.done }
    val targetTotal: Int get() = sets.sumOf { it.target }
    val synced: Boolean get() = taskId != null

    /**
     * 목표 개수를 못 채운 시도. 실패해도 기록은 남기고, 같은 세션을 다시 한다.
     *
     * Google 할 일에서 체크한 기록은 실제 개수를 알 수 없어 목표치로 채우므로
     * 여기서 실패로 잡히지 않는다(개수가 달랐다면 앱에서 다시 넣으면 된다).
     */
    val failed: Boolean get() = total < targetTotal
    val shortfall: Int get() = (targetTotal - total).coerceAtLeast(0)
    val repsText: String get() = sets.joinToString("/") { it.done.toString() }
    val fromTasks: Boolean get() = source == SOURCE_TASKS

    companion object {
        const val SOURCE_APP = "app"
        const val SOURCE_TASKS = "tasks"
    }
}

/**
 * Google Tasks에 올려 둔 "아직 안 한 운동" 한 건.
 *
 * 앱은 항상 다음에 할 세션 하나만 미할 일로 올려 둔다. 플랜이 완료 횟수 기준으로
 * 진행되기 때문에, 여러 개를 미리 만들어 두면 뒤쪽 목표 숫자가 실제와 어긋난다.
 */
@Serializable
data class PendingTask(
    val taskId: String,
    val taskListId: String,
    val planId: String,
    val sessionIndex: Int,
    /** 만들 당시 플랜 진행 위치. 이 값이 달라지면 낡은 항목이므로 새로 만든다. */
    val position: Int,
    val title: String,
    val dueDate: String,
    val createdAt: Long
)

@Serializable
data class AppData(
    val plans: List<TrainingPlan> = emptyList(),
    val logs: List<SessionLog> = emptyList(),
    val activePlanId: String? = null,
    val seeded: Boolean = false,
    /** Google Tasks에 올려 둔 "오늘 할 운동" 항목. 없으면 아직 안 올렸다는 뜻. */
    val pendingTask: PendingTask? = null
)

/** Google Tasks 목록. */
@Serializable
data class TaskListRef(val id: String, val title: String)
