package com.pullup.tracker.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pullup.tracker.MainActivity
import com.pullup.tracker.R
import com.pullup.tracker.data.AppRepository
import com.pullup.tracker.data.SettingsRepository
import java.time.LocalDate

/** 매일 정해진 시간에 오늘 목표를 알려주고, 다음 날 알림을 다시 예약한다. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsRepository(context).settings.value
        if (settings.reminderEnabled) {
            ReminderScheduler.schedule(context, settings.reminderHour, settings.reminderMinute)
        }

        val repository = AppRepository(context)
        val data = repository.data.value
        val plan = repository.planOf(data)
        val today = LocalDate.now().toString()
        val alreadyDone = data.logs.any { it.date == today && it.planId == plan?.id }
        if (plan == null || alreadyDone) return

        val session = repository.currentSession(data, plan) ?: return
        val isTrainingDay = plan.trainingDays.isEmpty() ||
            plan.trainingDays.contains(LocalDate.now().dayOfWeek.value)
        if (!isTrainingDay) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pullup)
            .setContentTitle("오늘의 ${plan.exercise} — 총 ${session.total}개")
            .setContentText(session.targets.joinToString(" / ") { "${it}개" })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
