@file:OptIn(ExperimentalMaterial3Api::class)

package com.pullup.tracker.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pullup.tracker.BuildConfig
import com.pullup.tracker.ai.GeminiClient
import com.pullup.tracker.data.DateUtils
import com.pullup.tracker.ui.MainViewModel
import com.pullup.tracker.ui.components.Pill
import com.pullup.tracker.ui.components.SectionCard

@Composable
fun SettingsScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val settings by viewModel.settings.collectAsState()
    val google by viewModel.googleStatus.collectAsState()
    val taskLists by viewModel.taskLists.collectAsState()
    val update by viewModel.update.collectAsState()
    val coach by viewModel.coach.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onAuthorizationResult(result.data) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateSettings { it.copy(reminderEnabled = granted) }
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
        item { Text("설정", style = MaterialTheme.typography.headlineMedium) }

        // ------------------------------------------------------- Google Tasks
        item {
            SectionCard(title = "Google 할 일 연동") {
                if (!viewModel.googleConfigured) {
                    Text(
                        "이 빌드에는 Google OAuth 클라이언트 ID가 들어 있지 않습니다. " +
                            "저장소 변수 GOOGLE_OAUTH_CLIENT_ID를 설정한 뒤 다시 빌드하면 연동이 켜집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!google.signedIn) {
                    Text(
                        "Google 계정을 연결하면 운동 기록이 선택한 할 일 목록에 '완료' 상태로 올라갑니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { signInLauncher.launch(viewModel.authorizationIntent()) },
                        enabled = !busy
                    ) { Text("Google 계정 연결") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            google.email ?: "연결됨",
                            icon = Icons.Default.CheckCircle,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::signOut) { Text("연결 해제") }
                    }
                    Spacer(Modifier.height(12.dp))
                    TaskListPicker(
                        options = taskLists.map { it.title },
                        selected = settings.taskListTitle ?: "목록을 선택하세요",
                        onSelect = { title ->
                            taskLists.firstOrNull { it.title == title }?.let(viewModel::selectTaskList)
                        },
                        onRefresh = viewModel::refreshTaskLists,
                        busy = busy
                    )
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        label = "기록하면 자동으로 업로드",
                        checked = settings.autoSync,
                        onChange = { value -> viewModel.updateSettings { it.copy(autoSync = value) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = settings.taskTitleTemplate,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(taskTitleTemplate = value) } },
                        label = { Text("할 일 제목 형식") },
                        supportingText = { Text("{exercise} {total} {sets} {date} {session} 사용 가능") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // ------------------------------------------------------- Gemini
        item {
            var visible by remember { mutableStateOf(false) }
            SectionCard(title = "Gemini AI") {
                OutlinedTextField(
                    value = settings.geminiApiKey,
                    onValueChange = { value -> viewModel.updateSettings { it.copy(geminiApiKey = value.trim()) } },
                    label = { Text("Gemini API 키") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(if (visible) "숨기기" else "보기")
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                val models = (GeminiClient.SUGGESTED_MODELS + coach.availableModels).distinct()
                SimpleDropdown(
                    label = "모델",
                    options = models,
                    selected = settings.geminiModel,
                    onSelect = { value -> viewModel.updateSettings { it.copy(geminiModel = value) } }
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    OutlinedButton(onClick = viewModel::verifyGeminiKey, enabled = !busy) { Text("키 확인") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "키는 이 기기에만 저장되며 Google AI Studio에서 발급받을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ------------------------------------------------------- 운동 설정
        item {
            SectionCard(title = "운동 설정") {
                Text("운동하는 요일", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day ->
                        val selected = settings.trainingDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = settings.trainingDays.toMutableList()
                                if (selected) next.remove(day) else next.add(day)
                                viewModel.setTrainingDays(next)
                            },
                            label = { Text(DateUtils.dayNameOf(day)) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                StepperRow(
                    label = "세트 간 휴식",
                    value = "${settings.restSeconds}초",
                    onMinus = {
                        viewModel.updateSettings {
                            it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(15))
                        }
                    },
                    onPlus = {
                        viewModel.updateSettings {
                            it.copy(restSeconds = (it.restSeconds + 15).coerceAtMost(600))
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    label = "자동 조절 (목표 미달이면 같은 세션 반복)",
                    checked = settings.autoRegulate,
                    onChange = { value -> viewModel.updateSettings { it.copy(autoRegulate = value) } }
                )
            }
        }

        // ------------------------------------------------------- 알림
        item {
            SectionCard(title = "알림") {
                ToggleRow(
                    label = "매일 목표 알림",
                    checked = settings.reminderEnabled,
                    onChange = { value ->
                        if (value && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.updateSettings { it.copy(reminderEnabled = value) }
                        }
                    }
                )
                if (settings.reminderEnabled) {
                    Spacer(Modifier.height(10.dp))
                    StepperRow(
                        label = "알림 시각",
                        value = String.format("%02d:%02d", settings.reminderHour, settings.reminderMinute),
                        onMinus = {
                            viewModel.updateSettings {
                                it.copy(reminderHour = (it.reminderHour + 23) % 24)
                            }
                        },
                        onPlus = {
                            viewModel.updateSettings {
                                it.copy(reminderHour = (it.reminderHour + 1) % 24)
                            }
                        }
                    )
                }
            }
        }

        // ------------------------------------------------------- 업데이트
        item {
            SectionCard(title = "앱 업데이트") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("현재 버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                        val release = update.release
                        Text(
                            when {
                                update.checking -> "확인 중..."
                                release == null -> "아직 확인하지 않았습니다."
                                release.isNewer -> "새 버전 ${release.tag} (${release.publishedAt})"
                                else -> "최신 상태입니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { viewModel.checkUpdate() }, enabled = !update.checking) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("확인")
                    }
                }

                val release = update.release
                if (release != null && release.isNewer) {
                    Spacer(Modifier.height(12.dp))
                    if (release.notes.isNotBlank()) {
                        Text(
                            release.notes.lines().take(6).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (update.downloading) {
                        LinearProgressIndicator(
                            progress = { update.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(update.progress * 100).toInt()}% 다운로드 중",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val file = update.downloadedFile
                        if (file == null) {
                            Button(
                                onClick = viewModel::downloadUpdate,
                                enabled = release.apkUrl != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (release.apkUrl != null) {
                                        "${release.tag} 내려받기"
                                    } else {
                                        "이 릴리스에 APK가 없습니다"
                                    }
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (viewModel.canInstallApk()) {
                                        context.startActivity(viewModel.installIntent(file))
                                    } else {
                                        context.startActivity(viewModel.unknownSourcesIntent())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("설치하기") }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    label = "앱 시작할 때 자동 확인",
                    checked = settings.autoCheckUpdate,
                    onChange = { value -> viewModel.updateSettings { it.copy(autoCheckUpdate = value) } }
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "테스트(프리릴리스) 버전 포함",
                    checked = settings.includePrerelease,
                    onChange = { value -> viewModel.updateSettings { it.copy(includePrerelease = value) } }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO} 릴리스에서 APK를 받아옵니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ------------------------------------------------------- 데이터
        item {
            SectionCard(title = "데이터") {
                OutlinedButton(
                    onClick = {
                        val file = viewModel.exportCsv()
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "운동 기록 내보내기"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("기록 CSV로 내보내기") }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun SimpleDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskListPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    busy: Boolean
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("할 일 목록", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("불러오기")
            }
        }
        Spacer(Modifier.height(6.dp))
        if (options.isEmpty()) {
            Text(
                "'불러오기'를 눌러 Google 할 일 목록을 가져오세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SimpleDropdown(label = "선택된 목록", options = options, selected = selected, onSelect = onSelect)
        }
    }
}
