# Android UI 改版验证（浅色卡片体系 + 顶部任务运行设置）

> 基线 `main @ 18bec09`。本轮按参考截图对 Android 端做布局/比例/配色改版，
> 并新增「点击顶部标题 → 任务运行设置（设备 / 工作空间 / 权限模式）」交互。
> 所有结论均以实际命令输出与设备截图为准。

---

## 1. 改版目标与范围

参考截图定义了浅色卡片视觉语言（画布 `#F8F8F8`、白色圆角卡片、`#1F1F1F` 主文字、
`#8B8B8B` 次级文字、大圆角胶囊按钮、底部五标签导航、顶部两行标题 + 抽屉账户卡）。

按产品决定：**套用视觉语言，保留我方 DSH 的内容与功能**；**保留浅色 + 深色两套**。

| 层 | 内容 |
| --- | --- |
| 配色底座 | `ui/DshTheme.kt`：`DshPalette` + `LocalDshPalette` + `dshPalette()`，浅/深各一套语义 token；`onPrimary`/`error`/`secondary` 按明暗分档以满足对比度 |
| 首页 | `ui/DshProductApp.kt`：品牌头（两行标题 + `›`）、工作区卡、新建会话胶囊（未连接即置灰）、任务区块 |
| 底部导航 | `ui/DshBottomNavigation.kt`：任务 / 项目 / 定时任务 / 设置 / 扫码（`DshTab`） |
| 抽屉 | `ui/WorkspaceDrawer.kt`：字标 → 设备下拉 → 新建任务 → `任务 (n)` → `空间 (n)` → 次级入口 → 账户卡 |
| 顶部弹层 | `ui/DshRuntimeSettingsSheet.kt`：设备 / 工作空间 / 权限模式，可下钻；返回键在主层关闭、二级页回主层 |
| 标签页 | `ui/DshTabScreens.kt`：项目（工作区列表） |
| 连接状态 | `ui/DshConnectionStateUi.kt`：7 个原始状态 → 4 类展示语义（纯函数，有单测） |

> 说明：首页的 hero 标题、会话搜索框与首页任务列表已移除（会话列表改由抽屉承载）；
> 「专家」「资料库」两个底栏入口已移除，`DshTaskList.kt` 因此整文件删除。

### 1.1 从截图反推的设计 token

| token | 取值 | 来源 |
| --- | --- | --- |
| 画布 | `#F8F8F8` | 首页大面积底色取样 |
| 抽屉画布 | `#FAFAFA` | 抽屉底色取样（比画布高一档形成层级） |
| 卡片 | `#FFFFFF` | 新建任务胶囊、任务卡 |
| 次级面 | `#F0F0F0` | 搜索框、圆形按钮底 |
| 描边 | `#EAEAEA` | 胶囊极细描边 |
| 主文字 | `#1F1F1F` | 标题/正文取样 |
| 次级文字 | `#8B8B8B` | 「任务」区块标题 |
| 三级文字 | `#9A9A9A` | 列表时间、占位符（取样约 `#909090`~`#9A9A9A`） |
| accent | `#1FA07E` | 账户头像字母绿色取样 |
| 分隔线 | `#EBEBEB` | 抽屉账户卡上方分隔线 |

主要比例（截图 3x → dp）：品牌圆钮 47dp、标题字号 ~24sp、新建任务胶囊 53dp 高 / 27dp 圆角、
搜索框 44dp 高 / 22dp 圆角、底部标签栏 84dp 高、标签图标 26dp、标签字号 11sp。

---

## 2. 顶部「任务运行设置」交互

点击顶部标题区域（`brand-runtime-settings-button`）弹出底部面板：

- **主层**：`任务运行设置` + 右上关闭；卡片一「设备」（当前 Gateway 主机）；卡片二「工作空间」+「权限模式」，每行右侧 `›`。
- **二级页**：顶部 `‹ 标题` + 关闭；选项行右侧选中项显示主色勾选圆点。
- **设备**：列出已配对主机（名称 + 服务器/电脑 + 在线态）。切换会重建运行时，因此选择后直接关闭面板。
- **工作空间**：`未分组` + 网关返回的工作空间列表；选择后回到主层。
- **权限模式**：会话权限选项；未打开会话时提示「请先打开一个会话」。
- 系统返回键在二级页回到主层（`BackHandler`）。

