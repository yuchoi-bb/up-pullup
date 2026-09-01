package com.pullup.tracker.google

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pullup.tracker.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleAuthStatus(
    val configured: Boolean,
    val signedIn: Boolean,
    val email: String? = null
)

/**
 * AppAuth 기반 Google OAuth. 클라이언트 ID는 빌드 시점에 주입되며(BuildConfig),
 * 비어 있으면 앱은 그대로 동작하고 Google Tasks 연동만 꺼진다.
 */
class GoogleAccountManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("google-auth", Context.MODE_PRIVATE)
    private val authService = AuthorizationService(context)

    private var authState: AuthState = readState()

    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<GoogleAuthStatus> = _status.asStateFlow()

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotBlank()

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    private fun redirectUri(): Uri = Uri.parse("${BuildConfig.OAUTH_REDIRECT_SCHEME}:/oauth2redirect")

    fun authorizationIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            redirectUri()
        )
            .setScopes(SCOPE_TASKS, "openid", "email")
            .setPrompt(AuthorizationRequest.Prompt.CONSENT)
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** 인증 화면에서 돌아온 Intent를 처리하고 토큰 교환까지 끝낸다. */
    suspend fun handleAuthorizationResult(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(error ?: IllegalStateException("Google 로그인이 취소되었습니다."))
        }
        authState = AuthState(response, error)
        return runCatching {
            suspendCancellableCoroutine { cont ->
                authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                    authState.update(tokenResponse, ex)
                    saveState()
                    if (tokenResponse != null) {
                        cont.resume(Unit)
                    } else {
                        cont.resumeWithException(ex ?: IllegalStateException("토큰 교환에 실패했습니다."))
                    }
                }
            }
        }.also { refreshStatus() }
    }

    /** 필요하면 자동으로 갱신된 액세스 토큰을 돌려준다. */
    suspend fun accessToken(): String = suspendCancellableCoroutine { cont ->
        if (!authState.isAuthorized) {
            cont.resumeWithException(IllegalStateException("Google 계정이 연결되지 않았습니다."))
            return@suspendCancellableCoroutine
        }
        authState.performActionWithFreshTokens(authService) { token, _, ex ->
            saveState()
            if (token != null) {
                cont.resume(token)
            } else {
                // 갱신 토큰 만료가 가장 흔한 원인이라 다시 연결하라고 분명히 말해 준다.
                val detail = ex?.errorDescription ?: ex?.error
                cont.resumeWithException(
                    IllegalStateException(
                        "Google 인증이 만료되었습니다. 설정에서 계정을 다시 연결해 주세요." +
                            if (detail != null) " ($detail)" else ""
                    )
                )
            }
        }
    }

    fun signOut() {
        authState = AuthState()
        prefs.edit().remove(KEY_STATE).apply()
        refreshStatus()
    }

    fun dispose() = authService.dispose()

    private fun currentStatus(): GoogleAuthStatus = GoogleAuthStatus(
        configured = isConfigured,
        signedIn = authState.isAuthorized,
        email = emailFromIdToken(authState.idToken)
    )

    private fun refreshStatus() {
        _status.value = currentStatus()
    }

    /** id_token(JWT) 페이로드에서 email 클레임만 꺼낸다. */
    private fun emailFromIdToken(idToken: String?): String? = runCatching {
        val payload = idToken?.split(".")?.getOrNull(1) ?: return@runCatching null
        val decoded = String(
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
            Charsets.UTF_8
        )
        org.json.JSONObject(decoded).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun readState(): AuthState = runCatching {
        prefs.getString(KEY_STATE, null)?.let { AuthState.jsonDeserialize(it) } ?: AuthState()
    }.getOrElse { AuthState() }

    private fun saveState() {
        prefs.edit().putString(KEY_STATE, authState.jsonSerializeString()).apply()
        refreshStatus()
    }

    companion object {
        const val SCOPE_TASKS = "https://www.googleapis.com/auth/tasks"
        private const val KEY_STATE = "auth_state"
    }
}
