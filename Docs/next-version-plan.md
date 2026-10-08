# Android 下一版本规划与执行：流式性能 / ask_question 交互 / 离线优先

> 基线 `main @ a7e9180`。所有结论均以 `path:line` 对照源码复核。
> **本版按产品决定只交付 Android**；`shared`（KMP）改动两端共享故照做，iOS Swift 侧仅同步必要映射。
> 状态图例：`[x]` 已完成并验证 · `[~]` 进行中 · `[ ]` 待办 · `[!]` 阻塞

---

## 0. 结论速览

| # | 问题 | 真实病因 | 本版做法 |
|---|---|---|---|
| 1 | LLM 输出时卡顿 | 快通道每 token 跑完整投影链；每 patch 一次无用 JSON 解码；每 patch O(n) 查找 + 全表复制；`LaunchedEffect` 每 token 重启；transient 轨迹 O(chunks²) | §1，6 项 |
| 2 | `ask_user_question` 无组件 | 该 tool_call 被归入**默认折叠**的「思考过程」，展开是只读 JSON；Android 还**完全绕过** `SharedQuestionStore` 校验 | §2，投影 + 内联组件 + KMP 接入 |
| 3 | 连接前看不到内容 | 会话列表缓存**已写但从不读**；对话内容零持久化 | §3，复活 `sessionsJson` + 正文缓存 |

### 必须先知道的两条硬约束

- **C-A（快通道 revision 严格连续）**：`assistant-stream` 的 `revision` 必须逐帧 `+1`，否则 `AssistantStreamState.kt:243` 直接 `fail(...)` 并**触发重订阅**。故**不能**像 legacy 通道那样合并帧后丢弃中间帧；合批只能放在**下游**（chunk → MVI patch → 投影 → 发布）。
  > 这修正了上一版「让 `assistant-stream` 复用 `PendingStreamingFrame` 合并 `chunk.text`」的提法——那样会 revision 断档。
- **C-B（envelope 永久 fail-closed）**：`MviEnvelopeValidator` 一旦 reject，对该 domain **永久**停摆，且 `reset()` 不重建 validator（`AndroidGatewayProjection.kt:526-529`、`:408-412`）。新增 kind / 灌缓存都必须先通过 `require` 校验。

### 两条环境事实（已实测）

