package com.pullup.tracker.data

import java.time.LocalDate
import java.util.UUID

/**
 * 사다리식 진행 플랜 생성기.
 *
 * 규칙
 *  - 세트 개수는 유지하고, 뒤쪽 세트부터 앞 세트 개수와 같아지도록 채운 뒤 전체를 한 칸씩 올린다.
 *    (예: 6/5/5/4/4 -> 6/6/5/4/4 -> 6/6/6/4/4 -> ... -> 6/6/6/6/6 -> 7/6/6/6/6)
 *  - 한 세션당 [incrementsPerSession]개씩 증가시킨다.
 *  - [consolidateEvery] 번째 세션은 증가 없이 같은 목표를 반복하는 "다지기" 날이다.
 */
object PlanGenerator {

    /**
     * 기록 한 건이 플랜을 몇 칸 전진시키는지. 0이면 같은 세션을 다시 한다.
     *
     * 실패(목표 미달)는 자동 조절 설정과 무관하게 언제나 0이다. 못 채운 걸
     * 채우려고 다시 하는 것이라 목표를 낮추지도 않는다.
     */
    fun advanceBy(done: Int, target: Int, autoRegulate: Boolean): Int = when {
        done < target -> 0
        !autoRegulate -> 1
        done >= target + 8 -> 2       // 크게 초과 -> 한 세션 건너뛰기
        else -> 1
    }


    const val DEFAULT_INCREMENTS_PER_SESSION = 2
    const val DEFAULT_CONSOLIDATE_EVERY = 5

    fun ladder(
        start: List<Int>,
        goalPerSet: Int,
        incrementsPerSession: Int = DEFAULT_INCREMENTS_PER_SESSION,
        consolidateEvery: Int = DEFAULT_CONSOLIDATE_EVERY
    ): List<PlanSession> {
        if (start.isEmpty()) return emptyList()
        val cur = start.map { it.coerceAtLeast(1) }.toMutableList()
        val out = mutableListOf<PlanSession>()
        out += PlanSession(1, cur.toList(), PlanSession.KIND_START, "시작 지점")

        var index = 2
        var guard = 0
        while (cur.any { it < goalPerSet } && guard++ < 1000) {
            val consolidate = consolidateEvery > 1 && index % consolidateEvery == 0
            if (!consolidate) {
                repeat(incrementsPerSession.coerceAtLeast(1)) { bump(cur, goalPerSet) }
            }
            out += PlanSession(
                index = index,
                targets = cur.toList(),
                kind = if (consolidate) PlanSession.KIND_CONSOLIDATE else PlanSession.KIND_PROGRESS,
                note = if (consolidate) "다지기 — 지난 세션과 같은 목표" else ""
            )
            index++
        }
        if (out.isNotEmpty()) {
            val last = out.last()
            out[out.lastIndex] = last.copy(kind = PlanSession.KIND_GOAL, note = "목표 달성 🎉")
        }
        return out
    }

    private fun bump(cur: MutableList<Int>, goal: Int) {
        for (i in 1 until cur.size) {
            if (cur[i] < goal && cur[i] < cur[i - 1]) {
                cur[i]++
                return
            }
        }
        if (cur[0] < goal) cur[0]++
    }

    /** 앱 최초 실행 시 들어가는 "풀업 100개" 플랜. */
    fun defaultPullupPlan(startDate: LocalDate, trainingDays: List<Int>): TrainingPlan {
        val sessions = ladder(start = listOf(6, 5, 5, 4, 4), goalPerSet = 20)
        return TrainingPlan(
            id = UUID.randomUUID().toString(),
            name = "풀업 100 프로젝트",
            exercise = "풀업",
            goal = "5세트 × 20개 = 총 100개",
            startDate = startDate.toString(),
            trainingDays = trainingDays,
            sessions = sessions,
            createdAt = System.currentTimeMillis(),
            source = TrainingPlan.SOURCE_BUILTIN
        )
    }

    /**
     * 목표 세션 수에 맞춰 증가 폭을 자동으로 고른다.
     * AI 없이도 "다른 종목 / 다른 기간" 플랜을 만들 때 사용한다.
     */
    fun planFor(
        name: String,
        exercise: String,
        start: List<Int>,
        goalPerSet: Int,
        trainingDays: List<Int>,
        startDate: LocalDate,
        targetWeeks: Int?
    ): TrainingPlan {
        val perWeek = trainingDays.size.coerceAtLeast(1)
        val wantedSessions = targetWeeks?.let { (it * perWeek).coerceAtLeast(2) }
        val needed = start.sumOf { (goalPerSet - it).coerceAtLeast(0) }
        val increments = when {
            wantedSessions == null || needed == 0 -> DEFAULT_INCREMENTS_PER_SESSION
            else -> {
                val effective = (wantedSessions * (DEFAULT_CONSOLIDATE_EVERY - 1) / DEFAULT_CONSOLIDATE_EVERY)
                    .coerceAtLeast(1)
                Math.ceil(needed.toDouble() / effective).toInt().coerceIn(1, 10)
            }
        }
        val sessions = ladder(start, goalPerSet, increments)
        return TrainingPlan(
            id = UUID.randomUUID().toString(),
            name = name,
            exercise = exercise,
            goal = "${start.size}세트 × ${goalPerSet}개 = 총 ${start.size * goalPerSet}개",
            startDate = startDate.toString(),
            trainingDays = trainingDays,
            sessions = sessions,
            createdAt = System.currentTimeMillis(),
            source = TrainingPlan.SOURCE_MANUAL
        )
    }
}
