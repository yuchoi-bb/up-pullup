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

    /**
     * 앞으로의 세션 예정일. 다음 세션이 [nextDue]에 오고, 그 뒤로는 하루에 하나씩.
     *
     * 할 일 기한과 같은 기준(매일)을 쓴다. 예전에는 운동 요일만 세어 계산했는데,
     * 기한이 요일을 안 보게 바뀌면서 완주 예상일만 실제보다 늦게 나왔다.
     */
    fun forecastDate(nextDue: LocalDate, sessionsAhead: Int): LocalDate =
        nextDue.plusDays(sessionsAhead.coerceAtLeast(0).toLong())

    /**
     * Google 할 일에 올릴 "다음 세션"의 기한.
     *
     * 오늘 뭐라도 했으면 내일, 아직 안 했으면 오늘. 규칙이 이 한 줄이라
     * 세 가지가 동시에 풀린다.
     *  - 방금 끝낸 세션 때문에 다음 것이 다시 오늘로 잡히지 않는다
     *  - 하루에 여러 세션을 해도 모레로 밀려나지 않고 내일에서 멈춘다
     *  - 며칠 건너뛰어 기한이 지난 항목은, 오늘 한 게 없으므로 오늘로 당겨진다
     *
     * 운동 요일 설정은 일부러 보지 않는다(쉬는 요일에도 "내일"로 잡는 선택).
     */
    fun nextTaskDue(today: LocalDate, didSomethingToday: Boolean): LocalDate =
        if (didSomethingToday) today.plusDays(1) else today

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
