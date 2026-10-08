# 修复：两项「既有失败」的真实根因与修复（Android 设备套件）

> 起因：`Docs/test-evidence/2026-10-07-ui-redesign/verification.md` 与
> `Docs/queue-steer-integration.md` 把两项设备用例失败记为「既有、非本次改动」。
> 本次不再搁置，逐项定位到真实根因并修复。
>
> 结果：`./gradlew :androidApp:connectedDebugAndroidTest` 等价的全量设备套件
> **50 项全绿（OK (50 tests)）**。

---

## 1. `AndroidAppGraphFakeIntegrationDeviceTest`：`Key beforeSeq is missing in the map`

### 1.1 真实根因（三层，逐层暴露）

**第一层：生产代码在缺格式版本时静默丢弃游标（真产品 bug）。**

`GatewayRuntime.requestHistory()` 原本为：

```kotlin
if (beforeSequence != null && historyFormatVersion == null) {
    return@serialized sendRequestLocked(GatewayRequests.history(sessionId, maxMessages = ...))
}
```

即「要翻页但拿不到格式版本」时，**悄悄退化成不带游标的请求**——那是「取最新一页」。
调用方（`AndroidGatewayProjection` → `AndroidSharedStateHolder`）以为翻到了更早的历史，
实际每次都拿回同一页：表现为「上翻加载更多」原地重复、列表不前进却反复转圈，
且没有任何可诊断的错误码。

对照真实网关 `dsh-plugin-mobile-gateway`：

- `lib/index.mjs:1206-1218` `validateHistoryCursor`：`historyFormatVersion` 不等于
  `SESSION_FORMAT_VERSION` 时直接回 `history-format-mismatch`；
- `PROTOCOL.md:706`：「分页：`hasMore` 为真时用 `beforeSeq: nextBeforeSeq,
  historyFormatVersion: 4` 请求更早一页」——**两者必须成对**；
- `PROTOCOL.md:651`：两者都是可选的「成对字段」。

所以客户端在这里应当 **fail-closed**，而不是发出语义错误的请求。

**修法**：缺版本时显式拒绝，复用 history 既有的失败路径（UI 会清分页 loading 并提示重载）：

```kotlin
rejectLocked("history", ERROR_HISTORY_CURSOR_WITHOUT_FORMAT, targetSessionId = sessionId)
return@serialized false
```

新增 shared 单测
`GatewayRuntimeIntegrationTest.historyCursorWithoutFormatVersionIsRejectedInsteadOfSilentlyRefetchingLatest`，
断言：拒绝事件带上 `reason = history-cursor-without-format`、**一条 history 请求都没发出去**、
带版本时 `beforeSeq` 与 `historyFormatVersion` 同时出现、首屏（无游标）不带任何版本字段。

**第二层：夹具不是协议真实的。**

用例的 `hello` 帧原本是 `{"kind":"hello","authenticated":true,"protocol":3}`，
**没有 `historyFormatVersion`**；而真实网关的 hello 始终带该字段
（`dsh-plugin-mobile-gateway/lib/index.mjs:3229`）。夹具省略它，就等于让客户端永远拿不到
翻页所需的格式版本——这才是「`beforeSeq` 缺失」的直接来源。

**修法**：夹具 hello 与每条 history 帧都补上 `historyFormatVersion`（取 4，与插件的
`SESSION_FORMAT_VERSION` 对齐），并新增断言：首屏请求不得带 `beforeSeq`、
翻页请求必须同时带 `beforeSeq` 与 `historyFormatVersion`。

**第三层：用例里有一条与 shared 契约矛盾的过期断言。**

修掉前两层后，用例暴露出更深的一条：它断言定稿后
`conversation.none { it.id.startsWith("stream-") }`。但这是 **97a44e5 之前的旧语义**。
自 97a44e5 起，`finalizeStream()` 会**原地 replace 并保留原 `stream-` id**：

```kotlin
val stableFinalItem = finalItem.copy(id = mutableItems[index].id)  // 保留 stream- id
// 注释原文：Keep a streamed response's identity through finalization.
// Platform renderers own their parser/source by item id; a remove+insert here
// tears down that source before its final buffered snapshot can be rendered.
```

