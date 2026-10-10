package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.shared.protocol.GatewayQuestion
import com.clarklevis.dsh.shared.protocol.GatewayQuestionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提问卡片的数据契约：**选项必须能被解析出来**。
 *
 * 用户报告「没有选择」，最可能的静默失败是参数解析不出 options ——
 * `parseQuestions` 解析失败时返回空列表，卡片随后渲染成「无可选项」的样子，
 * 而不是报错。这类失败不会在编译期暴露，因此这里把真实 wire 形态钉住。
 */
class AskQuestionParsingTest {

    @Test
    fun parsesOptionsFromTheHarnessParameterShape() {
        val args = """
            {"questions":[{"id":"q1","question":"用哪个方案？","header":"选择",
            "options":[{"label":"方案甲","description":"改动小"},{"label":"方案乙 (Recommended)","description":"更稳"}]}]}
        """.trimIndent()

        val questions = parseQuestions(args)

        assertEquals(1, questions.size)
        val question = questions.single()
        assertEquals("q1", question.id)
        assertEquals("用哪个方案？", question.question)
        assertEquals("选择", question.header)
        // 关键：两个选项都必须解析出来，否则卡片就是「没有选择」。
        assertEquals(2, question.options?.size)
        assertEquals(listOf("方案甲", "方案乙 (Recommended)"), question.options?.map { it.label })
        assertEquals("改动小", question.options?.get(0)?.description)
    }

    /** `multi_select` 是 harness 的 snake_case 写法；漏解析会让多选题退化成单选。 */
    @Test
    fun parsesSnakeCaseMultiSelect() {
        val args = """{"questions":[{"id":"q1","question":"选哪些？","multi_select":true,
            "options":[{"label":"A"},{"label":"B"}]}]}"""

        val question = parseQuestions(args).single()

        assertTrue("multi_select 必须解析为多选", question.allowsMultipleSelections)
    }

    /** `multiSelect` 驼峰写法同样要认（网关归一化后会统一成这个）。 */
    @Test
    fun parsesCamelCaseMultiSelect() {
        val args = """{"questions":[{"id":"q1","question":"选哪些？","multiSelect":true,
            "options":[{"label":"A"}]}]}"""
        assertTrue(parseQuestions(args).single().allowsMultipleSelections)
    }

    /** 纯字符串选项数组（`["A","B"]`）也要支持，避免只认对象形态。 */
    @Test
    fun parsesPlainStringOptions() {
        val args = """{"questions":[{"id":"q1","question":"选？","options":["通过","需要修复"]}]}"""

        val question = parseQuestions(args).single()

        assertEquals(listOf("通过", "需要修复"), question.options?.map { it.label })
    }

    /**
     * 提问**可以没有选项**（harness schema 的 `options` 是可选的）。
     * 这种情况下卡片必须退化成纯文本输入，而不是渲染一个空白列表。
     */
    @Test
    fun questionWithoutOptionsIsLegitimate() {
        val args = """{"questions":[{"id":"q1","question":"你的项目叫什么？"}]}"""

        val question = parseQuestions(args).single()

        assertTrue("无选项是合法形态", question.options.isNullOrEmpty())
    }

    /** `prompt` / `text` 是历史别名，不能因为没有 `question` 字段就丢掉整题。 */
    @Test
    fun parsesLegacyPromptAndTextAliases() {
        assertEquals("选？", parseQuestions("""{"questions":[{"id":"q1","prompt":"选？"}]}""").single().question)
        assertEquals("选？", parseQuestions("""{"questions":[{"id":"q1","text":"选？"}]}""").single().question)
    }

    /** 参数完全无法解析时返回空列表（不产生可交互卡片），且不抛异常。 */
    @Test
    fun malformedArgumentsYieldNoQuestionsInsteadOfThrowing() {
        assertTrue(parseQuestions("not json").isEmpty())
        assertTrue(parseQuestions("{}").isEmpty())
        assertTrue(parseQuestions("""{"questions":"nope"}""").isEmpty())
    }

