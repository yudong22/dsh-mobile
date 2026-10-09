import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.clarklevis.dsh.shared"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest {}
    }

    // iOS targets 已移除：iOS 客户端（DeepSeekHarnessMobile / .xcodeproj /
    // AgentLiveActivityWidget）连同其测试已从仓库删除，仓库现在只交付 Android。
    //
    // 之前这里的 iosX64/iosArm64/iosSimulatorArm64 会让每次 `:shared:*` 构建都
    // 额外编译三个 Kotlin/Native 目标（含各自的 framework 链接），是构建时间的主要浪费。
    //
    // 注意：`commonMain` / `commonTest` 保持原名不动。删掉 iOS 后这两个 source set
    // 就是唯一的源码集，改名成 androidMain/androidTest 只是名字更贴近现状，
    // 但需要移动 59 个文件并重写包路径，零性能收益，因此不做。

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
