<div align="center">

<img src="Design/whale-girl-ios-app-promo-16x9-v3.png" alt="DeepSeek Harness Mobile 应用展示" width="100%">

# DeepSeek Harness Mobile

**使用 Kotlin Multiplatform 共享核心逻辑的 Android 原生移动客户端。**

通过 Mobile Gateway 在手机上访问 DeepSeek Harness 的工作区、会话、实时对话、Agent 轨迹与文件。

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-Realtime-2563EB)

</div>

## 项目简介

DeepSeek Harness Mobile（DshMobile）是面向 DeepSeek Harness 的社区原生移动客户端。业务逻辑放在 Kotlin Multiplatform（KMP）共享层，平台层只保留原生能力：

- `shared` 负责协议、状态机、Reducer、投影和同步逻辑，是共享业务状态的唯一来源。
- `androidApp` 使用 Kotlin、Jetpack Compose 和 OkHttp 实现 Android 产品能力。

客户端通过 [`dsh-plugin-mobile-gateway`](https://github.com/Clarklevis1995/dsh-plugin-mobile-gateway) 与 Harness 建立 WebSocket 连接。网络、安全存储、文件、图片、生命周期和后台任务由 Android 原生实现，业务规则则尽可能收敛到 `shared/commonMain`。

> **iOS 客户端已移除。** 仓库此前同时提供 SwiftUI/UIKit 实现；该实现（`DeepSeekHarnessMobile/`、
> `DeepSeekHarnessMobileTests/`、`AgentLiveActivityWidget/`、`.xcodeproj`）与其 vendored
> Swift 依赖 `Vendor/swift-markdown-ui` 已一并删除，`shared` 的三个 Kotlin/Native target
> 也已移除。**因此仓库现在只交付 Android**，`shared` 虽是 KMP 结构，但当前只服务 Android。
> 需要时 iOS 实现可从 git 历史恢复（`git show 57f351f:DeepSeekHarnessMobile/...`）。

## 当前平台状态

| 平台/模块 | 当前状态 | 主要实现 |
| --- | --- | --- |
| KMP `shared` | 已接入 Android | Gateway 协议、Store/Reducer、Conversation/Trajectory 投影、History 同步、Question/Approval、Session Control、工作区文件状态与代码预览支持 |
| Android | 已实现原生客户端 | Jetpack Compose、OkHttp WebSocket、DataStore（非敏感配置）、Keystore（凭据）、CameraX/ML Kit 扫码、前台服务、附件缓存、文件下载与预览 |

## 功能亮点

- **原生实时对话**：处理 WebSocket 增量事件，展示 Markdown、代码、思考过程、工具调用、工具结果与图片附件。
- **历史与实时解耦**：分页加载历史记录时保留实时尾部，按序列与事件身份去重，避免消息重复或回退。
- **完整 Agent 轨迹**：查看 User、Assistant、Tool 等事件，以及调用参数、结果、Schema、Token 和耗时信息。
- **工作区与会话管理**：远程浏览、创建和切换 Harness 主机上的工作区，搜索、创建、切换及归档会话。
- **工作区文件**：浏览 Harness 主机上的远端目录，分块下载文件，校验完整性，预览常见代码文件或交给系统应用打开。
- **交互式 Agent 流程**：支持 Human Question 与工具审批请求，可提交、取消、拒绝或单次允许。
- **会话和默认配置**：管理 Agent 预设、Provider/模型、思考等级与 `read-only`、`workspace-write`、`danger-full-access` Harness Agent 权限；这些不是手机本地文件系统权限。
- **安全设备配对**：扫描 WebUI 二维码或手动输入配对信息；长期凭据保存到平台安全存储。
- **移动端生命周期**：Agent 回合执行期间通过前台服务维持连接。
- **浅色与深色主题**：原生主题适配，并保留 Harness 的深海、网格与鲸鱼视觉语言。

## 架构

```text
                         DeepSeek Harness
                                │
                  dsh-plugin-mobile-gateway
                                │
                             WebSocket
                                │
                    Android OkHttp Transport
                                │
                       KMP Gateway Runtime
                                │
                    KMP shared / commonMain
            Protocol · Store · Reducer · Projection
       History Sync · Question/Approval · Workspace Files
                                │
                       Jetpack Compose
                           Android UI
```

### 共享层职责

- `protocol/`：Gateway DTO、JSON 表示与 wire decoder。
- `gateway/`：平台传输接口，以及配对、鉴权、请求关联、重连与事件背压策略。Android 使用完整共享 Runtime，OkHttp 只实现底层传输。
- `domain/`：Session、Question、Approval 和 Session Control 的 Reducer。
- `projection/`：Conversation 与 Trajectory 的增量投影。
- `sync/`：History 分页、实时尾部合并和去重。
- `facade/`：面向平台层的粗粒度 Store、Intent、Event，以及工作区路径、分块顺序、进度与 SHA-256 校验逻辑。

共享层不依赖 Compose、Android Framework 或任何 UI 框架，因此是可测试的纯业务层。

Android 侧负责安全存储、图片处理、临时文件落盘、系统分享/打开以及应用生命周期。共享层负责远端文件协议、状态流转、分块一致性和完整性校验，不直接操作平台文件系统。

## 项目结构

```text
.
├── shared/                         # Kotlin Multiplatform 共享业务模块
│   └── src/
│       ├── commonMain/             # 协议、状态机、投影、同步与 facade
│       └── commonTest/             # 跨平台单元测试
├── androidApp/                     # Android 原生应用（Jetpack Compose）
│   └── src/
│       ├── main/                   # 产品代码与平台实现
│       └── test/                   # JVM 单元测试（含 UI 逻辑的纯函数守卫）
├── Docs/                           # 调研、KMP 迁移与验证文档
└── Design/                         # 设计规范与展示素材
```

## 环境要求

### 通用

- Java 17
- 已启用 `dsh-plugin-mobile-gateway` 的 DeepSeek Harness
- 能够访问 Mobile Gateway 的模拟器或真机

### Android

- Android Studio 或 Android SDK Command-line Tools
- Android SDK Platform 36（`compileSdk` / `targetSdk`）
- Android 7.0（API 24）及以上设备或模拟器

## 运行 Android

1. 配置 Java 17，并确保 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指向 Android SDK。
2. 在仓库根目录构建 Debug APK：

   ```bash
   ./gradlew :androidApp:assembleDebug
   ```

3. 安装到已连接的设备或模拟器：

   ```bash
   ./gradlew :androidApp:installDebug
   ```

4. 启动 `DshMobile`，扫描 WebUI 生成的配对二维码，或手动输入配对信息。

Debug APK 位于：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

当 Android Emulator 需要访问宿主机的 `127.0.0.1:3080` 时，可以先执行：

```bash
adb reverse tcp:3080 tcp:3080
```

## 连接与配对

1. 在 DeepSeek Harness 中安装并启用 `dsh-plugin-mobile-gateway`。
2. 打开 WebUI 的“移动设备”面板，启用移动设备连接与设备鉴权。
3. 确认 WebSocket 地址能被手机或模拟器访问。
4. 生成一次性配对二维码/Token，并在 Android 客户端中完成认证。
5. 返回首页，确认 Gateway 状态已连接，然后选择工作区和会话。

常见地址：

| 场景 | 地址 |
| --- | --- |
| Android Emulator + `adb reverse` | `ws://127.0.0.1:3080/ws/mobile` |
| 同一局域网内的真机 | `ws://<HOST-LAN-IP>:3081/ws/mobile` |
| 公网部署 | `wss://<your-domain>/ws/mobile` |

> [!IMPORTANT]
> 真机不能使用 `127.0.0.1` 访问电脑。表中的 `ws://` 仅用于受信任的本地开发环境；公网或不可信网络必须使用 `wss://` 并正确配置 TLS 与设备鉴权。一次性配对 Token 不应写入日志、Issue 或聊天记录。

> [!NOTE]
> 局域网真机走的是 `dsh-plugin-mobile-gateway` 独立监听的 `3081` 端口，该端口只提供经过鉴权的 `/ws/mobile`，其余路径返回 404。`3080` 是 DSH WebUI 自身的端口且仅监听回环，真机无法通过它连接。

## 构建与测试

执行共享层测试、Android 单元测试、Lint 和 APK 构建：

```bash
./gradlew \
  :shared:allTests \
  :androidApp:testDebugUnitTest \
  :androidApp:lintDebug \
  :androidApp:assembleDebug
```

UI 逻辑（滚动数学、页头槽位、字号与文案、投影分组等）都以**纯函数**形式放在
`androidApp/src/test/`，因此上面这条命令就能覆盖，不需要设备。

> 设备测试（`androidTest/`）已移除：它们要连真实设备、单次全量约 12 分钟，
> 且实测耗时受宿主机负载影响可达 9 倍波动。原有用例的**关键不变量**已改写为
> JVM 单测（例如页头重叠缺陷 → `DshPageHeaderSlotWidthTest`）；
> 其余用例仍在 git 历史中，需要时可恢复。

## 相关文档

- [架构说明](ARCHITECTURE.md)
- [移动端设计规范](Design/design-spec.md)
- [任务详情页 UI 稿与优化方案](Docs/v1.9.7-conversation-detail-plan.md)
- [许可证](LICENSE)

以下文档记录历史规划，阶段性状态以本文档和当前代码为准：

- [v1.9.0 规划：连接体验 / 导航一致性 / 文案统一](Docs/v1.9.0-plan.md)
- [v1.8.3 规划](Docs/v1.8.3-plan.md)

## 当前状态

项目处于持续开发阶段。KMP 共享层与 Android 原生应用均已落地，协议一致性和移动端体验仍会随 DeepSeek Harness 与 Mobile Gateway 持续演进。

本项目是面向 DeepSeek Harness 的社区客户端，不代表 DeepSeek 官方发布。

[![Listed on dsh-plugin.org](https://dsh-plugin.org/badges/listed.svg)](https://dsh-plugin.org/plugins/clarklevis1995/dsh-plugin-mobile-gateway)
