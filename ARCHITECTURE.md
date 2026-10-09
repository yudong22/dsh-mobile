# DeepSeek Harness Mobile 架构

> **iOS 客户端已移除（v1.9.7）**：`DeepSeekHarnessMobile/`、`DeepSeekHarnessMobileTests/`、
> `AgentLiveActivityWidget/`、`.xcodeproj` 与其 vendored Swift 依赖 `Vendor/swift-markdown-ui`
> 已从仓库删除，`shared` 的三个 Kotlin/Native target 也已移除。
> 本文档描述**当前唯一交付平台 Android**。被删除的 iOS 实现可从 git 历史取回
> （`git show 57f351f:DeepSeekHarnessMobile/...`），其中 Swift 相关的架构说明仅在恢复 iOS 时才有意义。

## 1. 架构目标

DeepSeek Harness Mobile 是 Kotlin Multiplatform 项目：KMP `shared` 模块是业务状态与纯逻辑的唯一来源，平台层（`androidApp`）使用 Jetpack Compose，只负责原生能力与呈现。

`shared` 仍保持 KMP 结构（`commonMain` / `commonTest`），但**当前只服务 Android**。保留这层结构的原因是业务逻辑与平台能力已按此边界分离（共享层不依赖 Compose 或 Android framework），而不是因为还存在第二个平台；若将来恢复 iOS，只需重新声明 Kotlin/Native target 并补回 Swift 平台层。

Android 客户端通过 WebSocket 连接 Mobile Gateway，承载 Workspace、Session、Conversation、Trajectory、History、Human Question 和 Session Control 等能力。

## 2. 核心原则

- KMP Store 保有 SessionList、Question、SessionControl、Conversation、Trajectory 和 History 的业务状态。
- UI 向 KMP 发送 Intent；KMP 通过可取消订阅向平台发送有序 Event。
- Event 同一事务内携带增量 state patch 与一次性 effect descriptor。
- 平台层不执行平行 Reducer；只校验 Event，更新可重建 UI 镜像，并执行平台 effect。
- Event 丢序、schema 未知、Intent 不匹配或 patch/effect 语义不一致时，平台 Adapter 必须在 UI 发布和 I/O 之前 fail-closed。
- WebSocket、DataStore/Keystore、文件与图片、Compose 运行时、应用生命周期和后台任务是平台能力，不进入 `commonMain`。

## 3. 单向数据流

```text
┌───────────────────────────────────────────────────────────┐
│ Jetpack Compose                                           │
│ 读取 StateHolder 发布的可重建 UI 镜像，产生用户 Intent       │
└────────────────────────────┬──────────────────────────────┘
                             │ Intent
┌────────────────────────────▼──────────────────────────────┐
│ AndroidSharedStateHolder（@Stable，Compose 观察的镜像）    │
│ Intent 转发、Event schema/sequence/语义校验、UI 镜像发布   │
└────────────────────────────┬──────────────────────────────┘
                             │ 粗粒度 KMP 桥接
┌────────────────────────────▼──────────────────────────────┐
│ KMP shared/commonMain                                      │
│ Store + Reducer + Projection + History Sync               │
│ 唯一业务状态 → 有序增量 Event(state patch + effect)          │
└────────────────────────────┬──────────────────────────────┘
                             │ effect descriptor
┌────────────────────────────▼──────────────────────────────┐
│ Android 平台 effect 执行器                                 │
│ OkHttp / DataStore / Keystore / Files / Images / Service  │
└────────────────────────────┬──────────────────────────────┘
                             │ WebSocket / 平台 API
                         Mobile Gateway
```

`AndroidSharedStateHolder` 中的 Compose state 是 KMP 状态的平台观察镜像，不是第二份业务状态源。它可以从 KMP 初始 snapshot 和后续 Event 重建，也不得由 UI 业务逻辑直接改写。

## 4. 模块边界

### 4.1 `shared/commonMain`

- `protocol/`：Gateway DTO、JSON 平台无关表示和 wire decoder。
- `domain/`：SessionList、Question、SessionControl Reducer。
- `projection/`：Conversation 和 Trajectory 唯一投影逻辑。
- `sync/`：History 状态机、分页合并与 live tail 去重。
- `facade/`：粗粒度 Store、Intent 入口、MVI Event 订阅与结构化错误边界。

