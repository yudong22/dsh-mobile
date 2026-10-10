package com.clarklevis.dsh.android.ui

import java.util.Locale
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown **解析层**基准（纯 JVM，毫秒级）。
 *
 * ## 为什么测这一层
 *
 * 完整的 Markdown 渲染链路是三段：
 *  1. **解析**：commonmark `Parser`（纯 JVM，本文件所测）
 *  2. **渲染**：Markwon `toMarkdown()` 返回 `Spanned`（`android.text`，只能在设备上跑）
 *  3. **缓存**：`DshMarkdownRenderCache` LRU（纯 JVM）
 *
 * 第 2 段依赖 `android.text.Spanned`，本仓库**没有 Robolectric**，且 v1.9.7 已刻意
 * 移除设备测试（`5b7267b`，理由是 13 分钟/轮不可用作门禁）。因此这里只测第 1 段——
 * 它恰好是**换解析器方案时唯一会替换的层**，也是自定义表格适配
 * （`DshTableBlockParserFactory`）所在之处。
 *
 * ## 断言的是「量级」不是「数字」
 *
 * 绝对耗时随机器与 CI 负载波动（v1.9.7 的移除记录里实测同一用例在全量跑与单跑间
 * 差 9 倍）。因此这里只断言**能区分「没问题」与「出大问题」的上界**：单次解析
 * 远小于一帧预算（16.7ms）。超时上界放宽到 200ms，避免负载抖动导致假失败。
 *
 * ## 守护的三件事
 *
 * 1. **单块解析成本**：会话里单条 assistant 消息的典型体量不应接近帧预算。
 * 2. **流式增量成本**：内容按 token 增长时，重新解析整段是 O(n²) 行为——
 *    这是本项目真实场景（每约 32ms 一个 token）。测出来的是「当前实现的增长曲线」，
 *    用于判断 `rememberStreamingMarkdownState()` 那样只重解析尾部的方案值不值得换。
 * 3. **缓存命中成本**：命中必须是常数级，否则 LazyColumn 回滚会反复付解析费。
 */
class DshMarkdownParseBenchmarkTest {

