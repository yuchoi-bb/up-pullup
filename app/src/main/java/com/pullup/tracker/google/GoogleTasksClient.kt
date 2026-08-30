package com.pullup.tracker.google

import com.pullup.tracker.data.TaskListRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Google Tasks REST API 최소 구현.
 * https://developers.google.com/tasks/reference/rest
 */
class GoogleTasksClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    suspend fun listTaskLists(accessToken: String): List<TaskListRef> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/users/@me/lists?maxResults=100")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val body = execute(request)
        val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TaskListRef(id, obj.optString("title", "(제목 없음)"))
        }
    }

    /**
     * 완료 상태의 할 일을 만든다.
     * insert 시 status가 무시되는 경우가 있어 insert -> patch 두 단계로 확실히 완료 처리한다.
     */
    suspend fun createCompletedTask(
        accessToken: String,
        taskListId: String,
        title: String,
        notes: String,
        date: LocalDate
    ): String = withContext(Dispatchers.IO) {
        val completedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val due = date.atStartOfDay().toInstant(ZoneOffset.UTC).let { DateTimeFormatter.ISO_INSTANT.format(it) }

        val insertBody = JSONObject()
            .put("title", title)
            .put("notes", notes)
            .put("due", due)
            .put("status", "completed")
            .put("completed", completedAt)
            .toString()

        val insert = Request.Builder()
            .url("$BASE/lists/$taskListId/tasks")
            .header("Authorization", "Bearer $accessToken")
            .post(insertBody.toRequestBody(JSON_MEDIA))
            .build()
        val created = JSONObject(execute(insert))
        val taskId = created.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Tasks가 할 일 ID를 돌려주지 않았습니다.")

        if (created.optString("status") != "completed") {
            val patchBody = JSONObject()
                .put("status", "completed")
                .put("completed", completedAt)
                .toString()
            val patch = Request.Builder()
                .url("$BASE/lists/$taskListId/tasks/$taskId")
                .header("Authorization", "Bearer $accessToken")
                .patch(patchBody.toRequestBody(JSON_MEDIA))
                .build()
            execute(patch)
        }
        taskId
    }

    suspend fun deleteTask(accessToken: String, taskListId: String, taskId: String) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE/lists/$taskListId/tasks/$taskId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            execute(request)
            Unit
        }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "Google Tasks 오류 ${response.code}" + if (message.isNotBlank()) ": $message" else ""
                )
            }
            return body.ifBlank { "{}" }
        }
    }

    private companion object {
        const val BASE = "https://tasks.googleapis.com/tasks/v1"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
