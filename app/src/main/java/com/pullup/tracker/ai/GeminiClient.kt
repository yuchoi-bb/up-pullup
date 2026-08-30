package com.pullup.tracker.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Gemini API(Generative Language API) 최소 클라이언트. */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    suspend fun generateText(
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String? = null,
        jsonOutput: Boolean = false,
        temperature: Double = 0.4
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API 키가 설정되지 않았습니다." }

        val generationConfig = JSONObject().put("temperature", temperature)
        if (jsonOutput) generationConfig.put("responseMimeType", "application/json")

        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put("generationConfig", generationConfig)

        if (!systemInstruction.isNullOrBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }

        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val body = execute(request)
        parseText(body)
    }

    /** 키가 살아있는지 확인하고 사용 가능한 모델 이름을 돌려준다. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models?pageSize=100")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        val models = JSONObject(execute(request)).optJSONArray("models") ?: return@withContext emptyList()
        (0 until models.length()).mapNotNull { i ->
            models.optJSONObject(i)?.optString("name")?.removePrefix("models/")?.takeIf { it.isNotBlank() }
        }.filter { it.startsWith("gemini") }
    }

    private fun parseText(body: String): String {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blocked = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw IllegalStateException(
                if (blocked.isNotBlank()) "Gemini가 응답을 거부했습니다 ($blocked)." else "Gemini 응답이 비어 있습니다."
            )
        }
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            for (i in 0 until (parts?.length() ?: 0)) {
                append(parts?.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
        if (text.isBlank()) throw IllegalStateException("Gemini 응답에 텍스트가 없습니다.")
        return text
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "Gemini 오류 ${response.code}" + if (message.isNotBlank()) ": $message" else ""
                )
            }
            return body
        }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val SUGGESTED_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite"
        )
    }
}