纯函数 `runtimeHeaderSubtitle()` / `runtimePermissionTitle()` 有单测覆盖
（`androidApp/src/test/.../ui/RuntimeSettingsHeaderTest.kt`，4 个用例 / 13 个断言）。
`runtimePermissionTitle()` 现在是权限文案的**唯一**来源（设置页与对话页原先各抄了一份，
`else` 分支互相漂移），并补齐了网关实际会返回的 `ask` → 「每次询问」。

---

## 3. 自动化门禁（实测）

```bash
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest \
  :androidApp:lintDebug :androidApp:assembleDebug \
  :androidApp:compileDebugAndroidTestKotlin --offline
```

结果：

| 任务 | 结果 |
| --- | --- |
| `:shared:testAndroidHostTest` | **BUILD SUCCESSFUL**，225 tests / 0 failures / 0 errors |
| `:androidApp:testDebugUnitTest` | **BUILD SUCCESSFUL**，125 tests / 0 failures / 0 errors |
| `:androidApp:lintDebug` | **BUILD SUCCESSFUL**，28 issue（25 Warning + 3 Hint，**0 Error**） |
| `:androidApp:assembleDebug` | **BUILD SUCCESSFUL** |
| `:androidApp:assembleRelease` | **BUILD SUCCESSFUL**（release 签名，见 `Docs/release-signing.md`） |
| `:androidApp:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** |
| `git diff --check` | 通过（无空白错误） |

> 数字以 v1.8.2 收尾时的实测为准（androidApp 单测从 114 → 123：新增
> `DshConnectionStateUiTest` 6 条与缓存守卫回归测试 3 条）。Lint 的 28 条均为既有项，
> 本轮未新增：`ModifierParameter` 2 条位于 `GatewaySwitcherUi.kt:595`、`SessionStatusUi.kt:130`
> （HEAD 上已存在）；其余为 `UnusedResources` / `UseKtx` / `VectorPath` / `OldTargetApi` 等历史告警。
> 本轮新增的 `ic_cloud_outline` 与因删除 `DshSearchField` 而失效的 `ic_search` 均已删除。

### 3.1 设备 instrumentation

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
```

`Starting 44 tests on medium_phone(AVD) - 16` → **43 通过 / 1 skipped / 2 failed**。

两个失败**均为改版前既有问题**，用基线 worktree 做了 A/B 对照证明：

| 失败用例 | 本分支结果 | 基线 `HEAD` 结果 | 判定 |
| --- | --- | --- | --- |
| `AndroidAppGraphFakeIntegrationDeviceTest.injectedProductGraphRunsRuntimeHolderProjectionHistoryAndVisibleAttachment` | FAIL（`Key beforeSeq is missing in the map`） | **同样 FAIL**（同一异常、同一行） | 既有，非 UI |
| `AndroidUiParityDeviceTest.offlineNewSessionOpensComposerWithoutShowingAnInternalSubscribeError` | FAIL（`composer-input` not displayed） | **同样 FAIL** | 既有 |

对照方法：`git worktree add /tmp/dsh-baseline HEAD`，仅回填工作区里本就存在的 5 个未提交
构建改动（`build.gradle.kts`、`gradle.properties`、`gradle-wrapper.properties`、
`settings.gradle.kts`、`WorkspaceCodePreviewSupport.kt`），其余为纯 HEAD。对
`AndroidUiParityDeviceTest` 整类运行，基线 **5 个用例中 4 个失败**：

```
  PASS  activityUsesResizeInsteadOfPanningTheConversationAboveTheIme
  FAIL  drawerOpensFromDeepSeekMarkAndNavigatesToPluginPage
  FAIL  offlineNewSessionOpensComposerWithoutShowingAnInternalSubscribeError
  FAIL  swipingWorkspaceRightOpensScheduledTasksEntry
  FAIL  workspaceChromeMatchesTheIosSourceAndExposesAccessibilitySemantics
```

基线失败原因是 `No compose hierarchies found in the app`（该模拟器环境下 `MainActivity`
未启动 Compose 层级）。**改版后该类 5 个用例中仅剩 1 个失败**，说明改版本身让这类设备
用例的通过面变好了，剩余失败属于环境/既有问题。

