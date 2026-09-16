package com.pullup.tracker

import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanGenerator
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.data.SetEntry
import com.pullup.tracker.data.TrainingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorTest {

    @Test
    fun `사다리는 현재 수준에서 시작해 목표에서 끝난다`() {
        val sessions = PlanGenerator.ladder(listOf(6, 5, 5, 4, 4), goalPerSet = 20)
        assertEquals(listOf(6, 5, 5, 4, 4), sessions.first().targets)
        assertEquals(listOf(20, 20, 20, 20, 20), sessions.last().targets)
        assertEquals(100, sessions.last().total)
    }

    @Test
    fun `총 개수는 절대 줄지 않는다`() {
        val sessions = PlanGenerator.ladder(listOf(6, 5, 5, 4, 4), goalPerSet = 20)
        sessions.zipWithNext().forEach { (a, b) ->
            assertTrue("세션 ${b.index}에서 총량이 줄었다", b.total >= a.total)
        }
    }

    @Test
    fun `다섯 번째 세션마다 다지기 날이 들어간다`() {
        val sessions = PlanGenerator.ladder(listOf(6, 5, 5, 4, 4), goalPerSet = 20)
        val fifth = sessions.first { it.index == 5 }
        val fourth = sessions.first { it.index == 4 }
        assertEquals(fourth.targets, fifth.targets)
    }

    @Test
    fun `세트 개수는 항상 유지된다`() {
        val sessions = PlanGenerator.ladder(listOf(10, 8, 8), goalPerSet = 15)
        assertTrue(sessions.all { it.targets.size == 3 })
        assertEquals(listOf(15, 15, 15), sessions.last().targets)
    }

    @Test
    fun `예정일은 다음 기한부터 하루에 하나씩이다`() {
        val nextDue = LocalDate.of(2026, 9, 8)
        assertEquals(nextDue, DateUtils.forecastDate(nextDue, 0))          // 다음 세션
        assertEquals(LocalDate.of(2026, 9, 9), DateUtils.forecastDate(nextDue, 1))
        assertEquals(LocalDate.of(2026, 9, 12), DateUtils.forecastDate(nextDue, 4))
    }

    @Test
    fun `주말을 건너뛰지 않는다`() {
        val friday = LocalDate.of(2026, 9, 11)
        // 하루 뒤는 토요일 12일, 이틀 뒤는 일요일 13일
        assertEquals(LocalDate.of(2026, 9, 12), DateUtils.forecastDate(friday, 1))
        assertEquals(LocalDate.of(2026, 9, 13), DateUtils.forecastDate(friday, 2))
    }

    @Test
    fun `남은 세션 수만큼 뒤가 완주일이다`() {
        // 48세션 중 1개 완료 -> 남은 47개, 다음 기한이 9월 8일이면 마지막은 46일 뒤
        val nextDue = LocalDate.of(2026, 9, 8)
        assertEquals(LocalDate.of(2026, 10, 24), DateUtils.forecastDate(nextDue, 46))
    }
}

class TaskDueDateTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 7)   // 월요일

    @Test
    fun `오늘 한 게 없으면 오늘이 기한이다`() {
        assertEquals(today, DateUtils.nextTaskDue(today, didSomethingToday = false))
    }

    @Test
    fun `오늘 한 게 있으면 내일이 기한이다`() {
        assertEquals(today.plusDays(1), DateUtils.nextTaskDue(today, didSomethingToday = true))
    }

    @Test
    fun `하루에 여러 세션을 해도 모레로 밀리지 않는다`() {
        // 세션을 몇 번을 끝내든 "오늘 했다"는 사실은 그대로라 기한은 계속 내일
        val afterFirst = DateUtils.nextTaskDue(today, didSomethingToday = true)
        val afterSecond = DateUtils.nextTaskDue(today, didSomethingToday = true)
        val afterThird = DateUtils.nextTaskDue(today, didSomethingToday = true)
        assertEquals(afterFirst, afterSecond)
        assertEquals(afterSecond, afterThird)
        assertEquals(today.plusDays(1), afterThird)
    }

    @Test
    fun `며칠 건너뛰면 기한이 오늘로 당겨진다`() {
        // 9월 4일에 잡아 둔 기한이 남아 있어도, 9월 7일에 아직 안 했으면 9월 7일
        val staleDue = LocalDate.of(2026, 9, 4)
        val refreshed = DateUtils.nextTaskDue(today, didSomethingToday = false)
        assertEquals(today, refreshed)
        assertTrue("밀린 기한은 오늘로 당겨져야 한다", refreshed.isAfter(staleDue))
    }

    @Test
    fun `쉬는 요일이어도 달력상 내일로 잡는다`() {
        val friday = LocalDate.of(2026, 9, 11)               // 금요일
        val saturday = LocalDate.of(2026, 9, 12)
        assertEquals(saturday, DateUtils.nextTaskDue(friday, didSomethingToday = true))
    }
}

/** 목표를 못 채웠을 때(실패) 어떻게 되는지. */
class FailureTest {

    private fun log(vararg pairs: Pair<Int, Int>) = SessionLog(
        id = "x",
        planId = "p",
        sessionIndex = 3,
        exercise = "풀업",
        date = "2026-09-07",
        sets = pairs.map { (target, done) -> SetEntry(target, done) },
        recordedAt = 0L
    )

    @Test
    fun `합계가 목표에 못 미치면 실패다`() {
        val miss = log(6 to 6, 6 to 5, 6 to 4, 4 to 2, 4 to 0)      // 17 / 26
        assertTrue(miss.failed)
        assertEquals(26, miss.targetTotal)
        assertEquals(17, miss.total)
        assertEquals(9, miss.shortfall)
    }

    @Test
    fun `세트별로 갈려도 합계만 채우면 실패가 아니다`() {
        // 1세트에서 몰아서 하고 뒤에서 모자라도 합계가 목표면 통과
        val ok = log(6 to 10, 6 to 6, 6 to 6, 4 to 2, 4 to 2)       // 26 / 26
        assertTrue(!ok.failed)
        assertEquals(0, ok.shortfall)
    }

    @Test
    fun `실패는 자동 조절 설정과 무관하게 같은 세션을 다시 한다`() {
        assertEquals(0, PlanGenerator.advanceBy(done = 17, target = 26, autoRegulate = true))
        assertEquals(0, PlanGenerator.advanceBy(done = 17, target = 26, autoRegulate = false))
        assertEquals(0, PlanGenerator.advanceBy(done = 25, target = 26, autoRegulate = false))
    }

    @Test
    fun `목표를 채우면 한 칸 전진한다`() {
        assertEquals(1, PlanGenerator.advanceBy(done = 26, target = 26, autoRegulate = true))
        assertEquals(1, PlanGenerator.advanceBy(done = 26, target = 26, autoRegulate = false))
    }

    @Test
    fun `크게 초과하면 자동 조절이 켜졌을 때만 건너뛴다`() {
        assertEquals(2, PlanGenerator.advanceBy(done = 34, target = 26, autoRegulate = true))
        assertEquals(1, PlanGenerator.advanceBy(done = 34, target = 26, autoRegulate = false))
        // 경계: 딱 +8이면 건너뛴다
        assertEquals(2, PlanGenerator.advanceBy(done = 26 + 8, target = 26, autoRegulate = true))
        assertEquals(1, PlanGenerator.advanceBy(done = 26 + 7, target = 26, autoRegulate = true))
    }

    @Test
    fun `실패를 거듭해도 세션 위치는 그대로다`() {
        // advanceBy 합이 곧 진행 위치다. 세 번 실패하면 합은 0.
        val attempts = listOf(17, 20, 24).map { PlanGenerator.advanceBy(it, 26, autoRegulate = true) }
        assertEquals(0, attempts.sum())
        // 네 번째에 채우면 그제서야 한 칸
        assertEquals(1, attempts.sum() + PlanGenerator.advanceBy(26, 26, autoRegulate = true))
    }
}

