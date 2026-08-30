package com.pullup.tracker.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    private val labelFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val shortFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)

    val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

    fun label(date: LocalDate): String = date.format(labelFormat)

    fun label(iso: String): String =
        runCatching { LocalDate.parse(iso).format(labelFormat) }.getOrDefault(iso)

    fun short(iso: String): String =
        runCatching { LocalDate.parse(iso).format(shortFormat) }.getOrDefault(iso)

    fun dayNameOf(value: Int): String = dayNames.getOrElse(value - 1) { "?" }

    /** [start]부터 시작해 [days] 요일만 세면서 [sessionIndex](1-based)번째 운동일을 구한다. */
    fun projectedDate(start: LocalDate, days: List<Int>, sessionIndex: Int): LocalDate {
        val allowed = days.filter { it in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
        var cursor = start
        var counted = 0
        var guard = 0
        while (guard++ < 4000) {
            if (allowed.contains(cursor.dayOfWeek.value)) {
                counted++
                if (counted >= sessionIndex) return cursor
            }
            cursor = cursor.plusDays(1)
        }
        return cursor
    }

    fun relativeLabel(iso: String): String {
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val today = LocalDate.now()
        return when (date) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            today.minusDays(2) -> "그제"
            today.plusDays(1) -> "내일"
            else -> label(date)
        }
    }
}
