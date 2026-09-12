import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Google OAuth 클라이언트 ID(Android 유형)는 비밀값이 아니므로 gradle 속성 또는
 * 환경 변수로 주입한다. 비어 있으면 앱은 정상 빌드되지만 Google Tasks 연동만 비활성화된다.
 */
val googleOauthClientId: String =
    (project.findProperty("GOOGLE_OAUTH_CLIENT_ID") as String?)
        ?: System.getenv("GOOGLE_OAUTH_CLIENT_ID")
        ?: ""

/** com.googleusercontent.apps.<숫자-해시> 형태의 역방향 스킴 (AppAuth 리다이렉트용) */
val appAuthRedirectScheme: String =
    if (googleOauthClientId.endsWith(".apps.googleusercontent.com")) {
        "com.googleusercontent.apps." + googleOauthClientId.removeSuffix(".apps.googleusercontent.com")
    } else {
        "com.pullup.tracker.unconfigured"
    }

val baseVersionName = "1.0.0"
val appVersionName: String = (project.findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
    ?: baseVersionName
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

val githubOwner: String = (project.findProperty("GITHUB_OWNER") as String?) ?: "yuchoi-bb"
val githubRepo: String = (project.findProperty("GITHUB_REPO") as String?) ?: "up-pullup"

/** CI에서 keystore.properties 또는 환경 변수로 릴리스 서명 정보를 넘긴다. */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val hasReleaseKeystore = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.pullup.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pullup.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        manifestPlaceholders["appAuthRedirectScheme"] = appAuthRedirectScheme

        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOauthClientId\"")
        buildConfigField("String", "OAUTH_REDIRECT_SCHEME", "\"$appAuthRedirectScheme\"")
        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        // 고정 서명 키로 빌드되었는지. false면 덮어쓰기 설치가 불가능하므로
        // 앱이 업데이트 안내 문구와 백업 유도를 다르게 보여준다.
        buildConfigField("boolean", "STABLE_SIGNING", hasReleaseKeystore.toString())
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            // 기기에서 직접 검증하기 어려운 개인용 빌드라 축소는 꺼 둔다.
            // 켤 때는 proguard-rules.pro의 직렬화/AppAuth 규칙이 함께 적용된다.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 릴리스 keystore가 없으면 debug 키로 서명해 CI 산출물이 항상 설치 가능하도록 한다.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.health.connect)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.appauth)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