该契约同时被 Android 端主动依赖：
`ConversationDisplayGroups.kt:126` 的注释即「最终回复会保留原来的 stream ID 以维持
LazyColumn 行稳定，因此不能只凭 ID 判断仍在流式输出」。

shared 侧早已把它钉死：

- `ProjectionAndHistoryTest.kt:42`：`assertEquals("stream-text-1-1", projector.items.single().id)`
- `SharedConversationStoreTest.kt:64-65`：定稿 `replace` 的 `itemId` / `item.id` 均为 `stream-text-1-1`

**修法**：把过期断言换成对**定稿语义**的断言——
文本等于最终全文、且 assistant 项**只有一条**（证明是原地替换而不是残留两条）。
不再断言 id 前缀。

### 1.2 影响面

第一层是**用户可见**的产品缺陷（上翻加载更多静默失效）；第二、三层是测试自身的问题。
`AndroidGatewayProjection` 传 `historyFormatVersion = assistantStream.formatVersion(sessionId)`，
只有在「宿主未宣告格式版本」这种非真实网关的夹具场景下才会为 null，因此第一层在真机上的
触发条件是「连到不发送 `historyFormatVersion` 的旧网关」——那时旧行为会让翻页静默失效，
新行为会明确报错并可重载，属于正确的降级。

---

## 2. `ConversationKeyboardDeviceTest.inputFocusMovesLatestMessageAboveImeAndTimelineTapHidesIme`

### 2.1 真实根因：环境，不是产品

用诊断用例（`adb logcat` 打印 `WindowInsets`）在两种 AVD 配置下实测：

| AVD `hw.keyboard` | `ime()` isVisible | `ime()` bottom | 最新气泡位移 |
| --- | --- | --- | --- |
| `yes`（本仓库默认镜像） | `true` | **0** | 58px（仅导航栏） |
| `no` | `true` | **883** | **878px** |

即：镜像带硬件键盘时，软键盘「可见」但**不占高度**（`ime() inset = 0`），
`imePadding()` 自然不产生位移，用例的 `top < before - 100f` 永远不成立，
以 5s 条件超时失败——这正是历史上它被误记为「产品回归」的原因。

产品本身是**正确**的：`ime=883` 时气泡上移 `878px`，与其一致。

### 2.2 修法

把「IME 必须真的占高度」这一环境前提显式化，用 JUnit assumption：
不满足时**跳过**（instrumentation status `-4`），并给出可操作的修复提示，而不是伪装成产品回归：

```kotlin
private fun assumeImeOccupiesSpace(view: View) {
    val imeBottom = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
    assumeTrue(
        "软键盘可见但不占高度（ime inset=0）：该 AVD 启用了硬件键盘，本用例无法验证 IME 位移。…",
        imeBottom > 0
    )
}
```

验证：`hw.keyboard=no` 时 2/2 通过；`hw.keyboard=yes` 时该用例 status `-4`（跳过）而
另一个用例照常通过——**不再有失败**。

---

## 3. 门禁结果（本次修复后）

| 门禁 | 结果 |
| --- | --- |
| `:shared:testAndroidHostTest` | **226 通过 / 0 失败**（+1：新增游标 fail-closed 用例） |
| `:androidApp:testDebugUnitTest` | **134 通过 / 0 失败** |
| `:androidApp:lintDebug` | 27 issue / **0 error** |
| `:androidApp:assembleDebug` / `assembleRelease` | 通过 |
| `:androidApp:compileDebugAndroidTestKotlin` | 通过 |
| 设备全量套件（instrumentation） | **50 项 OK / 0 失败**（含 2 项 skip：键盘用例在硬件键盘 AVD、模拟网关冒烟未启用） |

## 4. 复现/验证命令

```bash
# 非设备门禁
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest \
  :androidApp:lintDebug :androidApp:assembleDebug :androidApp:assembleRelease \
  :androidApp:compileDebugAndroidTestKotlin

# 设备套件（模拟器需先授予通知权限，否则首次运行的系统权限弹窗会抢焦点）
adb install -r -t androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r -t androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell pm grant com.clarklevis.dsh.android android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.clarklevis.dsh.android.test/androidx.test.runner.AndroidJUnitRunner

# 需要真实 IME 位移的键盘用例：用 hw.keyboard=no 的 AVD
```
