package com.pullup.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val taskListId: String? = null,
    val taskListTitle: String? = null,
    val autoSync: Boolean = true,
    val taskTitleTemplate: String = DEFAULT_TITLE_TEMPLATE,
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val trainingDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val restSeconds: Int = 120,
    val autoRegulate: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val includePrerelease: Boolean = false,
    val autoCheckUpdate: Boolean = true
) {
    companion object {
        const val DEFAULT_TITLE_TEMPLATE = "{exercise} {total}개 ({sets})"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pullup-settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        taskListId = prefs.getString(KEY_TASK_LIST_ID, null),
        taskListTitle = prefs.getString(KEY_TASK_LIST_TITLE, null),
        autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true),
        taskTitleTemplate = prefs.getString(KEY_TITLE_TEMPLATE, AppSettings.DEFAULT_TITLE_TEMPLATE)
            ?: AppSettings.DEFAULT_TITLE_TEMPLATE,
        geminiApiKey = prefs.getString(KEY_GEMINI_KEY, "").orEmpty(),
        geminiModel = prefs.getString(KEY_GEMINI_MODEL, AppSettings.DEFAULT_GEMINI_MODEL)
            ?: AppSettings.DEFAULT_GEMINI_MODEL,
        trainingDays = prefs.getString(KEY_TRAINING_DAYS, "1,2,3,4,5")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.distinct()
            ?.sorted()
            ?: listOf(1, 2, 3, 4, 5),
        restSeconds = prefs.getInt(KEY_REST_SECONDS, 120),
        autoRegulate = prefs.getBoolean(KEY_AUTO_REGULATE, true),
        reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false),
        reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 19),
        reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0),
        includePrerelease = prefs.getBoolean(KEY_PRERELEASE, false),
        autoCheckUpdate = prefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_TASK_LIST_ID, next.taskListId)
            .putString(KEY_TASK_LIST_TITLE, next.taskListTitle)
            .putBoolean(KEY_AUTO_SYNC, next.autoSync)
            .putString(KEY_TITLE_TEMPLATE, next.taskTitleTemplate)
            .putString(KEY_GEMINI_KEY, next.geminiApiKey)
            .putString(KEY_GEMINI_MODEL, next.geminiModel)
            .putString(KEY_TRAINING_DAYS, next.trainingDays.joinToString(","))
            .putInt(KEY_REST_SECONDS, next.restSeconds)
            .putBoolean(KEY_AUTO_REGULATE, next.autoRegulate)
            .putBoolean(KEY_REMINDER_ENABLED, next.reminderEnabled)
            .putInt(KEY_REMINDER_HOUR, next.reminderHour)
            .putInt(KEY_REMINDER_MINUTE, next.reminderMinute)
            .putBoolean(KEY_PRERELEASE, next.includePrerelease)
            .putBoolean(KEY_AUTO_CHECK_UPDATE, next.autoCheckUpdate)
            .apply()
        _settings.value = next
    }

    private companion object {
        const val KEY_TASK_LIST_ID = "task_list_id"
        const val KEY_TASK_LIST_TITLE = "task_list_title"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_TITLE_TEMPLATE = "title_template"
        const val KEY_GEMINI_KEY = "gemini_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_TRAINING_DAYS = "training_days"
        const val KEY_REST_SECONDS = "rest_seconds"
        const val KEY_AUTO_REGULATE = "auto_regulate"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val KEY_PRERELEASE = "include_prerelease"
        const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    }
}
