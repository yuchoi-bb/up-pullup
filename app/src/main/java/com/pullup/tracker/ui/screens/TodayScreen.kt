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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.Pill
import com.pullup.tracker.ui.components.ProgressBarThick
import com.pullup.tracker.ui.components.SectionCard
import java.time.LocalDate

@Composable
fun TodayScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsState()
    val reps by viewModel.repsInput.collectAsState()
    val done by viewModel.setDone.collectAsState()
    val note by viewModel.note.collectAsState()
    val rest by viewModel.restRemaining.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncing by viewModel.syncing.collectAsState()

    val plan = viewModel.plan
    val session = viewModel.currentSession()
    val stats = viewModel.stats()
    val position = viewModel.sessionPosition()
    val todayLog = viewModel.todayLog()
    val previous = data.logs.sortedByDescending { it.recordedAt }.firstOrNull { it.date != LocalDate.now().toString() }

    LaunchedEffect(plan?.id, session?.index, position) {
        viewModel.prepareInputs(session)
    }

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
        item {
            Column {
                Text(
                    DateUtils.label(LocalDate.now()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(plan?.name ?: "플랜이 없습니다", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("연속 ${stats.streak}일", icon = Icons.Default.CheckCircle)
                    Pill(
                        "누적 ${stats.totalReps}개",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        item { LastSessionCard(previous) }

        if (session != null && plan != null) {
            item {
                TodayTargetCard(
                    session = session,
                    position = position,
                    totalSessions = plan.sessions.size,
                    reps = reps,
                    done = done,
                    onReps = viewModel::setReps,
                    onToggle = viewModel::toggleSet
                )
            }

            if (rest > 0) {
                item {
                    RestCard(
                        remaining = rest,
                        total = settings.restSeconds,
                        onSkip = viewModel::stopRest,
                        onRestart = { viewModel.startRest() }
                    )
                }
            }

            item {
                SectionCard(title = "메모") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("컨디션, 자세, 느낌 등") },
                        minLines = 2
                    )
                }
            }

            item {
                val entered = reps.sum()
                Button(
                    onClick = viewModel::completeSession,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("총 ${entered}개 기록하기", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !viewModel.googleConfigured ->
                            "기록은 이 기기에 저장됩니다. 설정 → 데이터에서 백업 파일로 빼 둘 수 있어요."
                        syncing -> "Google 할 일과 맞추는 중..."
                        settings.taskListId == null ->
                            "설정에서 Google 할 일 목록을 고르면 오늘 할 운동이 그쪽에도 올라갑니다."
                        data.pendingTask != null ->
                            "Google 할 일에 올라가 있습니다. 거기서 체크해도 앱에 반영됩니다."
                        !settings.autoSync ->
                            "Google 자동 업로드가 꺼져 있습니다. 설정에서 켤 수 있어요."
                        else ->
                            "기록하면 Google 할 일 '${settings.taskListTitle ?: "목록 미선택"}'에 완료로 올라갑니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                SectionCard(title = "플랜 없음") {
                    Text("AI 코치 탭에서 새 플랜을 만들어 주세요.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (todayLog != null) {
            item { TodayDoneCard(todayLog) { viewModel.syncLog(todayLog.id) } }
        }
    }
}

@Composable
private fun LastSessionCard(previous: SessionLog?) {
    SectionCard(title = "지난 기록") {
        if (previous == null) {
            Text(
                "아직 기록이 없습니다. 오늘이 1일차!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DateUtils.relativeLabel(previous.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${previous.exercise} ${previous.repsText}", style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${previous.total}개", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "목표 ${previous.targetTotal}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTargetCard(
    session: PlanSession,
    position: Int,
    totalSessions: Int,
    reps: List<Int>,
    done: List<Boolean>,
    onReps: (Int, Int) -> Unit,
    onToggle: (Int) -> Unit
) {
    val entered = reps.sum()
    SectionCard(
        title = "오늘 목표",
        trailing = { Text("세션 ${position + 1} / $totalSessions", style = MaterialTheme.typography.labelMedium) }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${session.total}", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(4.dp))
            Text("개", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(
                session.targets.joinToString(" / "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (session.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                session.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        ProgressBarThick(progress = if (session.total == 0) 0f else entered.toFloat() / session.total)
        Spacer(Modifier.height(6.dp))
        Text(
            "입력한 합계 ${entered}개",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        session.targets.forEachIndexed { index, target ->
            SetRow(
                index = index,
                target = target,
                value = reps.getOrElse(index) { target },
                done = done.getOrElse(index) { false },
                onValue = { onReps(index, it) },
                onToggle = { onToggle(index) }
            )
            if (index != session.targets.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    target: Int,
    value: Int,
    done: Boolean,
    onValue: (Int) -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(64.dp)) {
                Text("${index + 1}세트", style = MaterialTheme.typography.labelLarge)
                Text(
                    "목표 ${target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onValue(value - 1) }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "하나 줄이기", modifier = Modifier.size(22.dp))
            }
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onValue(value + 1) }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "하나 늘리기", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onToggle) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = "세트 완료",
                    tint = if (done) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun RestCard(remaining: Int, total: Int, onSkip: () -> Unit, onRestart: () -> Unit) {
    SectionCard(title = "휴식") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format("%d:%02d", remaining / 60, remaining % 60),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("다시")
            }
            TextButton(onClick = onSkip) { Text("건너뛰기") }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total <= 0) 0f else remaining.toFloat() / total },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TodayDoneCard(log: SessionLog, onRetry: () -> Unit) {
    SectionCard(title = "오늘 기록됨") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${log.exercise} ${log.repsText}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "총 ${log.total}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (log.synced) {
                Pill(
                    "Google 동기화됨",
                    icon = Icons.Default.CheckCircle,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                FilledTonalButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("다시 올리기")
                }
            }
        }
        if (log.syncError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                log.syncError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