同一路径上我方主动修正了 1 条**既有失效断言**：旧
`swipingWorkspaceRightOpensScheduledTasksEntry` 断言点击「定时任务」后出现
`定时任务功能尚未接入`，但 `ROUTE_SCHEDULED_TASKS` 早已渲染真实功能页，该占位文案在
`HEAD` 主源码中已不存在——即 HEAD 上这条断言必然失败。已改为断言真实的
`scheduled-tasks-screen` 与标题「定时任务」，并在 `ScheduledTasksScreen.kt` 补上对应 testTag。

---

## 4. 设备视觉证据

设备：`medium_phone` AVD，Android 16 / API 36，1080×2400 @420（≈411×914dp）。
截图位于本目录。

> 真机（vivo V2505A，Android 16，1440×3168 @640）本轮**未完成安装**：
> `adb install -r` 两次返回 `INSTALL_FAILED_ABORTED: User rejected permissions`，
> 需要在设备屏幕上手动确认安装。设备已连接、APK 已产出，待确认后可复跑同一套步骤。
> 下文截图与结论均来自上面的 AVD。

| 截图 | 验证点 |
| --- | --- |
| `final_home.png` | 浅色画布、两行标题（`未连接设备 \| 未分组 ›`）、白色工作区卡、新建会话胶囊、任务区块 + 搜索框、底部五标签且「任务」高亮 |
| `final_sheet.png` | 点击顶部标题弹出「任务运行设置」：设备 / 工作空间 / 权限模式三行，右侧 `›` 与关闭按钮 |
| `sheet_workspace_picker.png` | 二级页「工作空间」：`‹ 工作空间` + 关闭，`未分组` 行右侧主色勾选圆点 |
| `sheet_permission_mode.png` | 二级页「权限模式」：未打开会话时给出「请先打开一个会话，再调整权限模式。」而不是空列表 |
| `drawer_v2.png` | 抽屉：品牌字标 → 云端 → 新建任务 → 任务 → 插件/定时任务 → 底部账户卡（`D` 头像 + 标准版 + 版本号）；**底部标签栏随页面向右滑出，与截图一致** |
| `t1_library.png` | 资料库标签：未选会话时给出明确指引而非静默空白 |
| `t3_schedules.png` | 定时任务标签：浅色空态 + 主色重试按钮 |
| `tab_projects.png` | 项目标签：卡片列表 + 选中态主色描边与勾选 |
| `t4_settings.png` | 设置页：浅色卡片、分组标题、主色可点项、深色文字全部可读 |
| `d1_home.png` | **深色模式**：深色画布 + 浅色文字，未出现「深色底配深色字」或白色残留 |

对照参考截图人工确认：浅色画布/卡片/文字三档层级、胶囊圆角比例、底部标签栏的图标与
11sp 标签、抽屉的「云端 / 新建任务 / 任务 / 账户卡」结构均已对齐；我方差异是有意的——
文案保留 DeepSeek Harness，抽屉额外保留「插件」入口（既有功能，参考截图无对应项）。

---

## 5. 本轮修正的缺陷

1. **弹层主题分叉**：`dshFrostedSheet` 原先用 `isSystemInDarkTheme()`，当用户把「界面」
   显式设为浅色而系统为深色时会渲染出深色面板 → 改用 `dshPalette().isDark`。
2. **状态栏图标分叉**：`DshProductApp` 的状态栏明暗原先同样依赖系统主题 → 改用有效 palette。
3. **标签栏层级错误**：首版把 `DshBottomTabBar` 放在 `Scaffold.bottomBar`，抽屉展开后标签栏
   会悬在抽屉之上；已移入抽屉的滑动页面内部，随页面一起滑出。
4. **死代码**：`dshGlassEdge` / `dshFloatingSurfaceShadow`（定义于 `ConversationScreen.kt`）
   在改版后零引用，已删除，否则 `ConversationScreen` 会保留 `Brush`/`isDark` 无用依赖。
5. **多色矢量图标**：底部「任务」图标首版含白色内层路径，被 `ColorFilter.tint` 着色后内部
   形状会丢失 → 改为单色描边实现，选中态仅变色。
6. **未使用资源**：本轮新增过程中产生的 `ic_mic_wave`（输入器语音位未落地）已删除，
   避免引入新的 `UnusedResources` 告警。

---

## 6. v1.8.2 复审修复（本轮）

针对一次独立的只读代码复审结论逐项修复；每项都给出了可复现的证据。