    /**
     * 「推荐」后缀要从标题里摘出来，但**选中值必须仍是 label 原文**——
     * 协议按 label 精确匹配，归一化后提交会被网关拒绝。
     */
    @Test
    fun recommendedSuffixIsStrippedForDisplayButSelectionUsesTheRawLabel() {
        val raw = "方案乙 (Recommended)"

        val metadata = optionMetadata(raw)

        assertEquals("方案乙", metadata.title)
        assertTrue(metadata.isRecommended)
        // 关键：原始 label 保留，提交时用的是它。
        assertEquals(raw, GatewayQuestionOption(raw).label)
    }

    /** 中文括号与中文「推荐」同样识别。 */
    @Test
    fun recommendedSuffixVariantsAreRecognised() {
        listOf("方案乙（推荐）", "方案乙 (推荐)", "方案乙（recommended）").forEach { label ->
            assertTrue("应识别：$label", optionMetadata(label).isRecommended)
        }
    }

    /** 以「（推荐）」结尾但其实是正文的情况不应误判——只有后缀才摘。 */
    @Test
    fun labelContainingButNotEndingWithRecommendedKeepsFullTitle() {
        assertEquals("推荐方案", optionMetadata("推荐方案").title)
        assertFalse(optionMetadata("推荐方案").isRecommended)
    }

    // ---- 关联：提问帧与时间线里的 tool/call 必须能对上 ----

    @Test
    fun matchesQuestionCallByQuestionIdSet() {
        val requestQuestions = listOf(GatewayQuestion(id = "q1", question = "选？"))
        val callArguments = """{"questions":[{"id":"q1","question":"选？"}]}"""

        assertTrue(matchesQuestionCall(requestQuestions, callArguments))
    }

    /** id 缺失时回落按题干文本匹配。 */
    @Test
    fun matchesQuestionCallFallsBackToQuestionText() {
        assertTrue(
            matchesQuestionCall(
                listOf(GatewayQuestion(id = "", question = "用哪个方案？")),
                """{"questions":[{"question":"用哪个方案？"}]}"""
            )
        )
    }

    @Test
    fun unrelatedCallDoesNotMatch() {
        assertFalse(
            matchesQuestionCall(
                listOf(GatewayQuestion(id = "q1", question = "选？")),
                """{"questions":[{"id":"zzz","question":"完全不同"}]}"""
            )
        )
    }

    // ---- 字号：卡片不得自成一套更大的层级 ----

    /**
     * 提问卡片曾经用 20sp 题干 / 16sp 选项，**比页面标题（18sp）还大**，
     * 用户反馈「字太大」。这里把「不得超过页面标题」这条约束钉住，
     * 否则下次改版很容易又把它放大回去。
     */
    @Test
    fun questionCardTypographyStaysWithinTheAppScale() {
        assertTrue(
            "题干不得大于页面标题",
            QuestionTitleFontSize.value <= DshPageTitleFontSize.value
        )
        assertTrue(
            "题干应至少是区块标题级，避免和正文一样大而失去层级",
            QuestionTitleFontSize.value >= DshSectionTitleFontSize.value
        )
        assertTrue(
            "选项标题应等于正文字号",
            QuestionBodyFontSize.value == DshBodyFontSize.value
        )
        assertTrue(
            "选项说明应等于辅助字号",
            QuestionCaptionFontSize.value == DshCaptionFontSize.value
        )
    }

    /** 两张提问卡片必须共用同一组字号，不能各写一套。 */
    @Test
    fun bothQuestionCardsShareOneTypographyScale() {
        // 通过共享 token 保证：若哪天有人把其中一张改成私有字号，这条断言会失败。
        assertTrue(QuestionTitleFontSize.value == DshSectionTitleFontSize.value)
        assertTrue(QuestionBodyFontSize.value == DshBodyFontSize.value)
        assertTrue(QuestionCaptionFontSize.value == DshCaptionFontSize.value)
    }
}
