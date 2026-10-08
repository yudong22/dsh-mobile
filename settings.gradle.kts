pluginManagement {
    repositories {
        // 官方仓库优先：此前把第三方镜像放在最前面，会让镜像替所有能命中的坐标做解析
        // （供应链风险），并且实测拖慢了 GitHub Actions 上的依赖解析。
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // 镜像只作为兜底（官方仓库不可达时使用）。需要强制走镜像时，可在
        // ~/.gradle/init.gradle.kts 里按需前插，而不是写死在本文件。
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/google/")
        maven("https://maven.aliyun.com/repository/gradle-plugin/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/google/")
    }
}

// 工具链自动下载：没有它的话 `jvmToolchain(17)` 只能靠本机已装的 JDK 17，
// 本机装的是 21 时会直接构建失败（此前靠 gradle.properties 里写死一个
// /opt/homebrew/Cellar/... 的绝对路径绕过，那把开发者机器写进了仓库）。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dsh-mobile-kmm"

include(":shared")
include(":androidApp")