- 构建需 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`；`compileSdk 36` 由 AGP 自动下载。
- 工作区 4 个未提交改动中，`GatewayWireDecoder.kt` **含语法错误**，导致 Android 完全无法编译（见 §4.1，已修）。

---

## 1. 问题一：流式卡顿

### 1.1 已经做对的部分（不要重写）

- 入站 decode 在后台 dispatcher（`AndroidAppGraph.kt:43-44`），只有发布回 `Dispatchers.Main.immediate`。
- legacy 通道有真合批：32 token / 50ms（`AndroidProjectionActor.kt:51-88`，常量 `:260-263`）。
- 发布侧 32ms 显示节奏合并（`AndroidSharedStateHolder.kt:1946-1968`，常量 `:2304-2305`）。
- 滚动中暂停提交 + 80ms 恢复窗口（`ConversationScreen.kt:717-741`）。
- Markdown LRU `Spanned` 缓存 + 单线程后台解析 + ≤320 字符分块 + 可视区预取（`DshMarkdownText.kt:82-140`）。
- KMP 投影是真增量（`ConversationProjection.kt:419-490`），非每 token 全量重建。

### 1.2 已确认根因与修复

| # | 根因 | 证据 | 修复 | 状态 |
|---|---|---|---|---|
| A1 | **快通道不走合批**：`assistant-stream` 恒走 `acceptFrame`，其 `mutate()` 每 token 跑完整投影链 | `AndroidSharedStateHolder.kt:403-412` → `AndroidProjectionActor.kt:37-45` | 在**下游**合批：逐帧 accept 保 revision 连续，32 条/50ms 或结构性帧时统一提交 | **[x]** |
| A2 | 每 patch 解 `effectsJson` **仅为断言为空**，而该字段恒为 `"[]"` | `AndroidGatewayProjection.kt:540`；`SharedConversationStore.kt:159-176` | 改字符串比较（同文件 `:257` 已有此写法） | **[x]** |
| A3 | `append-text` 每操作 O(n) `indexOfFirst` + 整表 `toMutableList()`；`insert` 的 `items.none{}` 亦 O(n) | `AndroidGatewayProjection.kt:575,581,586,596,602` | 维护 `id→index` 索引，定位降为 O(1) | **[x]** |
| A4 | `LaunchedEffect(listState, timelineEntries, markdownPreloader)` 以每 token 新建的 `timelineEntries` 为 key → 每 token 取消/重启 `snapshotFlow` | `ConversationScreen.kt:743`；实例源 `:694,:698-700` | key 改稳定标量，内部用 `rememberUpdatedState` | **[x]** |
| A5 | `transientTrajectoryNodes` 每次新建 projector 并重放全部 chunks；再全量编码 → 整轮 O(tokens²) | `AssistantStreamState.kt:72-92,94`；调用点 `AndroidGatewayProjection.kt:338` | 保留长期 projector，只 fold 新增 chunk | **[x]** |
| A6 | `conversationItems.clear()/putAll()` 每 patch 复制整张 map | `AndroidGatewayProjection.kt:531-534` | 按受影响 session 增量更新 | [ ] 低优先（O(#sessions)） |

> 量级更正：A6 的 `clear/putAll` 是 O(#sessions) 的**条目引用**复制，不是 O(#items) 深拷贝；真正每 token O(items) 的是 A3。

### 1.3 验收门禁

- `:shared:allTests`、`:androidApp:testDebugUnitTest`、`:androidApp:lintDebug`、`:androidApp:assembleDebug` 全绿。
- 新增单测：chunk 批量合并后 fold 结果与逐帧 fold 一致；`effectsJson != "[]"` 仍被拒；索引维护与线性查找结果一致（insert/append/replace/remove 交错）。
- 手工：长会话（≥300 行）持续流式输出滚动流畅，无丢字/重复，logcat 不含「实时流 revision 断档」。

---

## 2. 问题二：`ask_user_question` 的会话内组件

### 2.1 权威协议规格（已逐行复核 `deepseek-harness` 源码）

**工具名**：`ask_user_question`（`packages/interaction/user-questions/src/projection.ts:28`，常量 `ASK_USER_QUESTION_TOOL`）。

**模型入参**（`projection.ts:44-49`，**snake_case**）：
```json
{"questions":[{"id":"…","question":"…","header":"…",
               "options":[{"label":"…","description":"…"}],
               "multi_select":true}]}
