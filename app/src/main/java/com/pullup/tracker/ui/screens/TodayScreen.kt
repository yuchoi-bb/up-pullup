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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.PlanSession
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.data.TrainingPlan
import com.pullup.tracker.health.HealthConnectRepository
import com.pullup.tracker.health.HealthStatus
import com.pullup.tracker.health.WatchSession
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.RetryState
import com.pullup.tracker.ui.WatchUiState
import com.pullup.tracker.ui.components.Pill
import com.pullup.tracker.ui.components.ProgressBarThick
import com.pullup.tracker.ui.components.SectionCard
import java.time.LocalDate

@Composable
fun TodayScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    // 루틴별 입력값. 이 flow가 바뀌어야 카드가 다시 그려진다.
    val repsByRoutine by viewModel.repsByRoutine.collectAsState()

    val plan = viewModel.plan
    val stats = viewModel.stats()
    // 가장 최근 세션. 오늘 것을 빼지 않는다 — 방금 기록했는데 카드에 어제가
    // 남아 있으면 반영이 안 된 것처럼 보인다.
    val latest = data.logs.maxByOrNull { it.recordedAt }
    val watch by viewModel.watch.collectAsState()

    // 계약 객체를 매 recomposition마다 새로 만들면 런처가 다시 등록된다.
    val healthContract = remember { viewModel.healthPermissionContract() }
    val healthPermissionLauncher = rememberLauncherForActivityResult(healthContract) {
        viewModel.refreshWatch()
    }

    // 화면에 들어올 때마다 본다. 워치가 나중에 동기화되는 일이 흔하다.
    LaunchedEffect(Unit) { viewModel.refreshWatch() }

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
                Text("MXM", style = MaterialTheme.typography.headlineMedium)
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

        item { LastSessionCard(latest) { latest?.let { viewModel.syncLog(it.id) } } }

        if (watch.checkedOnce && watch.status != HealthStatus.UNSUPPORTED) {
            item {
                WatchCard(
                    watch = watch,
                    onConnect = { healthPermissionLauncher.launch(viewModel.healthPermissions) },
                    onUse = viewModel::useWatchSession
                )
            }
        }

        val routines = viewModel.routines()

        if (routines.isEmpty()) {
            item {
                SectionCard(title = "루틴이 없습니다") {
                    Text(
                        "설정 → 루틴 관리에서 하고 싶은 운동이나 습관을 추가해 주세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(routines, key = { it.id }) { routine ->
            if (routine.isCheck) {
                CheckRoutineCard(
                    routine = routine,
                    done = viewModel.didToday(routine),
                    onToggle = { viewModel.toggleCheckRoutine(routine) }
                )
            } else {
                CountedRoutineCard(
                    routine = routine,
                    session = viewModel.sessionOf(routine),
                    position = viewModel.positionOf(routine),
                    reps = viewModel.repsFor(routine),
                    doneToday = viewModel.didToday(routine),
                    retry = viewModel.retryStateOf(routine),
                    busy = busy,
                    onReps = { index, value -> viewModel.setRepsFor(routine, index, value) },
                    onClear = { viewModel.clearRepsFor(routine) },
                    onRecord = { viewModel.recordRoutine(routine) }
                )
            }
        }

        item {
            Text(
                when {
                    !viewModel.googleConfigured ->
                        "기록은 이 기기에 저장됩니다. 설정 → 데이터에서 백업 파일로 빼 둘 수 있어요."
                    syncing -> "Google 할 일과 맞추는 중..."
                    settings.taskListId == null ->
                        "설정에서 Google 할 일 목록을 고르면 지정한 루틴이 그쪽에도 올라갑니다."
                    else ->
                        "Google 할 일 '${settings.taskListTitle ?: "목록 미선택"}'에는 " +
                            "${plan?.name ?: "지정된 루틴"} 하나만 올라갑니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WatchCard(
    watch: WatchUiState,
    onConnect: () -> Unit,
    onUse: (WatchSession) -> Unit
) {
    SectionCard(title = "워치 기록") {
        when {
            watch.status == HealthStatus.NEEDS_INSTALL -> Text(
                "Health Connect를 설치하거나 업데이트하면 가민 기록을 볼 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            !watch.granted -> {
                Text(
                    "가민 등 워치가 남긴 운동 기록을 읽어옵니다. 읽기만 하고 쓰지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onConnect) { Text("건강 기록 연결") }
            }

            watch.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("읽는 중...", style = MaterialTheme.typography.bodySmall)
            }

            watch.error != null -> Text(
                watch.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            watch.sessions.isEmpty() -> Text(
                "오늘 워치에 기록된 운동이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                watch.sessions.forEachIndexed { index, session ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                HealthConnectRepository.timeRange(session) +
                                    " · ${session.minutes}분" +
                                    (session.reps?.let { " · ${it}개" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onUse(session) }) { Text("메모에 넣기") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "워치는 운동을 했다는 사실과 시간만 남깁니다. 개수는 아래에서 직접 넣어 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LastSessionCard(latest: SessionLog?, onRetry: () -> Unit) {
    SectionCard(title = "최근 기록") {
        if (latest == null) {
            Text(
                "아직 기록이 없습니다. 오늘이 1일차!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateUtils.relativeLabel(latest.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("${latest.exercise} ${latest.repsText}", style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${latest.total}개", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "목표 ${latest.targetTotal}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (latest.failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (latest.failed) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${latest.shortfall}개 부족 — 같은 세션을 다시 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // 동기화 상태는 여기 같이 둔다. 예전엔 화면 맨 아래 "오늘 기록됨"
        // 카드에 따로 있어서, 같은 기록이 위아래로 두 번 보였다.
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (latest.synced) {
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
        if (latest.syncError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                latest.syncError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    target: Int,
    value: Int,
    onValue: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
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
        }
    }
}

/**
 * 횟수를 세는 루틴 한 칸. 세트별 숫자를 넣고 기록한다.
 *
 * 합계가 목표에 모자라면 버튼이 빨갛게 "실패로 기록하기"가 된다 —
 * 누르기 전에 실패로 남는다는 걸 알 수 있어야 한다.
 */
@Composable
private fun CountedRoutineCard(
    routine: TrainingPlan,
    session: PlanSession?,
    position: Int,
    reps: List<Int>,
    doneToday: Boolean,
    retry: RetryState?,
    busy: Boolean,
    onReps: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onRecord: () -> Unit
) {
    if (session == null) {
        SectionCard(title = routine.name) {
            Text("완주했습니다 🎉", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var expanded by remember(routine.id) { mutableStateOf(!doneToday) }
    val entered = reps.sum()
    val shortfall = session.total - entered
    val willFail = shortfall > 0

    SectionCard(
        title = routine.name,
        trailing = {
            Text(
                "세션 ${position + 1} / ${routine.sessions.size}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${session.total}", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(4.dp))
            Text("개", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            if (doneToday) {
                Pill(
                    "오늘 완료",
                    icon = Icons.Default.CheckCircle,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Text(
                    session.targets.joinToString(" / "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (session.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                session.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 왜 같은 숫자가 또 떠 있는지 여기서 바로 알 수 있어야 한다.
        if (retry != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "재도전 ${retry.attempt}회차 — 지난번 ${retry.lastFailure.total}개로 " +
                    "${retry.lastFailure.shortfall}개 모자랐습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "접기" else "펼쳐서 기록하기")
        }

        if (!expanded) return@SectionCard

        Spacer(Modifier.height(4.dp))
        ProgressBarThick(progress = if (session.total == 0) 0f else entered.toFloat() / session.total)
        Spacer(Modifier.height(6.dp))
        Text(
            "입력한 합계 ${entered}개",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        session.targets.forEachIndexed { index, target ->
            SetRow(
                index = index,
                target = target,
                value = reps.getOrElse(index) { target },
                onValue = { onReps(index, it) }
            )
            if (index != session.targets.lastIndex) Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("못 했음 (전부 0개로)")
        }
        Button(
            onClick = onRecord,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = if (willFail) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                if (willFail) "실패로 기록하기 (총 ${entered}개)" else "총 ${entered}개 기록하기",
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (willFail) {
            Spacer(Modifier.height(6.dp))
            Text(
                "목표 ${session.total}개에 ${shortfall}개 부족합니다. 같은 세션을 다시 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 개수를 안 세는 루틴. 눌러서 켜고, 다시 눌러 되돌린다. */
@Composable
private fun CheckRoutineCard(
    routine: TrainingPlan,
    done: Boolean,
    onToggle: () -> Unit
) {
    SectionCard(title = routine.name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    routine.goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (routine.trainingDays.isNotEmpty()) {
                    Text(
                        routine.trainingDays.sorted().joinToString(" ") { DateUtils.dayNameOf(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (done) {
                FilledTonalButton(onClick = onToggle) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("완료")
                }
            } else {
                Button(onClick = onToggle, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("체크")
                }
            }
        }
    }
}
