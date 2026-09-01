package com.pullup.tracker.google

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 지금 설치된 APK가 어떤 키로 서명됐는지 앱 안에서 그대로 보여 주기 위한 것.
 *
 * Google Cloud Console의 Android OAuth 클라이언트는 "패키지명 + 서명 인증서 SHA-1"에
 * 묶여 있는데, 어느 지문을 등록해야 하는지 헷갈려서 로그인이 조용히 실패하는 일이 잦다.
 * 설정 화면에서 이 값을 그대로 복사해 등록하면 그 문제가 사라진다.
 */
object AppSigningInfo {

    fun sha1(context: Context): String? = fingerprint(context, "SHA-1")

    private fun fingerprint(context: Context, algorithm: String): String? = runCatching {
        val manager = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
        } ?: return@runCatching null

        MessageDigest.getInstance(algorithm)
            .digest(signature.toByteArray())
            .joinToString(":") { byte -> "%02X".format(byte) }
    }.getOrNull()
}
