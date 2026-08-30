package com.pullup.tracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pullup.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val apkUrl: String?,
    val apkSize: Long,
    val publishedAt: String,
    val prerelease: Boolean,
    val htmlUrl: String
) {
    val isNewer: Boolean get() = UpdateManager.compareVersions(version, UpdateManager.currentVersion()) > 0
}

/** GitHub Releases에서 새 APK를 찾아 내려받고 설치까지 이어준다. */
class UpdateManager(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatest(includePrerelease: Boolean): ReleaseInfo = withContext(Dispatchers.IO) {
        val url = if (includePrerelease) {
            "$API/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases?per_page=10"
        } else {
            "$API/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        }
        val body = get(url)
        val obj = if (includePrerelease) {
            val array = JSONArray(body)
            if (array.length() == 0) throw IllegalStateException("공개된 릴리스가 아직 없습니다.")
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .firstOrNull { !it.optBoolean("draft", false) }
                ?: throw IllegalStateException("공개된 릴리스가 아직 없습니다.")
        } else {
            JSONObject(body)
        }
        parseRelease(obj)
    }

    private fun parseRelease(obj: JSONObject): ReleaseInfo {
        val tag = obj.optString("tag_name")
        val assets = obj.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize = 0L
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets?.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                apkSize = asset.optLong("size", 0L)
                break
            }
        }
        return ReleaseInfo(
            tag = tag,
            version = normalize(tag),
            name = obj.optString("name").ifBlank { tag },
            notes = obj.optString("body").orEmpty(),
            apkUrl = apkUrl,
            apkSize = apkSize,
            publishedAt = obj.optString("published_at").take(10),
            prerelease = obj.optBoolean("prerelease", false),
            htmlUrl = obj.optString("html_url")
        )
    }

    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = release.apkUrl ?: throw IllegalStateException("이 릴리스에는 APK 파일이 없습니다.")
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "up-pullup-${release.version}.apk")

            val request = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("다운로드 실패 (${response.code})")
                val body = response.body ?: throw IllegalStateException("응답이 비어 있습니다.")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var written = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
            target
        }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw IllegalStateException("아직 공개된 릴리스가 없습니다.")
                }
                throw IllegalStateException("GitHub 오류 ${response.code}")
            }
            return body
        }
    }

    companion object {
        private const val API = "https://api.github.com"

        fun currentVersion(): String = normalize(BuildConfig.VERSION_NAME)

        fun normalize(raw: String): String =
            raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')

        /** 1이면 a가 더 최신. */
        fun compareVersions(a: String, b: String): Int {
            val left = normalize(a).split('.').map { it.toIntOrNull() ?: 0 }
            val right = normalize(b).split('.').map { it.toIntOrNull() ?: 0 }
            val size = maxOf(left.size, right.size)
            for (i in 0 until size) {
                val l = left.getOrElse(i) { 0 }
                val r = right.getOrElse(i) { 0 }
                if (l != r) return if (l > r) 1 else -1
            }
            return 0
        }
    }
}
