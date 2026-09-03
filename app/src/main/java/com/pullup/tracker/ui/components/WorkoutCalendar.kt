@file:OptIn(ExperimentalMaterial3Api::class)

package com.pullup.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

/** 달력 한 칸의 상태. */
private enum class DayState { EMPTY, REST, PLANNED_MISSED, DONE }

/**
 * 월 단위 운동 달력.
 *
 * 한 날은 채워진 원, 운동하기로 한 요일인데 지나갔는데 기록이 없으면 빈 테두리로
 * 표시해서 "빠진 날"이 눈에 들어오게 한다. 오늘은 링으로 감싼다.
 */
@Composable
fun WorkoutCalendar(
    month: YearMonth,
    workoutDays: Map<LocalDate, Int>,
    trainingDays: Set<Int>,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onSelectDay: (LocalDate) -> Unit = {}
) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    val first = month.atDay(1)
    // 일요일 시작. DayOfWeek는 월=1..일=7이므로 7을 0으로 접는다.
    val leading = first.dayOfWeek.value % 7
    val cells: List<LocalDate?> =
        List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            Text(
                "${month.year}년 ${month.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onMonthChange(month.plusMonths(1)) },
                enabled = month < thisMonth
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (i) {
                        0 -> MaterialTheme.colorScheme.error
                        6 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        state = when {
                            date == null -> DayState.EMPTY
                            workoutDays.containsKey(date) -> DayState.DONE
                            date.isBefore(today) &&
                                (trainingDays.isEmpty() || trainingDays.contains(date.dayOfWeek.value)) ->
                                DayState.PLANNED_MISSED
                            else -> DayState.REST
                        },
                        reps = date?.let { workoutDays[it] },
                        isToday = date == today,
                        isSelected = date != null && date == selected,
                        onClick = { date?.let(onSelectDay) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 마지막 주가 7칸이 안 되면 빈칸으로 채워 정렬을 맞춘다
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(MaterialTheme.colorScheme.primary, filled = true, label = "운동함")
            LegendDot(MaterialTheme.colorScheme.outline, filled = false, label = "빠진 날")
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    state: DayState,
    reps: Int?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (date == null) return@Box

        val done = state == DayState.DONE
        val base = when (state) {
            DayState.DONE -> MaterialTheme.colorScheme.primary
            DayState.PLANNED_MISSED -> Color.Transparent
            else -> Color.Transparent
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(base)
                .then(
                    when {
                        isToday -> Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                        isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        state == DayState.PLANNED_MISSED ->
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        else -> Modifier
                    }
                )
                .clickable(enabled = true, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (done || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        done -> MaterialTheme.colorScheme.onPrimary
                        state == DayState.PLANNED_MISSED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (done && reps != null) {
                    Text(
                        reps.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .then(
                    if (filled) Modifier.background(color)
                    else Modifier.border(1.dp, color, CircleShape)
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
