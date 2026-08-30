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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.data.SessionLog
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.RepsBarChart
import com.pullup.tracker.ui.components.SectionCard
import com.pullup.tracker.ui.components.StatTile

@Composable
fun HistoryScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsState()
    val stats = viewModel.stats()
    val logs = data.logs.sortedByDescending { it.date }
    var pendingDelete by remember { mutableStateOf<SessionLog?>(null) }

    val chartLogs = logs.take(14).reversed()

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
            SectionCard(title = "최근 14세션") {
                RepsBarChart(
                    values = chartLogs.map { it.total },
                    labels = chartLogs.filterIndexed { i, _ -> i % 3 == 0 }.map { DateUtils.short(it.date) },
                    goal = viewModel.plan?.goalTotal
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "가로선은 최종 목표(${viewModel.plan?.goalTotal ?: 0}개)입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                "기록",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
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
                onDelete = { pendingDelete = log }
            )
        }
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
private fun LogRow(log: SessionLog, onRetry: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
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
                    "${log.exercise} ${log.repsText}  ·  목표 ${log.targetTotal}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
