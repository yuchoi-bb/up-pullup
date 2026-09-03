@file:OptIn(ExperimentalMaterial3Api::class)

package com.pullup.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import com.pullup.tracker.ui.screens.CoachScreen
import com.pullup.tracker.ui.screens.HistoryScreen
import com.pullup.tracker.ui.screens.PlanScreen
import com.pullup.tracker.ui.screens.SettingsScreen
import com.pullup.tracker.ui.screens.TodayScreen

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("오늘", Icons.Default.Home),
    Tab("플랜", Icons.Default.List),
    Tab("기록", Icons.Default.DateRange),
    Tab("AI 코치", Icons.Default.Star),
    Tab("설정", Icons.Default.Settings)
)

@Composable
fun AppRoot(viewModel: MainViewModel = viewModel()) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(message?.id) {
        val current = message
        if (current != null) {
            snackbarHostState.showSnackbar(current.text)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(Unit) {
        if (settings.autoCheckUpdate) viewModel.checkUpdate(silent = true)
        // Google Tasks는 변경 알림을 주지 않으므로, 앱을 열 때마다 직접 확인한다.
        viewModel.syncWithGoogleTasks()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            val topPadding = PaddingValues(top = padding.calculateTopPadding())
            when (selected) {
                0 -> TodayScreen(viewModel, topPadding)
                1 -> PlanScreen(viewModel, topPadding)
                2 -> HistoryScreen(viewModel, topPadding)
                3 -> CoachScreen(viewModel, topPadding)
                else -> SettingsScreen(viewModel, topPadding)
            }
        }
    }
}
