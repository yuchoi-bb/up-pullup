package com.pullup.tracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Health Connect가 이 기기에서 쓸 수 있는 상태인지. */
enum class HealthStatus {
    /** 쓸 수 있다. */
    AVAILABLE,

    /** 앱은 있는데 업데이트가 필요하거나, 설치가 안 돼 있다. */
    NEEDS_INSTALL,

    /** 이 기기/안드로이드 버전에서는 지원하지 않는다. */
    UNSUPPORTED
}

/**
 * 워치(가민 등)가 Health Connect에 남긴 운동 세션 한 건.
 *
 * [reps]는 대개 null이다. 확인해 보니 가민은 "운동을 했다"는 세션만 쓰고
 * 세트별 횟수는 안 남긴다. 그래도 필드는 읽어 둔다 — 앱이나 기기가 바뀌어
 * 횟수를 쓰기 시작하면 그때는 그대로 쓸 수 있다.
 */
data class WatchSession(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val reps: Int?
) {
    val minutes: Int get() = java.time.Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)
}

class HealthConnectRepository(private val context: Context) {

    /** 운동 기록 읽기 하나면 된다. 쓰기 권한은 요청하지 않는다. */
    val permissions: Set<String> =
        setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_INSTALL
        else -> HealthStatus.UNSUPPORTED
    }

    private fun clientOrNull(): HealthConnectClient? =
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext false
        runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    /**
     * 그날 하루의 운동 세션. 종목으로 거르지 않는다.
     *
     * 가민이 풀업을 어떤 종목 코드로 올리는지 확인할 방법이 없어서, 다 보여주고
     * 사용자가 고르게 하는 편이 확실하다. 잘못 거르면 아무것도 안 나온다.
     */
    suspend fun sessionsOn(date: LocalDate): List<WatchSession> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        response.records.map { record ->
            // 세그먼트에 횟수가 있으면 합친다. 없으면 null로 둔다.
            val reps = record.segments
                .mapNotNull { it.repetitions.takeIf { count -> count > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.sum()

            WatchSession(
                id = record.metadata.id,
                title = record.title?.takeIf { it.isNotBlank() }
                    ?: exerciseLabel(record.exerciseType),
                start = LocalDateTime.ofInstant(record.startTime, zone),
                end = LocalDateTime.ofInstant(record.endTime, zone),
                reps = reps
            )
        }.sortedBy { it.start }
    }

    /** 종목 코드를 사람이 읽을 이름으로. 모르는 코드는 그냥 "운동"으로 둔다. */
    private fun exerciseLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "근력 운동"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "맨몸 운동"
        ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "체조"
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "기타 운동"
        else -> "운동"
    }

    companion object {
        /** Health Connect 설치/업데이트로 보내는 Play 스토어 주소. */
        const val PLAY_STORE_URI = "market://details?id=com.google.android.apps.healthdata"

        fun timeRange(session: WatchSession): String {
            val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            return "${session.start.format(f)}~${session.end.format(f)}"
        }
    }
}
