package com.pullup.tracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pullup.tracker.data.SettingsRepository

/** 재부팅 후 알림을 다시 예약한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = SettingsRepository(context).settings.value
        if (settings.reminderEnabled) {
            ReminderScheduler.schedule(context, settings.reminderHour, settings.reminderMinute)
        }
    }
}
