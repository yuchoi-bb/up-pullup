@file:OptIn(ExperimentalMaterial3Api::class)

package com.pullup.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.Pill
import com.pullup.tracker.ui.components.ProgressBarThick
import com.pullup.tracker.ui.components.SectionCard
import com.pullup.tracker.ui.components.StatTile

@Composable
fun PlanScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsState()
    val plan = viewModel.plan
    val position = viewModel.sessionPosition()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (plan == null) {
            item {
                SectionCard(title = "플랜 없음") {
                    Text("AI 코치 탭에서 플랜을 만들어 주세요.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@LazyColumn
        }

        item {
            val remaining = (plan.sessions.size - position).coerceAtLeast(0)
            val finishDate = viewModel.projectedDate(plan.sessions.size)
            SectionCard(title = plan.name) {
                Text(plan.goal, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                ProgressBarThick(
                    progress = if (plan.sessions.isEmpty()) 0f else position.toFloat() / plan.sessions.size
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$position / ${plan.sessions.size} 세션 완료",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("남은 세션", "$remaining", "회", Modifier.weight(1f))
                    StatTile(
                        "완주 예상",
                        DateUtils.short(finishDate.toString()),
                        "",
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.trainingDays.forEach { Pill(DateUtils.dayNameOf(it)) }
                    if (plan.trainingDays.isEmpty()) Pill("매일")
                }
            }
        }

        item {
            Text(
                "전체 로드맵",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        items(plan.sessions, key = { it.index }) { session ->
            val index = plan.sessions.indexOf(session)
            SessionRow(
                session = session,
                state = when {
                    index < position -> SessionState.DONE
                    index == position -> SessionState.CURRENT
                    else -> SessionState.UPCOMING
                },
                dateLabel = DateUtils.short(viewModel.projectedDate(session.index).toString())
            )
        }

        if (data.plans.size > 1) {
            item {
                SectionCard(title = "다른 플랜") {
                    data.plans.filter { it.id != plan.id }.forEach { other ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(other.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    other.goal,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AssistChip(
                                onClick = { viewModel.setActivePlan(other.id) },
                                label = { Text("전환") }
                            )
                            Spacer(Modifier.width(6.dp))
                            AssistChip(
                                onClick = { viewModel.deletePlan(other.id) },
                                label = { Text("삭제") }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class SessionState { DONE, CURRENT, UPCOMING }

@Composable
private fun SessionRow(session: PlanSession, state: SessionState, dateLabel: String) {
    val container = when (state) {
        SessionState.CURRENT -> MaterialTheme.colorScheme.primaryContainer
        SessionState.DONE -> MaterialTheme.colorScheme.surfaceVariant
        SessionState.UPCOMING -> MaterialTheme.colorScheme.surface
    }
    Surface(shape = RoundedCornerShape(18.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(52.dp)) {
                Text("#${session.index}", style = MaterialTheme.typography.labelLarge)
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(session.targets.joinToString(" / "), style = MaterialTheme.typography.titleMedium)
                if (session.note.isNotBlank()) {
                    Text(
                        session.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${session.total}",
                    style = MaterialTheme.typography.titleLarge,
                    color = when (state) {
                        SessionState.CURRENT -> MaterialTheme.colorScheme.primary
                        SessionState.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        SessionState.UPCOMING -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    when (state) {
                        SessionState.DONE -> "완료"
                        SessionState.CURRENT -> "오늘"
                        SessionState.UPCOMING -> "예정"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
