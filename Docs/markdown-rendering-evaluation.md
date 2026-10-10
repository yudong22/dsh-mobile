# Markdown 渲染方案评估（是否更换第三方库）

> 结论先行：**暂不更换**。解析层实测 20KB 仅 0.92ms，远不是瓶颈；
> 换库的真实代价在「正文可复制」与「紧邻表格解析」两处已有能力上，而非性能。
> 门槛是：先补一条能跑在真机上的渲染层基准，再决定。
>
> 基准：`androidApp/src/test/kotlin/com/clarklevis/dsh/android/ui/DshMarkdownParseBenchmarkTest.kt`

---

## 1. 现状

| 项 | 值 |
| --- | --- |
| 库 | Markwon `4.6.2` |
| 该版本发布 | **2021-02**（之后无新版本） |
| 仓库状态 | 未归档（`noties/Markwon`，3419 stars，最后 push 2024-04） |
| 传递依赖 | `com.atlassian.commonmark:0.13.0` |
| 同族新版 | `org.commonmark` 已到 **0.30.0** |
| 本项目适配代码 | `DshMarkdownText.kt` 509 行 + `DshTableBlockParserFactory.kt` 50 行 |

`com.atlassian.commonmark` 是**已弃用 group**，新版迁移到了 `org.commonmark`；
Markwon 4.6.2 仍锁在旧 group 的 0.13.0 上。这是本项目最明确的技术债信号。

### 1.1 详情页 UI 的第三方依赖：0

需要先纠正一个常见误解：**任务详情页的 UI 没有任何第三方组件**。
`ConversationScreen.kt`（3156 行）的第三方 import 数为 **0**，全部是
Compose + 自研（`DshPageHeader` / `Composer` / `TaskGoalPanels` / `DshMarkdownText`）。
因此「任务页 UI 重选开源方案」没有对象可换——**唯一可评估的只有 Markdown 渲染**。

---

## 2. 渲染链路的分层与可测性

```
① 解析   commonmark Parser            → 纯 JVM        ✅ 可毫秒级单测
② 渲染   Markwon.toMarkdown() → Spanned → android.text  ❌ 需设备（本仓库无 Robolectric）
③ 缓存   DshMarkdownRenderCache (LRU)  → 纯 JVM        ✅
```

第 ② 段依赖 `android.text.Spanned`，而 v1.9.7 已**刻意移除设备测试**
（`5b7267b`：13 分钟/轮，不可用作门禁）。因此本次基准只覆盖 ①③ 两段。

这不是妥协：**换解析器时唯一被替换的就是第 ① 段**，
自定义表格适配 `DshTableBlockParserFactory` 也正在这一层。

---

## 3. 实测数据（JVM，取 7 次中位数，已预热 3 轮）

| 场景 | 体量 | 耗时 |
| --- | --- | --- |
| 单条典型回复 | 250 字符 | **0.016 ms** |
| 长会话 | 5 011 字符 | **0.164 ms** |
| 流式 5x | 1 250 字符 | 0.445 ms |
| 流式 20x | 5 011 字符 | 0.528 ms |
| 流式 80x | 20 071 字符 | **0.918 ms** |
| 同文本重复解析（缓存命中参照） | 1 250 字符 | 0.058 ms |

语料是真实回复结构：小标题 + 段落 + 行内代码 + GFM 表格 + 围栏代码块，
且**表格头紧邻上一行文字、中间无空行**（见 §4.2，基准里有正反两条断言守卫该场景）。

### 3.1 读数

- 20KB 文本解析 **0.92ms**，占 60fps 一帧预算（16.7ms）的 **5.5%**。
- 4 倍体量（5KB→20KB）耗时约 1.7 倍，**次线性**——解析不是流式的瓶颈。
- 因此「Markwon 老 → 性能差」这个假设**不成立**。

> 注意：这是**解析层**数据，不含 `Spanned` 构造与 TextView 排版。
> 后者才是设备上的真实成本，本仓库当前无法在 JVM 测（§2）。

---

## 4. 候选方案