```
移动端 `GatewayQuestion` 用 **`multiSelect`**（camelCase）→ **需要别名归一化**。

**是否可回答取决于 `request/header`**（`projection.ts:249-259`）：仅当 header 的 tools 里 `ask_user_question` schema 声明了 `timeout`（`TIMED_WAIT_PARAMETER`，`:36`）时，其后调用才可被继续回答。**用阻塞式 legacy schema 调用的永不进跟踪**（`:32-35` 注释）。

**状态机**（`applyUserQuestionEvent`，`projection.ts:262-311`）：

| 事件 | 转移 |
|---|---|
| `request/header` | 决定 `timed` 开关 |
| `tool/call`（timed） | → `active`，state=`open`（`:255-269`） |
| `tool/result` 带 `{"pending":true}` 或 `error.code == TOOL_OUTCOME_UNKNOWN` | → **`continued`**（仍可答，`:271-280`） |
| `tool/result` 含 `{"answers":[…]}` 且无 error | → `settled` |
| `tool/result` 失败 | 从 `active` 移除（丢弃） |
| `tool/ptc-dispatch`（pending） | → `active`，state=`continued` |
| `user/message` 且 `source.kind == "user-question-reply"` | 按 `callId` settle（`:301-307`） |

**回传通道**（`index.ts:165-202`）：`answer(agent, callId, answer)` 带 `@Remote`，内部实为 **steer 一条 user message**，`source.kind = 'user-question-reply'`；**严格校验** answers 必须恰好覆盖每个 question 一次（`:178-183`），否则 `BAD_ANSWER`。注意该 API 处理的是 **`continued`**（已超时仍可答）的问题（`:161` 注释）；窗口内（`open`）走另一条 waterfall（`:272-281`）。

### 2.2 现状事实

- 网关把提问投射为**独立顶层帧** `{kind:'question-requested', rpcId, sessionId, questions, replay?}`（`dsh-plugin-mobile-gateway@0.8.1` `lib/index.mjs:2296-2302`），**不带 `callId`**（审批帧带：`:2304-2310`）。
- 它**同时**以普通 `tool/call{callId,name,arguments}` 存在于时间线；两条通路互不知道。
- Android 现有 `HumanQuestionCard.kt` 挂在 composer 位（`ConversationScreen.kt:543-556`），答完即消失，时间线无痕迹。
- 时间线把该 tool_call 降级为 `ConversationItemKind.TOOL` → `ConversationProcessGroup` → **默认折叠**（`ConversationDisplayGroups.kt:266-286`；`ProcessDisclosure:2343` `expanded=false`），展开是只读 JSON。
- **Android 完全绕过 `SharedQuestionStore`**：`AndroidSharedStateHolder.kt:1416-1420` 直连 `GatewayRuntime.answerQuestion`，无 KMP 校验与单次 effect gating；`SharedMobileStore.kt:577` 的 `makeQuestionStore()` 零调用点。
- 答案通道本身通（`question-answer` RPC；插件已校验 `lib/index.mjs:2548-2570`）。

### 2.3 实施方案（按批次）

**第一批 — 纯渲染 + 状态投影（不依赖任何回传通道）** `[x]` 已完成

| 动作 | 文件 |
|---|---|
| `ConversationItemKind` 增 `QUESTION`；`tool/call` 与 `tool-call-delta` 分支识别 `name in setOf("ask_user_question","ask_question")` | `ConversationProjection.kt:13,211-216,271-275` |
| **同一次提交**更新 Swift 映射与 Android 分组，否则 C-B 停摆 | `KMPSharedAdapter.swift:35-49`、`ConversationDisplayGroups.kt` |
| 让提问行**移出**折叠组，单独成行 | `ConversationDisplayGroups.kt:266-286` |
| `ToolActivitySummaryFormatter` 加提问分支 | `ToolActivitySummary.kt:27-36` |
| 新建 Android 内联组件（复用 `HumanQuestionCard.kt` 的选项行/多选/自定义输入） | 新增 `ui/AskQuestionToolCard.kt` |

展示态：`待作答`（可交互）/`提交中`/`已作答`（只读）/`已跳过`/`历史回放`。
**硬约束**：整批提交，answers 的 id/顺序须与 questions 一致，`selected` 须来自 `options[].label`（`QuestionReducer.kt:125-144`）。
**不要预设「已超时」**：`QuestionReducer.kt:22-27` 只有 Idle/Submitting/Accepted/Rejected。

**第二批 — `continued` 回传：已复核为「不需要」** ✅

通过直接检查已安装的 `dsh-plugin-mobile-gateway@0.8.1` 源码，§2.4 的两个阻塞问题已全部定论，
且结论是**不需要新增任何回传通道**：

- 插件用 `ctx.on('user-questions/request', …, { prepend: true })` **整体接管**该 waterfall
  （`lib/index.mjs:3114-3144`），并用 `pending.resolve(answer)` 把移动端答案**直接返回给
  `ask()` 的 Promise**（`lib/index.mjs:2391-2400`）。legacy / timed 两种 schema 走同一入口，
  因此移动端 `question-answer` RPC **对两种模式都有效**，`ask_user_question` 的返回值
  由移动端答案决定。
- `admitMessage` 只读取 `text` / `images` / `mode`（`lib/index.mjs:508-553`），
  **从不读取 `source`**，所以「steer 一条带 `source.kind = user-question-reply` 的消息」这条
  harness 内部路径**无法经移动端复用**，也**不再需要**。
- 而 `request/header` 虽被实时转发（`buildWireEvent` 的 `default` 分支只保留 `{type}`，
  `lib/index.mjs:407-408`），但**事件体被裁掉**，移动端拿不到 `header.tools`；
  不过由于上一条，判定 `timed` 对本通道**不再是前提**。

**第三批 — 不需要 Typert Remote** ✅

原计划把「窗口内（`open`）提问」列为需新增 Remote 子系统的独立里程碑。经上述复核，
`open` 与 `continued` 在移动端都通过同一个 `question-answer` RPC 解决
（`respondToQuestion` → `completeQuestion` → `pending.resolve`），**无需 Remote 通道**。

**第二轮补齐（本轮）** ✅

| 动作 | 位置 |
|---|---|
| `ToolActivitySummary` 增加 `ask_user_question` / `ask_question` 文案（「提问」+ 题目摘要 + 多题计数） | `ToolActivitySummary.kt` |
| Android 作答改走 KMP 校验：新增 `submitQuestionAnswer` / `submitQuestionCancel`（含 effect 至多一次守卫），非法批次本地即拒、不再产生必然失败的往返 | `SharedMobileStore.kt`、`AndroidGatewayProjection.kt`、`AndroidProjectionActor.kt`、`AndroidSharedStateHolder.kt` |

### 2.4 协议结论（已定论，无需再问上游）

| 原问题 | 结论 | 依据 |
|---|---|---|
| 事件流是否含 `request/header`？ | 报文只留 `{type}`，**拿不到 header.tools** | `lib/index.mjs:407-408` |
| 移动端 message 能否带 `source.kind`？ | **不能**（`admitMessage` 不读 `source`） | `lib/index.mjs:508-553` |
| 是否因此需要 Typert Remote？ | **不需要**：插件已整体接管 waterfall，`question-answer` 对 legacy/timed 均有效 | `lib/index.mjs:3114-3144`、`:2391-2400` |
| legacy `question-requested` 是否仍 emit？ | **是**，且是移动端唯一入口 | `lib/index.mjs:2296-2302` |

### 2.5 验收门禁

- 时间线出现可交互提问卡片，单选/多选/自定义/提交/跳过均可用。
- 断线重连、`replay:true` 重放、历史回放三种场景状态正确不重复。
- 已作答后行仍在，显示所选答案。
- 新增 kind 后 iOS **不**因未知 kind 停摆。
- `ConversationDisplayGroupsTest`、新增组件单测、`SharedQuestionStoreTest` 通过。

---

## 3. 问题三：连接前的缓存与离线可读

### 3.1 现状事实

| 内容 | Android 现状 | 证据 |
|---|---|---|
| 会话列表持久化 | 通道存在（DataStore key）但**从不写入真实数据**：`sessionsJson` 只写不读 | `AndroidGatewayPreferences.kt:36,44`；唯一 `update()` 调用点只写 `selectedWorkspaceId`（`AndroidSharedStateHolder.kt:2130`） |
| 连接前渲染会话列表 | ❌ 无（列表来自 `snapshot.sessions`，冷启动为空） | `DshProductApp.kt:277-287,372` |
| 对话内容持久化 | ❌ 零持久化（仅凭据/偏好/附件三类落盘） | — |
| 热断开 | ✅ 不丢数据：`disconnected()` 只清临时 chunk | `AndroidGatewayProjection.kt:136-141`；`reset()` 仅测试可达 |
| 选中会话 / 归档集合 | ❌ 不落盘 | — |

**「冷启动什么都没有」是唯一缺口**；断线重连已能工作。

### 3.2 可复用的既有机遇

- `SharedHistoryStore.installSnapshot`（`:212-229`）已是**原子基线 + replace patch + 无 effect**，Android 已在 `session-snapshot` 路径使用（`AndroidGatewayProjection.kt:242`）——正是所需入口，零新算法。
- `mergeHistoryPage`（`:314-322`）已实现「按 seq 合并、live 胜出」。
- `SharedSessionListStore.restore(snapshotJson)` 已存在（iOS 在用）。

### 3.3 实施方案

**分片 1 — 会话列表缓存接线（独立可交付）** `[x]` 已完成

- `sessions` 更新时把 `sessionsJson` 写入 DataStore（复用现有 key，无需新契约）。
- 冷启动/激活 profile 时读回作为初始列表（`AndroidMultiGatewayStore.kt:197` 一带已 `load()`，接上即可）。
- 离线时照常渲染 + 离线标识。

**已决策**：会话列表采用**全量替换**语义——缓存仅作冷启动种子，连接后以宿主 `sessions` 帧为准，宿主已删除的会话直接消失。**不改** `SessionListReducer.kt:110` 及其注释。唯一注意：冷启动到首帧之间存在瞬时窗口（缓存会话未被宿主确认），UI 不应在此窗口显示「暂无已知会话」。

**分片 2 — 对话正文缓存** `[x]` 已完成

- 新增平台契约 `GatewayConversationCache`（与 `GatewayPreferences:91`、`GatewayAttachmentCache:108` 并列，放 `GatewayPlatformContracts.kt`）；**不要**塞进 `sessionsJson`。
- Android 实现：`cacheDir/gateway-conversation/<gatewayId>/<sha256(sessionId)>.json`，原子写；复用 `AndroidAttachmentCache` 的原子写/限额范式。
- 内容：规范化后的 `[SessionEvent]` 基线（用现有 `installSnapshot` 恢复）。
- **写入时机**：只在历史终态（completed/failed）与重连成功时落盘 + ~2s 尾部防抖。**绝不每 token 写盘**（否则把 §1 收益还回去）。
- **失效**：按体积+条数限额，**不用短 TTL**（一周后打开看到空会话比看到略旧更糟）；正确性失效用显式触发（会话删除、宿主变更、`historyFormatVersion` 变化）。
- Schema 版本放 payload 内（对齐 `SharedHistoryBootstrap.schema`）；不匹配视为 cache miss 直接丢弃（派生缓存，不值得写迁移）。

**分片 3 — 避免 C-B 陷阱** `[x]` 已实现（水位取 min，走 installSnapshot 正经顺序）

灌缓存时必须先正确种下水位，否则永久停摆：

- `historyLastSequences`（`:55`）**不得高于**恢复事件尾 seq，否则 `:485` 的 `require(replacementTail >= previousTail)` 失败；
- `conversationLastSequences`（`:61`）同理（`:565,:573`）；
- MVI envelope：首事件必须是 `kind=="snapshot"`（`:662`），后续 `sequence == lastSequence + 1`（`:663`）→ 恢复流程须走「先 snapshot、再事件」正规顺序。

### 3.4 验收门禁

- 飞行模式冷启动：首页显示缓存会话列表；打开会话显示缓存正文；无报错；有离线+等待同步标识。
- 恢复网络后自动刷新，内容不重复不回退。
- 多网关不串号；删除主机清缓存；限额淘汰生效。
- 灌缓存后**不出现** `history-adapter-failed`/`conversation-adapter-failed`。

---

## 4. 顺手修复的其它 Android 问题

| # | 问题 | 位置 | 状态 |
|---|---|---|---|
| 4.1 | `GatewayWireDecoder.kt` **语法错误**（未提交改动引入 `objectValue["type"]!` 用法错误），令 Android 完全无法编译 | `GatewayWireDecoder.kt:109` | **[x] 已修**（改 `objectValue.getValue("type")`） |
| 4.2 | 该别名路径的已知副作用：把 `{"type":"ask_question"}` 合成 `question-requested`，但 `SharedMobileStore.kt:367` 要求 `rpcId != null` → 无 rpcId 时**静默丢弃且不报错** | `GatewayWireDecoder.kt:103-116` | `[ ]` 待拿到插件真实帧样例后决定是否收窄 |
| 4.3 | `LaunchedEffect` 每 token 重启（= A4） | `ConversationScreen.kt:743` | **[x]** §1 |
| 4.4 | `transientTrajectoryNodes` O(chunks²)（= A5） | `AssistantStreamState.kt:72-92` | **[x]** §1 |
| 4.5 | 运行时事件队列仅 8 条，60 tok/s 下有反压风险 | `GatewayRuntime.kt:1178-1179` | **[x]** 已调至 256，保留 48MiB 字节兜底 |
| 4.6 | `ARCHITECTURE.md:73/:121` 过期（仍称 Android 是「Compose 验证壳」、安全持久化属 stage-12 TODO，而 12/13 早已完成） | `ARCHITECTURE.md` | **[x]** 已更新，并补记缓存契约 |

---

## 5. 执行顺序

1. **[x] P0 构建解阻**：修 `GatewayWireDecoder.kt` 语法错误 → 构建恢复。
2. **[x] P1 流式性能**：A1–A5 + 4.5 已完成；A6 判定低优先、暂不改。
3. **[x] P2 会话列表缓存**（分片 1）：导出/恢复 + DataStore 接线，冷启动可先看到列表。
4. **[x] P3 ask_question 组件**：第一批（投影 + 时间线内联可交互卡片）完成。
5. **[x] P4 对话正文缓存**（分片 2/3）：`GatewayConversationCache` 契约 + Android 文件缓存 + 选中会话时先播缓存。
6. **[x] P5 收尾**：`ARCHITECTURE.md` 已更新；Android 版本号先升至 1.7.0 / 15；UI 改版后为
   1.8.0 / 16；复审修复后为 **1.8.2 / 18**（1.8.1 未发布）。

### 本轮已验证的门禁（全绿）

```
./gradlew :shared:allTests :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug
```

- `--rerun-tasks` 强制全量重跑同样通过（78 tasks executed），非缓存假绿。
- lint 仅剩既有告警，改动文件无新增问题。
- 新增测试：chunk 增量折叠等价性、attempt 重置、会话缓存往返/损坏降级/宿主覆盖、提问行独立分组与结果回填。

### 环境备忘（本机）

- JDK：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- SDK：`ANDROID_HOME=/Users/denis/Library/Android/sdk`；`compileSdk 36` 由 AGP 自动下载补齐。

每步以 `:shared:allTests :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug` 为门禁。

---

## 6. 非目标

- 不改 iOS Swift 实现（仅同步必要枚举映射以免停摆）。
- 不重写投影架构、不引入第二套状态源、不恢复 Swift/KMP 双 Reducer。
- 不做离线发送队列、本地全文搜索、缓存加密。
- 不修改 Harness Host 侧协议；网关插件改动（补 `callId`、确认 §2.4）单独提出。

---

## 7. 本轮实施说明（与计划的差异）

实施中发现三处计划未预料的事实，均已按实际情况处理：

1. **投影必须逐帧 accept**：计划原写「让 `assistant-stream` 复用 `PendingStreamingFrame` 合并
   `chunk.text`」。实施时确认 `AssistantStreamState` 要求 `revision`/`index` 严格连续，
   丢帧会 `fail(...)` 并重订阅。因此改为**逐帧 accept、合并下游**（`acceptAssistantStreamBatch`）。
2. **`androidApp` 未应用 kotlinx.serialization 插件**：缓存 DTO 放在 androidApp 会在运行期
   抛 `Serializer ... not found`（本机测试实测）。已把 payload 移到 `shared` 的
   `SharedConversationCachePayload`，与既有 `SharedHistoryBootstrap` 同处。
3. **并发写者**：实施期间另一提交（`44a6dca` 后台通知）正在修改 `AgentNotificationSettings.kt` /
   `SettingsScreen.kt` / `DshTheme.kt`。这些文件**未由本轮改动**，其编译错误曾短暂阻塞构建，
   已由该提交自行修复。

## 8. 变更记录

### 本版（Android 版重写 + 执行）

- 范围收敛为 Android；iOS 改为对照与映射同步。保留用户补充的 §2.1 权威协议规格（已对照 `projection.ts` 逐行复核）。
- 新增 **C-A 约束**：快通道 revision 严格连续，合批只能放下游——修正上一版「复用 `PendingStreamingFrame` 合并 assistant-stream」的提法（会触发重订阅）。
- 新增 **C-B 约束**：envelope reject 永久停摆且 `reset()` 不重建 validator。
- 更正 A6 量级（`clear/putAll` 为 O(#sessions)，非 O(#items)）。
- 记录并修复构建阻塞（§4.1）。
- 补充 Android 绕过 `SharedQuestionStore` 的既有缺陷。

## 9. CI 覆盖

本仓（含上游 `Clarklevis1995/dsh-mobile`）此前**没有任何 workflow**，push 不会触发构建。
已新增 `.github/workflows/android.yml`，在 `ubuntu-latest` 上跑：

```
:shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug
```

关键取舍：

- `shared` 原先只有 **iOS 测试目标**（`iosX64Test` / `iosSimulatorArm64Test`），
  而 Kotlin/Native 的 iOS target **无法在 Linux 上编译**——这意味着 23 个 `commonTest`
  文件（含本轮的缓存、提问校验、流式折叠测试）在 CI 上完全不被执行。
  已在 `shared/build.gradle.kts` 打开 `withHostTest {}`，新增 `:shared:testAndroidHostTest`
  在 JVM 上跑同一套 `commonTest`（实测 22 个测试类 / 225 个测试，0 失败），
  因此 ubuntu runner 现在能覆盖 KMP 业务逻辑。
- 仍**未**被 CI 覆盖的部分：iOS target 自身的编译与测试（需 macOS runner），
  以及任何真机 / instrumented 测试。
- 本机 `settings.gradle.kts` / `gradle-wrapper.properties` 的国内镜像属未提交的本地改动；
  仓库中的配置只有 `mavenCentral()` + `google()`，CI 上可直接拉取依赖。