KMP 不直接执行网络、磁盘、Keychain 或任何 UI framework I/O。

平台存储契约见 `shared/.../platform/GatewayPlatformContracts.kt`：transport、网络监控、偏好、
凭据、附件缓存、**会话正文缓存（`GatewayConversationCache`）** 与时钟。磁盘读写由其平台实现负责，
合并 / 水位 / 去重规则仍留在 KMP。

### 4.2 Android 平台层（`androidApp`）

- `AndroidSharedStateHolder.kt`：面向 Compose 的平台 State Holder；转发 Intent、发布 KMP change，协调平台 effect。
- `AndroidGatewayProjection.kt`：KMP 投影到平台镜像的适配层；校验 Event 原子性并保持 fail-closed。
- `AndroidProjectionActor.kt`：投影变更、结果发布与 UI 清理共用的有序提交边界（单 Mutex + 批处理）。
- `GatewayRuntime.kt`(shared) + OkHttp：WebSocket 连接、重连与平台发送。
- `ui/`：Compose 产品界面（导航、主题 token、各页面）。
- DataStore / Keystore、图片与附件缓存、前台服务：平台服务。

### 4.3 UI 与设计系统

Compose UI 仅读取 StateHolder 状态并调用语义化 Intent。Conversation 和 Trajectory 的高频更新经投影节流（32ms / 100ms）批处理，不对每个 token 复制完整列表。

Android 端视觉体系为**浅色卡片 + 深色等价推导**两套，统一由 `ui/DshTheme.kt` 的
`DshPalette`（`dshPalette()` / `LocalDshPalette`）提供语义 token（画布、卡片、次级面、
描边、分隔线、三级文字、accent、主操作色、选中行底色、禁用主色、浮层阴影）。页面与内嵌卡片一律从该 token 取色，
MaterialTheme.colorScheme 由同一份 palette 映射，禁止在浅色背景上硬编码白色文字；
`if (isSystemInDarkTheme())` 只允许用于与有效配色无关的场景——凡需判断明暗（状态栏、
毛玻璃面板）必须用 `dshPalette().isDark`，否则「界面」显式设浅色而系统为深色时会分叉。

**几何也必须走 token**：页头高度/圆钮直径/槽位宽、卡片圆角、屏幕内边距、FAB 尺寸与避让高度
都在 `DshTheme.kt` 里集中定义。页面只传语义，不自己写尺寸——否则同一个控件在不同页面会
出现几像素的差异（历史上出现过页头圆钮 40dp/46dp 并存、以及两个圆钮因槽位宽写死而重叠溢出）。
页头左右槽位宽度由 `dshPageHeaderSlotWidth(按钮数)` 同源推导，保证标题整屏居中。

Android 顶层结构：底部四标签（任务 / 项目 / 定时任务 / 我的）为主导航
（`ui/DshBottomNavigation.kt` 的 `DshTab`）；抽屉（`ui/WorkspaceDrawer.kt`）承载品牌、设备下拉、新建任务、
`任务 (n)` / `项目 (n)` 区块与账户卡，**仅在任务详情页可唤起**；点击首页顶部标题区域弹出「任务运行设置」面板
（`ui/DshRuntimeSettingsSheet.kt`），在设备（Gateway 主机）/ 工作空间 / 权限模式之间切换，
均由既有 StateHolder Intent 驱动，不引入新的业务状态源。

导航守卫约定（易错点）：`ModalBottomSheet` 渲染在独立的 dialog 窗口中，因此面板内的
返回键处理必须写在 sheet 的 content **内部**，并同时设置
`ModalBottomSheetProperties(shouldDismissOnBackPress = false)`；写在 content 之外只会
注册到 Activity 的 dispatcher，面板打开时不会被调用。`LocalDshPalette` 与
`LocalAppearanceSettings` 一样默认抛错而不是静默退回浅色。

## 5. 关键 Use Case

### 5.1 发送消息并接收 Agent 回复

1. Compose 调用 StateHolder 的发送 Intent。
2. StateHolder 让 KMP SessionList/History 状态机处理本地状态，并交由 GatewayRuntime 执行 WebSocket 发送。
3. Gateway 返回 `sent` / `event` / `assistant-stream` 帧；投影层将帧归一化为 KMP Intent。
4. KMP Conversation/Trajectory/History/SessionList Store 从唯一业务状态生成有序增量 Event。
5. 投影适配层先验证 schema、sequence、Intent 和 patch，再发布 UI change。
6. Conversation 文本 delta 与 Trajectory operation 在下一次投影批次刷新 UI（见 §6）。
7. 最终 `assistant/message` 由 KMP 替换临时流式消息，平台层不再重复执行归并算法。