| 方案 | stars | 最近 push | 许可 | 判定 |
| --- | --- | --- | --- | --- |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | 1105 | 2026-10-07 | Apache-2.0 | **最值得评估** |
| [jeziellago/compose-markdown](https://github.com/jeziellago/compose-markdown) | 872 | 2026-10-08 | MIT | ❌ 见 §4.1 |
| [halilozercan/compose-richtext](https://github.com/halilozercan/compose-richtext) | 993 | 2026-06-08 | Apache-2.0 | 富文本编辑向，非 markdown 渲染 |
| [boswelja/compose-markdown](https://github.com/boswelja/compose-markdown) | 12 | 2026-09-07 | MIT | 过早期，不可用于生产 |

### 4.1 `jeziellago/compose-markdown` 不解决问题

它的 README 自述：**"This library wraps a `TextView`-based renderer in Compose"**。
即它和当前实现是同一架构（TextView + AndroidView），
换过去**只是把 Markwon 换个名字**，不消除 §1 的版本债，反而多一层间接。

### 4.2 唯一真正不同的候选

`multiplatform-markdown-renderer` 的优势是架构性的，不是性能：

- **原生 Compose `Text`**：`MarkdownParagraph` 走 `buildAnnotatedString` +
  `MarkdownText`，不是 `AndroidView` 包 TextView。
- **自带流式增量解析**：`rememberStreamingMarkdownState()` 只重解析不稳定尾部。
- **依赖活跃解析器**：`org.jetbrains:markdown` 0.7.9（JetBrains 维护，2026-10 仍更新），
  替换掉已弃用的 `com.atlassian.commonmark` 0.13.0。
- 已发布 `-m3` 与 `-code`（语法高亮）子模块，Android variant 齐全。

---

## 5. 为什么现在不换

三个**已实现的能力**会在换库时面临回退风险，而收益（§3 证明）不在性能：

### 5.1 正文可复制（有意的功能）

`DshMarkdownText.kt:283` 的 `setTextIsSelectable(true)` 是为「长按选中代码块局部复制」
专门加的（v1.9.7 UX-4；此前整页只有一个 16×26dp 的整条复制按钮）。

候选库虽用 Compose `Text`（理论上可包 `SelectionContainer`），
但其 issue 库中 **selection 相关 issue 数为 0** —— 即该路径**未被验证过**。
换库意味着把一个已验证能力交给未验证的实现。

### 5.2 紧邻表格解析（真实模型行为）

`DshTableBlockParserFactory` 解决的是「模型在小标题后**不生成空行**就直接给表格」。
这是真实场景（基准语料里复现了它），并且有 4 条行为契约测试
（`DshTableBlockParserFactoryTest`：前置段落保留、连续表格、对齐/转义/引用链接、引用与列表内表格）。

该文件注释已写明：**「升级 commonmark 时需同时验证此兼容适配」**——
换解析器就要针对新解析器重写这 50 行，并重新建立等价性证明。

### 5.3 已驯服的边缘行为

509 行适配里包含：行内代码圆角背景（`DshInlineCodeSpan` + 自定义绘制）、
分隔线 span、`SoftBreakAddsNewLinePlugin`（软换行视为换行）、
`TableAwareMovementMethod`、HTML 透传、按明暗主题两套 palette。
这些是踩出来的行为，换库后需要**逐条重新对齐**。

---

## 6. 建议路线

### 第一步（已在本轮完成）：解析层基准

`DshMarkdownParseBenchmarkTest`，5 条用例，毫秒级，纳入常规单测门禁：

- 单条消息 / 长会话 / 流式三档 / 重复解析 各一条
- 外加一条**语料守卫**：断言基准语料确实含「紧邻表格」场景，
  防止有人日后给语料补空行而悄悄绕开 `DshTableBlockParserFactory`

结论：**解析层不是瓶颈，不构成换库理由。**

### 第二步（下一步，需真机）：渲染层基准

要判定「换不换」，必须测第 ② 段（`Spanned` 构造 + TextView 排版）。
两种做法：

- **A（轻量）**：加 `testOptions.unitTests.isIncludeAndroidResources`
  \+ Robolectric，在 JVM 上跑 `Markwon.toMarkdown()` 并计时。
  好处是不违反「反馈速度优先」的既定决策；代价是 Robolectric 的
  `android.text` 实现与真机有差异（可作相对比较，不可作绝对结论）。
- **B（准确）**：临时加回一个受控的设备基准（单独 source set、不进默认门禁），
  用 `dumpsys gfxinfo` + 手写计时。**注意**：v1.9.7 的 plan §1.4 明确写过
  模拟器是软件渲染，掉帧数据**不可作为结论**——必须真机。

### 第三步（有数据再决定）

若第二步显示 TextView 路径确实是瓶颈（例如长会话滚动掉帧集中在 markdown 行），
再按现有对外接口替换——出口只有三个函数，替换面可控：

```
DshMarkdownText          （详情页正文 + 轨迹/工具卡片）
DshLazyMarkdownText      （LazyColumn 内的流式正文）
DshMarkdownPreloader     （后台预热）
```

届时才需要处理 §5 的三个回退风险，并同步 `DshTableBlockParserFactoryTest`
与 `ConversationDisplayGroupsTest` 的行为契约。

---

## 7. 附：本轮已交付

| 项 | 内容 |
| --- | --- |
| 副标题默认值修复 | `conversationTaskSubtitle` 无任务时返回 `暂无任务`（原先返回 `null` 导致整行空掉），返回类型由 `String?` 收紧为 `String` |
| 守卫测试 | `ConversationTaskSubtitleTest` 增补 2 条：固定兜底、任何输入都非空白 |
| 解析层基准 | `DshMarkdownParseBenchmarkTest`，5 条 |
| 文档 | 本节（换库决策依据）；`Docs/v1.9.7-conversation-detail-plan.md` UX-3 补记副标题契约变更 |
