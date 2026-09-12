package com.pullup.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.pullup.tracker.ai.GeminiClient
import com.pullup.tracker.data.AppRepository
import com.pullup.tracker.data.SettingsRepository
import com.pullup.tracker.google.GoogleAccountManager
import com.pullup.tracker.google.GoogleTasksClient
import com.pullup.tracker.health.HealthConnectRepository
import com.pullup.tracker.reminder.ReminderScheduler
import com.pullup.tracker.update.UpdateManager

/** 앱 전역 싱글턴 묶음. DI 프레임워크 없이 Application에서 직접 들고 있는다. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val repository = AppRepository(context)
    val google = GoogleAccountManager(context)
    val tasks = GoogleTasksClient()
    val gemini = GeminiClient()
    val health = HealthConnectRepository(context)
    val updates = UpdateManager(context)
}

class PullupApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        container.repository.seedIfNeeded(container.settings.settings.value.trainingDays)
        val settings = container.settings.settings.value
        if (settings.reminderEnabled) {
            ReminderScheduler.schedule(this, settings.reminderHour, settings.reminderMinute)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = getString(R.string.reminder_channel_desc) }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