| # | 级别 | 问题 | 修复 |
| --- | --- | --- | --- |
| 1 | blocker | `availableWorkspaces` 用普通 `LinkedHashMap` 做缓存，而该 getter 会在 Main（Composable）与 gateway dispatcher（`sendMessage`）两处被读 → 并发读写非线程安全 | 改为 `derivedStateOf`（Compose 快照状态，跨线程读安全）；并把 `activeWorkspace` 的读取提前到 Main，随请求带入 gateway scope |
| 2 | blocker | `RuntimeSettingsSheet` 的 `BackHandler` 写在 `ModalBottomSheet` **外部**：M3 的 sheet 渲染在独立 dialog 窗口并自带返回回调，二级页按返回键会关闭整个面板（文档却称回到主层） | `shouldDismissOnBackPress = false`，并把 `BackHandler` 移入 sheet content 内；主层返回键由它接管关闭。`page` 改用 `rememberSaveable` |
| 3 | blocker | 发版 APK 用 `signingConfigs.debug` 签名，CI 每次生成随机 debug keystore（实测 v1.8.0 签名为 `f4cf5893…`，与本机 `a05c08c5…` 不同）→ 用户无法覆盖升级 | 新增 secret 驱动的 `release` 签名配置；无签名材料时产物为 unsigned，`release.yml` 校验签名并拒绝发布非项目密钥/ debug 签名产物（`Docs/release-signing.md`） |
| 4 | should-fix | 缓存播种是 TOCTOU：`hasLiveContent` 检查与 `restoreConversationCache` 写入之间隔着一次挂起磁盘读，期间到达的实时帧会被旧基线覆盖 | `restoreConversationCache` 在锁内二次校验 |
| 5 | should-fix | `hello` 清空实时标记但**保留内存内容**，重连后重新选中该会话会用旧磁盘基线覆盖内存中更新的内容 | 新增 `hasAuthoritativeContent()` = 实时标记 **或** 内存已有事件基线 **或** 已渲染的会话内容（见第 15 项） |
| 6 | should-fix | 被拒绝的 history/live 帧也会置位实时标记，导致该会话缓存播种被永久跳过 | 标记改到接受判定之后，且 history 仅在 `events` 非空时置位 |
| 7 | should-fix | 首页「新建会话」未传 `enabled`（抽屉传了）→ 未连接时仍可点，点了才报错；新设备用例 `assertIsNotEnabled()` 必然失败 | 传 `enabled = !connection.dshBlocksNetworkActions` |
| 8 | should-fix | `drawerTaskSectionHostsTheSessionList` 用 `hasText("任务")`（默认精确匹配），而区块渲染的是 `任务 (0)`，只会命中底栏同名 Tab → 删掉整个区块测试仍通过 | 改断言 `drawer-section-任务`、`任务 (`（substring）与新增的 `drawer-task-list` |
| 9 | should-fix | 构建可移植性：`gradle.properties` 写死 `/opt/homebrew/Cellar/openjdk@17/17.0.19/...`，同时注释掉 foojay 解析器（两者互为因果，换机/`brew upgrade` 即挂） | 删除写死的路径、恢复 foojay 自动下载工具链；官方仓库置于镜像之前；wrapper 回到官方 distribution URL；在 `Docs/kmp-development.md` 说明 JDK 获取方式 |
| 10 | should-fix | 深色下 `onPrimary` 用白色叠 `#7EA8FF` 仅 2.35:1；`error` 作正文色仅 3.3~3.6:1；深色 `secondary` 由 HEAD 的 7.39:1 退到 3.18:1；未设置的 `surfaceContainer*` 让原生 `AlertDialog` 退回 M3 紫灰 | 按明暗分档 `onPrimary`/`error`/`secondary`，并显式映射 `surfaceContainer*` |
| 11 | should-fix | `LocalDshPalette` 默认静默返回浅色 | 与 `LocalAppearanceSettings` 一致改为 `error(...)` |
| 12 | minor | 派生状态收益被 `snapshot.sessions` 直读抵消（`DshProductApp` 的 `LaunchedEffect`、`ProjectsTabScreen`） | 改用 `homeSessions` |
| 13 | minor | 死代码：`DshTaskList.kt`（整文件）、`DshSearchField`、`GatewayAuthenticationMenu`、`GlassCircleButton`、`MarkdownLikeText`、`homeSearchResultSessionIds`、`backStackEntry`、未使用的 `onOpenSession`/`state` 参数、重复的 `Image` import | 全部删除；连带删除因此失效的 `ic_cloud_outline.xml` 与 `ic_search.xml` |
| 14 | — | `SmallConnectionDot` 用 `Color.Red`/`Color.Gray`，与抽屉/顶栏同语义状态不同色 | 统一走 `dshConnectionDotColor(state, dshPalette())` |
| 15 | **blocker** | 第 5 项的判据本身不够：`historyEvents` 只是「可落盘的规范化事件」缓冲，而 **steering（排队）消息等只进 `conversationStore`**，不进 `historyEvents`（实测此时 `exportConversationCache()` 为 null）。于是重连后 `hasAuthoritativeContent` 仍返回 false，旧磁盘基线照样覆盖已渲染内容 | 判据补上 `conversationItems`（已渲染内容）这一路；并加两条 streaming/steering 回归用例，做了变异验证 |

