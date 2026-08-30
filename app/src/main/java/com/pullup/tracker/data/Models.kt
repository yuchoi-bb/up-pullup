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

/** 운동 종목 하나에 대한 전체 로드맵. */
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
    val source: String = SOURCE_BUILTIN
) {
    val goalTotal: Int get() = sessions.lastOrNull()?.total ?: 0

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_GEMINI = "gemini"
        const val SOURCE_MANUAL = "manual"
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
    val syncError: String? = null
) {
    val total: Int get() = sets.sumOf { it.done }
    val targetTotal: Int get() = sets.sumOf { it.target }
    val synced: Boolean get() = taskId != null
    val repsText: String get() = sets.joinToString("/") { it.done.toString() }
}

@Serializable
data class AppData(
    val plans: List<TrainingPlan> = emptyList(),
    val logs: List<SessionLog> = emptyList(),
    val activePlanId: String? = null,
    val seeded: Boolean = false
)

/** Google Tasks 목록. */
@Serializable
data class TaskListRef(val id: String, val title: String)
