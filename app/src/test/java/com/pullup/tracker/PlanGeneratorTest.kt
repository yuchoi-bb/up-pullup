package com.pullup.tracker

import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanGenerator
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
    fun `운동 요일만 세어 예정일을 계산한다`() {
        // 2026-08-31은 월요일
        val monday = LocalDate.of(2026, 8, 31)
        val weekdays = listOf(1, 2, 3, 4, 5)
        assertEquals(monday, DateUtils.projectedDate(monday, weekdays, 1))
        assertEquals(LocalDate.of(2026, 9, 4), DateUtils.projectedDate(monday, weekdays, 5))
        // 6번째 운동일은 다음 주 월요일
        assertEquals(LocalDate.of(2026, 9, 7), DateUtils.projectedDate(monday, weekdays, 6))
    }
}
