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
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * 하루에 완료한 루틴 수에 따른 동그라미 반지름(칸 대비 비율). 바깥쪽부터.
 *
 * 많이 할수록 바깥 원이 커져서 그날이 꽉 차 보이고, 빠뜨리면 자연히
 * 작아지거나 아예 안 그려진다.
 */
private val RING_SIZES: Map<Int, List<Float>> = mapOf(
    1 to listOf(0.56f),
    2 to listOf(0.78f, 0.50f),
    3 to listOf(0.96f, 0.72f, 0.48f),
    4 to listOf(1.00f, 0.80f, 0.60f, 0.40f)
)

private const val MAX_RINGS = 4

/**
 * 월 단위 운동 달력.
 *
 * 한 날은 채워진 원, 운동하기로 한 요일인데 지나갔는데 기록이 없으면 빈 테두리로
 * 표시해서 "빠진 날"이 눈에 들어오게 한다. 오늘은 링으로 감싼다.
 */
@Composable
fun WorkoutCalendar(
    month: YearMonth,
    /** 날짜 -> 그날 완료한 루틴 수. 동그라미 겹 수가 이 값이다. */
    doneCounts: Map<LocalDate, Int>,
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
                        doneCount = date?.let { doneCounts[it] } ?: 0,
                        missed = date != null &&
                            (doneCounts[date] ?: 0) == 0 &&
                            date.isBefore(today) &&
                            (trainingDays.isEmpty() || trainingDays.contains(date.dayOfWeek.value)),
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
        Text(
            "동그라미 겹 수 = 그날 끝낸 루틴 수. 빠뜨린 날은 작고 옅게 남습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    doneCount: Int,
    missed: Boolean,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // 오늘/선택 표시는 맨 바깥에 옅은 테두리로. 동그라미 겹과 섞이지 않게 한다.
            if (isToday || isSelected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (isToday) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            },
                            CircleShape
                        )
                )
            }

            when {
                doneCount > 0 -> {
                    val rings = RING_SIZES[doneCount.coerceAtMost(MAX_RINGS)].orEmpty()
                    rings.forEachIndexed { index, fraction ->
                        // 가장 안쪽만 채우고 나머지는 테두리로 둬서 겹이 다 보이게 한다.
                        val innermost = index == rings.lastIndex
                        Box(
                            Modifier
                                .fillMaxSize(fraction)
                                .clip(CircleShape)
                                .then(
                                    if (innermost) {
                                        Modifier.background(MaterialTheme.colorScheme.primary)
                                    } else {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    }
                                )
                        )
                    }
                }

                missed -> Box(
                    Modifier
                        .fillMaxSize(0.42f)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                )
            }

            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (doneCount > 0 || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    // 가운데가 채워져 있으면 글자는 그 위에 얹힌다.
                    doneCount > 0 -> MaterialTheme.colorScheme.onPrimary
                    missed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            // 4개를 넘으면 겹으로는 구분이 안 되니 숫자를 작게 덧붙인다.
            if (doneCount > MAX_RINGS) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    Text(
                        doneCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