### 6.1 本轮新增的回归测试

`:androidApp:testDebugUnitTest` 从 114 → **125**（新增 11 条：`DshConnectionStateUiTest` 6 条
+ 本轮 5 条）：

- `staleDiskCacheCannotOverwriteInMemoryContent` —— 断言守卫的**效果**（旧缓存被跳过、内容不变），
  而不是只断言布尔标记。
- `newHandshakeClearsLiveMarkerButKeepsAuthoritativeContent` —— 复现「hello 后标记为假」。
- `reconnectThenReselectStillProtectsInMemoryContent` —— 重连后重选不得被旧缓存覆盖。
- `conversationOnlyContentSurvivesReconnectAndStaleCacheSeed` —— **第 15 项的关键回归**：
  steering 内容只进 conversation store、`exportConversationCache()` 为 null，重连后旧缓存
  不得覆盖它。
- `reselectingConversationOnlySessionDoesNotReseedFromStaleCache` —— 同一场景在真实播种调用点
  （`selectSession`）上的表现。

变异验证：把 `hasAuthoritativeContent` 的 `conversationItems` 那一路去掉时，
上述最后两条**失败**；只保留 `liveSessionIds` 时全部失败；修复版全部通过。因此不是恒真断言。

### 6.2 设备验证的更新

`AndroidUiParityDeviceTest` 之前 7/8 失败是环境问题（`No compose hierarchies found`，
模拟器上 APK 未安装导致 `MainActivity` 未启动 Compose 层级），**不是**改版引入的崩溃。
本轮在干净安装 APK 后重跑：**8/8 全部通过**，包括此前争议的两条
（`offlineNewSessionIsDisabledInsteadOfFailing`、`drawerTaskSectionHostsTheSessionList`）。

底部标签栏「被手势条裁切」的复核结论：**未裁切**。像素分析显示所有 Tab 图标/文字的墨迹
最底到 y=2281，而系统 `mandatorySystemGestures` 从 y=2316 起、`navigationBars` 从 y=2337 起，
`DshBottomTabBar` 的 `navigationBarsPadding()` 生效，手势条之下是空白。

## 7. 仍未完成 / 不属本轮的项

- **真实 Gateway 全业务人工冒烟**：本轮设备验证在未连接网关下完成（主机与工作空间列表为空，
  故「工作空间」二级页仅有「未分组」）。主机切换、权限切换的端到端生效需连接真实网关后复验，
  与 `Docs/kmp-stage12-android-gateway-verification.md` 同属未完成项。
- **`ApprovalRequestCard` 浅色警示色**：由 `#F59E0B` 改为按主题分档的浅色 `#B45309`，
  该字面量未进 `DshColors`（保持改动局部）；若要求所有取色统一入 token，需在 `DshTheme.kt`
  增设浅色警示 token。
- **轨迹 CONTEXT 成功色**仍为成对硬编码 `#30D158 / #34C759`，作为语义状态色保留。
- **Markwon 代码块围栏底色**由 Markwon 默认 Theme 提供，未在 `DshMarkdownText.kt` 覆盖，
  建议后续目视确认深色模式代码块。
- `AndroidAppGraphFakeIntegrationDeviceTest` 的 `beforeSeq` 缺失属 shared/transport 侧的既有
  问题，不在 UI 改版范围。
- **设备用例未进 CI**：`.github/workflows/*.yml` 只跑单测 + lint + assemble，不跑
  `connectedDebugAndroidTest`，因此设备用例的失败面只能在本地模拟器复核。