    private val parser: Parser = Parser.builder()
        // 与 buildDshMarkwon 的解析插件集保持一致：自定义表格解析器 + GFM 表格 + 删除线。
        // 刻意不含 TaskListPlugin——它来自 `io.noties.markwon:ext-tasklist` 的 AAR，
        // 引用 `android.graphics.drawable.Drawable`，在 JVM 单测里不可用；
        // 且它只影响任务列表项的解析，不影响本基准覆盖的表格/标题/代码块路径。
        .customBlockParserFactory(DshTableBlockParserFactory())
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create()
            )
        )
        .build()

    /** 典型 assistant 回复：小标题 + 段落 + 行内代码 + 表格 + 围栏代码块。 */
    private fun realisticMessage(repeat: Int = 1): String = buildString {
        repeat(repeat) { index ->
            append("### 第 ${index + 1} 节：结论\n")
            append("先说结论：这段用 `in_progress` 表示进行中，其余归入待处理。\n")
            append("| 用途 | 色值 |\n|---|---|\n| 页面背景 | `#151517` |\n| 正文 | `#F2F2F2` |\n")
            append("```kotlin\nval phase = when (state) {\n    \"in_progress\" -> Phase.ACTIVE\n    else -> Phase.PENDING\n}\n```\n")
            append("补充说明一段足够长的文字，用来模拟真实回复里那些解释性的自然语言。\n")
        }
    }

    /** 解析一次，返回节点总数（避免 JIT 把整段解析优化掉）。 */
    private fun parseOnce(markdown: String): Int = parser.parse(markdown).countNodes()

    /** 递归下降统计全部节点：`next` 只是同级兄弟链，必须同时下钻 `firstChild`。 */
    private fun Node.countNodes(): Int {
        var count = 1
        var child = this.firstChild
        while (child != null) {
            count += child.countNodes()
            child = child.next
        }
        return count
    }

    private fun medianMillis(times: Int = 7, block: () -> Unit): Double {
        // 预热：JIT 与类加载都计进首轮，会让基准严重虚高。
        repeat(3) { block() }
        val samples = (1..times).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        return samples[samples.size / 2]
    }

    private fun report(label: String, millis: Double, budgetMillis: Double) {
        println(
            String.format(
                Locale.US,
                "[md-parse] %-46s %8.3f ms  (上限 %.0f ms)",
                label, millis, budgetMillis
            )
        )
    }

    @Test
    fun `single realistic message parses far below a frame budget`() {
        val markdown = realisticMessage()
        var nodes = 0
        val millis = medianMillis { nodes = parseOnce(markdown) }
        report("单条典型回复 (${markdown.length} 字符)", millis, 200.0)

        assertTrue("解析应产出节点", nodes > 10)
        assertTrue(
            "单条消息解析耗时 ${millis}ms 超过 200ms，已明显偏离帧预算量级",
            millis < 200.0
        )
    }

    /**
     * 长会话：20 段，约 6KB。这是「打开一个长任务」时要解析的体量。
     */
    @Test
    fun `long conversation parses within budget`() {
        val markdown = realisticMessage(repeat = 20)
        val millis = medianMillis { parseOnce(markdown) }
        report("长会话 ${markdown.length} 字符", millis, 200.0)

        assertTrue(
            "长会话解析耗时 ${millis}ms 超过 200ms",
            millis < 200.0
        )
    }

    /**
     * 流式增长曲线：这是判断「要不要换自带流式增量解析的库」的依据。
     *
     * 当前实现每次拿到新全文就整段重解析（`DshLazyMarkdownText` 的 LaunchedEffect
     * 以 markdown 为 key）。若整段增长时单次成本线性上升，那么整个流式过程就是 O(n²)。
     * 这里打印 1x/4x/16x 三档，让人能直接读出斜率。
     */
    @Test
    fun `streaming growth is measurable across sizes`() {
        val sizes = listOf(5, 20, 80)
        val timings = sizes.map { repeat ->
            val markdown = realisticMessage(repeat = repeat)
            val millis = medianMillis(times = 5) { parseOnce(markdown) }
            report("流式档位 ${repeat}x (${markdown.length} 字符)", millis, 500.0)
            millis
        }
        // 只守卫「不炸」：80 段仍应在 500ms 内。增长曲线的解读留给报告，不作为门禁，
        // 因为绝对斜率依赖机器，写死断言会在 CI 上随机失败。
        assertTrue(
            "最大档位解析耗时 ${timings.last()}ms 超过 500ms",
            timings.last() < 500.0
        )
    }

    /**
     * 缓存命中：`DshMarkdownRenderCache` 存在的全部理由是「命中要便宜」。
     * 这里测同一段文本重复解析的成本，作为「命中路径」的量级参照。
     */
    @Test
    fun `repeated parse of identical source stays cheap`() {
        val markdown = realisticMessage(repeat = 5)
        val millis = medianMillis(times = 20) { parseOnce(markdown) }
        report("同文本重复解析 (缓存命中路径参照)", millis, 100.0)

        assertTrue("重复解析耗时 ${millis}ms 超过 100ms", millis < 100.0)
    }

    /**
     * 真实场景回归：模型常在**小标题后不带空行**直接给表格。
     * 这是 `DshTableBlockParserFactory` 存在的理由，基准用的语料必须覆盖它，
     * 否则测的是一条并不存在的理想路径。
     */
    @Test
    fun `benchmark corpus exercises the no-blank-line table case`() {
        val markdown = realisticMessage()
        // DshTableBlockParserFactory 的场景：表格头紧跟在文字之后，**中间没有空行**。
        // 若基准语料在表格前补了空行，就绕开了这个自定义解析器，测的是一条理想路径。
        assertTrue(
            "基准语料必须包含「文字行紧邻表格头、无空行」的场景",
            markdown.contains("归入待处理。\n| 用途 | 色值 |")
        )
        // 反向确认：表格头之前**不得**出现空行（否则覆盖不到该分支）。
        assertTrue(
            "表格头之前不应有空行，否则测不到 no-blank-line 路径",
            !markdown.contains("\n\n| 用途 |")
        )
        assertTrue("解析不应抛异常", parseOnce(markdown) > 0)
    }
}
