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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.BarEntry
import com.pullup.tracker.ui.components.RepsBarChart
import com.pullup.tracker.ui.components.SectionCard
import com.pullup.tracker.ui.components.WorkoutCalendar
import com.pullup.tracker.ui.components.StatTile
import java.time.LocalDate
import java.time.YearMonth

private enum class HistorySort { DATE, REPS }

/** YearMonth는 기본 Saver가 없어서 "2026-09" 문자열로 저장한다. */
private val YearMonthSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>(
    save = { it.toString() },
    restore = { runCatching { YearMonth.parse(it) }.getOrDefault(YearMonth.now()) }
)

@Composable
fun HistoryScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsState()
    val stats = viewModel.stats()
    var sortBy by rememberSaveable { mutableStateOf(HistorySort.DATE) }
    // 같은 날짜가 여러 건이면 세션 순서를 2차 기준으로 써서 순서가 뒤죽박죽이 되지 않게 한다.
    val logs = remember(data.logs, sortBy) {
        when (sortBy) {
            HistorySort.DATE -> data.logs.sortedWith(
                compareByDescending<SessionLog> { it.date }
                    .thenByDescending { it.sessionIndex }
                    .thenByDescending { it.recordedAt }
            )
            HistorySort.REPS -> data.logs.sortedWith(
                compareByDescending<SessionLog> { it.total }.thenByDescending { it.date }
            )
        }
    }
    var pendingDelete by remember { mutableStateOf<SessionLog?>(null) }
    var editing by remember { mutableStateOf<SessionLog?>(null) }
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    // 날짜별 총 개수 (하루에 두 번 했으면 합산)
    val byDay = remember(data.logs) {
        data.logs.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) }.getOrNull()?.let { it to log.total }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    }

    // 차트는 정렬과 무관하게 항상 시간순(오래된 것 -> 최근)
    val chartLogs = remember(data.logs) {
        data.logs.sortedWith(compareBy<SessionLog> { it.date }.thenBy { it.sessionIndex })
            .takeLast(14)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("누적", "${stats.totalReps}", "개", Modifier.weight(1f))
                StatTile(
                    "연속",
                    "${stats.streak}",
                    "일",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.secondary
                )
                StatTile(
                    "최고",
                    "${stats.bestSession}",
                    "개",
                    Modifier.weight(1f),
                    MaterialTheme.colorScheme.tertiary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("최근 7일", "${stats.last7Days}", "개", Modifier.weight(1f))
                StatTile("최근 30일", "${stats.last30Days}", "개", Modifier.weight(1f))
                StatTile("총 세션", "${stats.sessionCount}", "회", Modifier.weight(1f))
            }
        }

        item {
            val monthDays = byDay.filterKeys { YearMonth.from(it) == month }
            SectionCard(
                title = "달력",
                trailing = {
                    Text(
                        "${monthDays.size}일 · 총 ${monthDays.values.sum()}개",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                WorkoutCalendar(
                    month = month,
                    workoutDays = byDay,
                    trainingDays = (viewModel.plan?.trainingDays ?: emptyList()).toSet(),
                    onMonthChange = { month = it; selectedDay = null },
                    selected = selectedDay,
                    onSelectDay = { selectedDay = if (selectedDay == it) null else it }
                )
                val picked = selectedDay
                if (picked != null) {
                    Spacer(Modifier.height(12.dp))
                    val dayLogs = data.logs.filter { it.date == picked.toString() }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(DateUtils.label(picked), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            if (dayLogs.isEmpty()) {
                                Text(
                                    "이 날은 기록이 없습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                dayLogs.forEach { log ->
                                    Text(
                                        "${log.exercise} ${log.repsText} · 총 ${log.total}개" +
                                            (if (log.failed) " · 실패(${log.shortfall}개 부족)" else "") +
                                            (if (log.fromTasks) " · Tasks에서 체크" else ""),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "최근 14세션") {
                val goalTotal = viewModel.plan?.goalTotal
                RepsBarChart(
                    entries = chartLogs.map { BarEntry(it.total, DateUtils.short(it.date)) },
                    goal = goalTotal
                )
                Spacer(Modifier.height(6.dp))
                val dataMax = chartLogs.maxOfOrNull { it.total } ?: 0
                Text(
                    if (goalTotal != null && dataMax > 0 && goalTotal <= dataMax * 1.6) {
                        "가로선은 최종 목표 ${goalTotal}개입니다."
                    } else {
                        "세로 축은 지금 기록 범위에 맞춰져 있습니다. 최종 목표는 ${goalTotal ?: 0}개."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = sortBy == HistorySort.DATE,
                    onClick = { sortBy = HistorySort.DATE },
                    label = { Text("날짜순") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = sortBy == HistorySort.REPS,
                    onClick = { sortBy = HistorySort.REPS },
                    label = { Text("개수순") }
                )
            }
        }

        if (viewModel.hasSameDayClusters()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("같은 날짜에 몰린 기록이 있습니다", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "한 번에 몰아 입력한 경우, 하루 간격으로 펼쳐 실제 날짜에 맞출 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        FilledTonalButton(onClick = viewModel::spreadSameDayLogs) { Text("펼치기") }
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                SectionCard {
                    Text("아직 기록이 없습니다.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(logs, key = { it.id }) { log ->
            LogRow(
                log = log,
                onRetry = { viewModel.syncLog(log.id) },
                onDelete = { pendingDelete = log },
                onEdit = { editing = log }
            )
        }
    }

    val editTarget = editing
    if (editTarget != null) {
        var draft by remember(editTarget.id) {
            mutableStateOf(runCatching { LocalDate.parse(editTarget.date) }.getOrDefault(LocalDate.now()))
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("날짜 바꾸기") },
            text = {
                Column {
                    Text(
                        "${editTarget.exercise} ${editTarget.repsText} (총 ${editTarget.total}개)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { draft = draft.minusDays(1) }) { Text("← 하루 전") }
                        Text(
                            DateUtils.label(draft),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { draft = draft.plusDays(1) },
                            enabled = draft.isBefore(LocalDate.now())
                        ) { Text("하루 후 →") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { draft = LocalDate.now() }) { Text("오늘") }
                        TextButton(onClick = { draft = LocalDate.now().minusDays(1) }) { Text("어제") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.changeLogDate(editTarget.id, draft)
                    editing = null
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("취소") }
            }
        )
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("기록 삭제") },
            text = { Text("${DateUtils.label(target.date)} · ${target.repsText} 기록을 지울까요? 플랜 진행도도 함께 되돌아갑니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLog(target.id)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun LogRow(
    log: SessionLog,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateUtils.label(log.date), style = MaterialTheme.typography.titleMedium)
                    if (log.synced) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Google 동기화됨",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    "${log.exercise} ${log.repsText}  ·  목표 ${log.targetTotal}개" +
                        if (log.fromTasks) "  ·  Tasks에서 체크" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (log.failed) {
                    Text(
                        "실패 — ${log.shortfall}개 부족, 같은 세션 다시",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (log.note.isNotBlank()) {
                    Text(
                        log.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (log.syncError != null) {
                    Text(
                        log.syncError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text("${log.total}", style = MaterialTheme.typography.headlineSmall)
            if (!log.synced) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "다시 동기화", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", modifier = Modifier.size(18.dp))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
