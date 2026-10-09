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
        applicationId = "com.clarklevis.dsh.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 26
        versionName = "1.9.7"
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
        buildConfig = false
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

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