### 5.2 Human Question 与 Session Control

Question request 进入 `SharedQuestionStore`，UI answer/cancel 作为 Intent 返回 KMP。KMP 校验顺序、单/多选和状态，仅在合法转移中产生一次 Gateway effect。

模型、权限、Context Usage、Stats、Agent Presets 和默认配置的 active/queued target、generation token 及迟到响应隔离由 KMP 状态机持有。低频控制面使用分片 patch，只跨桥传输改变的 session/global section。

## 6. 增量协议与性能边界

- Conversation：`insert` / `append-text(delta)` / `remove`；历史基线和乱序修正才 replace。
- Trajectory：`insert` / `remove` / `move` / `replace` / `update`，流式 update 仅携带 delta 和新 records。
- History：live 尾部使用 append/upsert，分页基线使用 replace。
- SessionControl：按 session/global section 分片 upsert/remove，无状态变化时不发 payload。
- 流式节流：KMP 无损消费每个 token，平台按显示节奏（正文 32ms、轨迹 100ms）合并快照；用户滚动期间暂停提交，fling 结束后统一落地。
- **基线替换避免 JSON 往返**：`SharedConversationStore.replaceSession` 有 JSON 与 `List<SessionEvent>`
  两个重载。Android 侧必须用列表重载——它手里本来就有事件列表，编码成 JSON 再解码回来
  实测占该调用 **92%** 的耗时，且随分页深度呈 **O(n²)** 放大（30 页实测 29.2x）。
  JSON 重载保留给跨语言边界（恢复 iOS 时使用）。
- **分页顺序不可"优化"成增量追加**：向后翻页取的是更早的事件，而 `ConversationProjector.insert`
  只追加到末尾、patch 也没有 prepend 操作。改成增量追加会把旧消息排到列表末尾。
  顺序不变量由 `SharedConversationBaselineTest` 守护。

## 7. 错误、线程与安全

- KMP facade 捕获 `Throwable` 并返回结构化错误，禁止未声明异常跨边界。
- Compose 状态写入固定在主线程；KMP Event 序列严格单调。
- 运行期坏 Event 会使对应适配层永久 fail-closed，防止 KMP/UI 状态分叉和错误 I/O。
- Gateway 端点保存在 DataStore，当前局域网协议不带鉴权；公网部署必须使用 TLS 和鉴权。
- Provider 凭据由 Harness Host 管理，App 不保存 Provider API Key。

## 8. 测试门禁

```bash
./gradlew \
  :shared:allTests \
  :androidApp:testDebugUnitTest \
  :androidApp:lintDebug \
  :androidApp:assembleDebug \
  :androidApp:assembleRelease
```

- `shared:allTests`：Reducer、Projection、History、MVI Event、增量 payload 和异常原子性。
- `androidApp:testDebugUnitTest`：Android 侧纯逻辑（滚动数学、页头几何推导、字号与文案、投影分组等）。
- **设备测试已移除**：`androidTest/` 曾需连接真实设备、单次全量约 12 分钟且受宿主机负载影响
  可达 9 倍波动。原有用例的关键不变量已下沉为 JVM 单测（如页头重叠 → `DshPageHeaderSlotWidthTest`），
  其余仍在 git 历史中。
- 真实 Gateway 人工回归：连接/重连、消息收发、History 分页、Trajectory、Question、模型/权限和前后台。

## 9. 当前边界

- 只交付 Android；`shared` 保持 KMP 结构但无 Kotlin/Native target。
- 会话列表落盘（DataStore `sessions_json`）；会话正文由平台侧 `GatewayConversationCache` 缓存，连接建立前先呈现本地内容。
- 历史分页在 Host 侧仍是**每页 2 次往返**（`session/follow` 取 cursor + `session/page` 取数据）；
  设计权衡见网关仓库 `docs/history-paging-host-roundtrip-design.md`，尚未实施。
- 「后台任务 / 子代理」在协议层不存在；任务面板的数据源是 `todo_write` 的 `taskSnapshot` 投影。