/** 루틴 생성은 전부 산술이다. AI를 부르지 않는다. */
class RoutineTest {

    @Test
    fun `횟수형 루틴은 사다리로 목표까지 간다`() {
        val routine = PlanGenerator.countedRoutine(
            name = "푸시업",
            exercise = "푸시업",
            start = listOf(15, 12, 12, 10, 10),
            goalPerSet = 30,
            trainingDays = emptyList(),
            startDate = LocalDate.of(2026, 9, 15),
            order = 1
        )
        assertTrue(routine.isCounted)
        assertEquals("5세트 × 30개 = 총 150개", routine.goal)
        // 첫 세션은 지금 할 수 있는 개수 그대로
        assertEquals(listOf(15, 12, 12, 10, 10), routine.sessions.first().targets)
        // 마지막 세션은 전 세트가 목표치
        assertEquals(List(5) { 30 }, routine.sessions.last().targets)
        assertEquals(150, routine.goalTotal)
    }

    @Test
    fun `체크형 루틴은 세션이 없다`() {
        val routine = PlanGenerator.checkRoutine(
            name = "견갑골 스트레칭",
            trainingDays = listOf(1, 3, 5),
            startDate = LocalDate.of(2026, 9, 15),
            order = 2
        )
        assertTrue(routine.isCheck)
        assertTrue(routine.sessions.isEmpty())
        assertEquals(0, routine.goalTotal)
    }

    @Test
    fun `요일을 안 고르면 매일로 본다`() {
        val everyday = PlanGenerator.checkRoutine("안구 운동", emptyList(), LocalDate.now(), 0)
        (1..7).forEach { assertTrue("$it 요일에도 해야 한다", everyday.runsOn(it)) }

        val weekdays = PlanGenerator.checkRoutine("flow", listOf(1, 2, 3, 4, 5), LocalDate.now(), 0)
        assertTrue(weekdays.runsOn(1))
        assertTrue(!weekdays.runsOn(6))
        assertTrue(!weekdays.runsOn(7))
    }

    @Test
    fun `예전 JSON처럼 kind가 없으면 횟수형이 된다`() {
        // 기존 "풀업 100 프로젝트"가 그대로 횟수형 루틴이 되는지.
        val old = TrainingPlan(
            id = "p", name = "풀업", exercise = "풀업", goal = "",
            startDate = "2026-09-01", trainingDays = emptyList(),
            sessions = PlanGenerator.ladder(listOf(6, 5, 5, 4, 4), 20),
            createdAt = 0L
        )
        assertTrue(old.isCounted)
        assertEquals(0, old.order)
        assertTrue(!old.archived)
    }

    @Test
    fun `기록을 고치면 전진 칸수도 따라 바뀐다`() {
        // 54개 목표에 실수로 10개를 넣은 상황: 실패라 제자리(0칸)
        val wrong = PlanGenerator.advanceBy(done = 10, target = 54, autoRegulate = true)
        assertEquals(0, wrong)

        // 실제로 한 54개로 고치면 한 칸 전진한다. 진행 위치는 advanceBy의 합이라
        // 이 값만 맞으면 재도전 표시도 같이 풀린다.
        val fixed = PlanGenerator.advanceBy(done = 54, target = 54, autoRegulate = true)
        assertEquals(1, fixed)
    }

    @Test
    fun `고친 개수가 여전히 모자라면 실패로 남는다`() {
        assertEquals(0, PlanGenerator.advanceBy(done = 53, target = 54, autoRegulate = true))
    }

    @Test
    fun `목표에 이미 도달했으면 세션이 하나뿐이다`() {
        val sessions = PlanGenerator.ladder(listOf(20, 20, 20), goalPerSet = 20)
        assertEquals(1, sessions.size)
    }
}
