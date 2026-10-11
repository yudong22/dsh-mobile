import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The google-services plugin aborts the build when `google-services.json` is absent, and that file
// is deliberately not committed (same policy as the keystore). CI provisions it from secrets for
// release builds; every other job — including pull-request CI, which runs on a clean checkout —
// has no config and must still build.
//
// A `plugins {}` entry cannot be conditional, so the plugin is applied imperatively below. The
// version is pinned in the root `build.gradle.kts`, matching the official setup guide.
val googleServicesFile = file("google-services.json")

if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * Release signing material. The keystore must never be committed, so it is resolved from
 * outside the repository:
 *  - CI: `release.yml` decodes the keystore secrets and points `DSH_KEYSTORE_PROPERTIES` at them.
 *  - Local: `~/dsh-release/keystore.properties` (see `Docs/release-signing.md`).
 *
 * Reading these at configuration time is deliberate: Gradle records the file/env-var reads as
 * configuration-cache inputs, so a keystore appearing later invalidates the cached configuration
 * instead of silently building an unsigned APK.
 */
val releaseKeystoreProperties: Properties? = run {
    val explicit = System.getenv("DSH_KEYSTORE_PROPERTIES")
    val candidates = listOfNotNull(
        explicit?.takeIf { it.isNotBlank() }?.let(::File),
        rootProject.file("keystore.properties").takeIf { it.isFile },
        File(System.getProperty("user.home"), "dsh-release/keystore.properties").takeIf { it.isFile }
    )
    candidates.firstOrNull()?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
}

android {
    namespace = "com.clarklevis.dsh.android"
    compileSdk = 36

    defaultConfig {
        // 与 vivo/Mi Push 开发者后台登记的应用包名对齐（均为 com.yudong22.dshapp），
        // 否则系统级推送的 regId 无法绑定到本应用、消息会被丢弃。
        applicationId = "com.yudong22.dshapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 27
        versionName = "1.9.8"

        // Mi Push app id/key (Xiaomi/Redmi system push). Read from `local.properties`
        // (the same file Gradle uses for the SDK location); absent there, these stay
        // empty and Mi Push registration is skipped at runtime — the app still builds
        // and runs on non-Xiaomi hardware.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.isFile }
                ?.inputStream()?.use(::load)
        }
        val miPushAppId = localProps.getProperty("mipush.appId", "").trim()
        val miPushAppKey = localProps.getProperty("mipush.appKey", "").trim()
        buildConfigField("String", "MIPUSH_APP_ID", "\"$miPushAppId\"")
        buildConfigField("String", "MIPUSH_APP_KEY", "\"$miPushAppKey\"")

        // vivo push app id/key (vivo/iQOO system push). Same `local.properties` source;
        // absent → empty → registration skipped at runtime on non-vivo hardware.
        val vivoAppId = localProps.getProperty("vivo.appId", "").trim()
        val vivoAppKey = localProps.getProperty("vivo.appKey", "").trim()
        buildConfigField("String", "VIVO_APP_ID", "\"$vivoAppId\"")
        buildConfigField("String", "VIVO_APP_KEY", "\"$vivoAppKey\"")
        // 注入到 AndroidManifest 的 vivo 接收器 meta-data（com.vivo.push.app_id/api_key）。
        // 缺失时为空字符串，SDK 初始化会抛异常并由 VivoPushInitializer 静默跳过。
        manifestPlaceholders["vivoAppId"] = vivoAppId
        manifestPlaceholders["vivoAppKey"] = vivoAppKey
    }

    testOptions {
        // 关掉系统动画：仅在跑设备测试时有意义（历史上如此设置）。设备测试已移除，
        // 这个开关对 JVM 单测无副作用，保留不影响 `testDebugUnitTest`。
        animationsDisabled = true
    }

    signingConfigs {
        // Only registered when real material is present. Without it the release variant stays
        // unsigned (AGP emits `*-unsigned.apk`); `release.yml` then refuses to publish it, so a
        // debug-signed or unsigned artifact can never be shipped as a Release again.
        releaseKeystoreProperties?.let { properties ->
            create("release") {
                storeFile = File(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Deliberately not defaulted to the debug config: a debug-signed artifact carries a
            // per-machine/per-runner random key and cannot be upgraded over.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.backdrop)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    // Firebase BoM keeps every Firebase artifact on a mutually compatible set; individual
    // libraries must not carry their own version.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Mi Push SDK (Xiaomi/Redmi background-freeze-proof channel). The AAR is committed
    // under libs/ so a build needs no extra repository; this declaration is skipped when
    // the file is absent, keeping the project buildable without it.
    val miPushAar = file("libs/MiPush_SDK_Client_7_12_4-C_3rd.aar")
    if (miPushAar.exists()) implementation(files(miPushAar))

    // vivo push SDK (vivo/iQOO background-freeze-proof channel). Same pattern as Mi Push:
    // the AAR is committed under libs/ and this declaration is skipped when the file is absent.
    val vivoPushAar = file("libs/vpush_clientSDK_v4.1.7.0_517.aar")
    if (vivoPushAar.exists()) implementation(files(vivoPushAar))

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
