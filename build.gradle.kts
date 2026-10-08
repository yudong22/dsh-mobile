plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    // Applied by `:androidApp`. Declared here with `apply false` so the version lives in one place.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
