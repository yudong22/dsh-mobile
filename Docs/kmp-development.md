# KMP 开发说明

## 模块边界

```text
androidApp ──> shared <── KMPSharedAdapter <── SwiftUI/AppStore
   Compose      commonMain             iOS 薄边界
                ├── protocol
                ├── domain
                ├── data
                └── usecase
```

- `shared`：Kotlin Multiplatform library，承载协议模型和纯业务逻辑；不得依赖 Android UI 或 Apple UI 框架。
- `androidApp`：原生 Android application，只负责 Compose UI、Android 生命周期和平台 effect。
- `DeepSeekHarnessMobile`：现有 SwiftUI application；通过 `KMPSharedAdapter` 和粗粒度 `SharedMobileFacade` 链接共享静态 framework，现有网络、持久化、生命周期和 UI 行为仍留在 Swift。

## 工具链

- Java 17
- Gradle 9.1.0
- Android Gradle Plugin 9.0.1
- Kotlin 2.3.20
- compileSdk / targetSdk 36
- minSdk 24

### JDK 17 的获取方式

`androidApp` 声明了 `jvmToolchain(17)`，而 `settings.gradle.kts` 启用了
`foojay-resolver-convention`，因此**不需要**在任何提交进仓库的文件里写死 JDK 路径：
本机没有 JDK 17 时 Gradle 会自动下载匹配的工具链（需要对 `api.foojay.io` 的网络访问；
`--offline` 下则必须本机已装 17）。

CI（GitHub Actions）用 `actions/setup-java` 提供 JDK 17，因此是「当前 JVM」路径。

本机若已装 17、但默认 `JAVA_HOME` 指向别的版本，请用环境变量或**个人**的
`~/.gradle/gradle.properties`（不要提交）指定，例如：

```bash
export JAVA_HOME=$(/opt/homebrew/bin/brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
```

本机 Android SDK 默认位于 `/Users/lichaofan/Library/Android/sdk`。不要提交 `local.properties`；命令行环境没有配置 `ANDROID_HOME` 时，应显式传入：

```bash
ANDROID_HOME=/Users/lichaofan/Library/Android/sdk ./gradlew tasks
```

## 验证命令

```bash
ANDROID_HOME=/Users/lichaofan/Library/Android/sdk \
  ./gradlew :shared:allTests \
  :androidApp:testDebugUnitTest \
  :androidApp:assembleDebug

ANDROID_HOME=/Users/lichaofan/Library/Android/sdk \
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 \
  :androidApp:lintDebug
```

主要产物：

- Android APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- iOS Simulator framework：`shared/build/bin/iosSimulatorArm64/debugFramework/DeepSeekHarnessShared.framework`

## iOS 集成

Xcode target 的 `Build KMP Framework` phase 会根据 Debug/Release、Simulator/Device 和当前架构调用对应的 Gradle link task。Swift 仅从 `KMPSharedAdapter` 访问 `SharedMobileFacade`，不直接耦合内部 Reducer。

首次在新机器构建前需要安装 Java 17、Android SDK，并保证 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或默认的 `$HOME/Library/Android/sdk` 之一有效。之后直接在 Xcode 构建和测试即可，无需手工预编译 framework。

## Android 人工测试

安装并启动 Debug APK 后：

1. 确认首页显示 `DeepSeekHarnessShared · schema 1`。
2. 点击“加载共享 Fixture”，确认出现一条 `android-demo` 运行中 Session、两条对话和“待回答 Human Question：1”。
3. 点击“交给 KMP decoder / reducer”，确认最后 frame 为 `event`，流式临时消息被“最终消息会替换流式临时消息。”替换。
4. 将 Gateway JSON 改为无效文本并提交，确认页面显示错误但既有 Session 和 Conversation 没有丢失。
5. 点击“重置”，确认共享状态清空且应用不崩溃。

## 后续迁移规则

1. 先在 `commonMain` 建立平台无关模型和 Reducer，再由 Android UI 接入。
2. 已迁移领域的 KMP 实现是唯一行为基线；用 `commonTest` 和平台 Adapter 测试验证协议，不再保留 Swift 重复 Reducer/Projection。
3. 网络、磁盘、Keychain、后台任务和 UI 生命周期不得直接进入 `commonMain`。
4. Swift 通过 `KMPSharedAdapter` 访问粗粒度 facade；不要从 SwiftUI 直接操作 Kotlin Reducer 或复杂协程类型。
5. UI Intent 向下进入 KMP，KMP Event 向上发布增量 state patch/effect；Swift 镜像只允许在对应 Event 发布作用域中写入。
6. 新功能不得恢复 Swift/KMP 双 Reducer、运行时迁移开关或只读 shadow 对比路径。
