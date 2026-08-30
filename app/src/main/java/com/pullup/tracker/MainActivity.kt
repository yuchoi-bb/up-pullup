package com.pullup.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pullup.tracker.ui.AppRoot
import com.pullup.tracker.ui.theme.UpPullupTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UpPullupTheme {
                AppRoot()
            }
        }
    }
}
