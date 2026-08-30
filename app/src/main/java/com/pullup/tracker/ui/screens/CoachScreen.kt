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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.SectionCard

@Composable
fun CoachScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val settings by viewModel.settings.collectAsState()
    val coach by viewModel.coach.collectAsState()
    val currentSession = viewModel.currentSession()
    val plan = viewModel.plan

    var exercise by remember { mutableStateOf(plan?.exercise ?: "풀업") }
    var currentReps by remember {
        mutableStateOf(currentSession?.targets?.joinToString(", ") ?: "6, 5, 5, 4, 4")
    }
    var goalPerSet by remember { mutableStateOf("20") }
    var weeks by remember { mutableStateOf("10") }
    var daysPerWeek by remember { mutableStateOf(settings.trainingDays.size.toString()) }
    var extra by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }

    fun parsedReps(): List<Int> = currentReps.split(",", "/", " ")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }

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
                Text("AI 코치", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gemini가 다음 종목·목표·기간에 맞는 세션 계획을 짜 줍니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (settings.geminiApiKey.isBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "설정 탭에서 Gemini API 키를 넣으면 AI 기능이 켜집니다. 키가 없어도 아래 '앱 규칙으로 만들기'는 동작합니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "새 플랜 만들기") {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = { exercise = it },
                    label = { Text("운동 종목") },
                    placeholder = { Text("예: 푸시업, 랫풀다운, 딥스") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = currentReps,
                    onValueChange = { currentReps = it },
                    label = { Text("현재 세트별 개수") },
                    placeholder = { Text("6, 5, 5, 4, 4") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = goalPerSet,
                        onValueChange = { goalPerSet = it.filter { c -> c.isDigit() } },
                        label = { Text("세트당 목표") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weeks,
                        onValueChange = { weeks = it.filter { c -> c.isDigit() } },
                        label = { Text("목표 기간(주)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = daysPerWeek,
                        onValueChange = { daysPerWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("주 n회") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("추가 요청 (선택)") },
                    placeholder = { Text("어깨가 안 좋으니 주 1회는 가볍게") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "총 목표: ${parsedReps().size}세트 × ${goalPerSet.toIntOrNull() ?: 0}개 = " +
                        "${parsedReps().size * (goalPerSet.toIntOrNull() ?: 0)}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.generatePlan(
                                exercise = exercise,
                                currentSets = parsedReps(),
                                goalPerSet = goalPerSet.toIntOrNull() ?: 20,
                                daysPerWeek = daysPerWeek.toIntOrNull() ?: 5,
                                weeks = weeks.toIntOrNull(),
                                extraNote = extra
                            )
                        },
                        enabled = !coach.busy && parsedReps().isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (coach.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("AI로 만들기")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.generatePlanLocally(
                                name = "$exercise 플랜",
                                exercise = exercise,
                                currentSets = parsedReps(),
                                goalPerSet = goalPerSet.toIntOrNull() ?: 20,
                                weeks = weeks.toIntOrNull()
                            )
                        },
                        enabled = parsedReps().isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("앱 규칙으로") }
                }
            }
        }

        if (coach.error != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            coach.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = viewModel::clearCoachError) { Text("닫기") }
                    }
                }
            }
        }

        val generated = coach.generated
        if (generated != null) {
            item {
                SectionCard(title = "미리보기") {
                    Text(generated.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        generated.goal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (generated.advice.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(generated.advice, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "총 ${generated.sessions.size}세션 · " +
                            "시작 ${generated.sessions.first().total}개 → 목표 ${generated.sessions.last().total}개",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    val preview = generated.sessions.take(6) + generated.sessions.takeLast(2)
                    preview.distinctBy { it.index }.forEach { session ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                "#${session.index}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(38.dp)
                            )
                            Text(session.targets.joinToString(" / "), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text("${session.total}개", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = viewModel::applyGeneratedPlan, modifier = Modifier.fillMaxWidth()) {
                        Text("이 플랜 적용하기")
                    }
                }
            }
        }

        item {
            SectionCard(title = "코치에게 물어보기") {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("질문") },
                    placeholder = { Text("어제 목표를 못 채웠는데 오늘 어떻게 할까?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = { viewModel.askCoach(question) },
                        enabled = !coach.busy && question.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("물어보기")
                    }
                    Spacer(Modifier.width(10.dp))
                    FilterChip(
                        selected = false,
                        onClick = { question = "오늘 컨디션이 별로인데 목표를 조정해야 할까?" },
                        label = { Text("예시 질문") }
                    )
                }
                if (coach.answer.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(coach.answer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
