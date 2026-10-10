package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.shared.facade.SharedMobileStore
import com.clarklevis.dsh.shared.projection.ConversationItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提问卡片**端到端**：真实 wire 帧 → 共享投影 → 卡片实际拿到的参数。
 *
 * 单测 `AskQuestionParsingTest` 只证明「给定参数能解析出选项」，证明不了
 * 「宿主推来的帧真的会把选项带到卡片手上」。中间隔着
 * `GatewayWireDecoder` 归一化与 `ConversationProjection` 的 `jsonDisplayText()`
 * 这两道工序，任一环节丢了 `options`，卡片就会渲染成「没有选择」——
 * 而用户看到的正是这个症状，且编译期不会有任何提示。
 */
class AskQuestionEndToEndTest {

    private fun store(): SharedMobileStore = SharedMobileStore()

    /** 会话帧：建立 session 并**选中**它，否则投影不产出对话行。 */
    private fun SharedMobileStore.openSession() {
        acceptFrame(
            """{"kind":"sessions","items":[{"sessionId":"s1","updatedAt":1800000000,"running":false,"blank":false,"cwd":"/tmp/q","agentPreset":"standard"}]}"""
        )
        selectSession("s1")
        acceptFrame(
            """{"sessionId":"s1","seq":1,"time":1800000001,"event":{"type":"user/message","source":"user","text":"开始"}}"""
        )
    }

    /**
     * 宿主推来一问两选项 → 时间线里必须出现 QUESTION 行，且其参数能解析出两个选项。
     *
     * 这是「没有选择」的直接回归断言。
     */
    @Test
    fun realWireFrameCarriesOptionsAllTheWayToTheCardArguments() {
        val store = store()
        store.openSession()

        val args = """{"questions":[{"id":"q1","question":"用哪个方案？","header":"选择方案",
            "options":[{"label":"方案甲","description":"改动小"},{"label":"方案乙","description":"更稳"}]}]}"""
        store.acceptFrame(
            """{"sessionId":"s1","seq":2,"time":1800000002,"event":{"type":"tool/call","callId":"c1","name":"ask_user_question","arguments":${embeddedJsonString(args)}}}"""
        )

        val questionItem = store.snapshot().conversation
            .firstOrNull { it.kind == ConversationItemKind.QUESTION }

        assertTrue("时间线里必须有 QUESTION 行", questionItem != null)
        val parsed = parseQuestions(questionItem!!.text)
        assertEquals("必须解析出 1 道题", 1, parsed.size)
        assertEquals("用哪个方案？", parsed.single().question)
        // 关键断言：两个选项都在，卡片才可能渲染出可选项。
        assertEquals(
            listOf("方案甲", "方案乙"),
            parsed.single().options?.map { it.label }
        )
    }

    /** 待答请求帧本身也必须带上 options（卡片另一条取数路径）。 */
    @Test
    fun pendingRequestFrameExposesOptions() {
        val store = store()
        store.openSession()

        store.acceptFrame(
            """{"kind":"question-requested","rpcId":"r1","sessionId":"s1","questions":[{"id":"q1","question":"选？","options":[{"label":"A"},{"label":"B"}]}]}"""
        )

        val request = store.snapshot().pendingQuestions.single()
        assertEquals(listOf("A", "B"), request.questions.single().options?.map { it.label })
    }

    /**
     * 归一化后的参数仍然可解析：投影会对参数做 pretty-print
     * （`JsonValue.jsonDisplayText()`），若缩进后的文本解析不了，卡片同样没有选择。
     */
    @Test
    fun prettyPrintedArgumentsRemainParseable() {
        val store = store()
        store.openSession()
        store.acceptFrame(
            """{"sessionId":"s1","seq":2,"time":1800000002,"event":{"type":"tool/call","callId":"c1","name":"ask_user_question","arguments":{"questions":[{"id":"q1","question":"选？","options":[{"label":"A"},{"label":"B"}]}]}}}"""
        )

        val text = store.snapshot().conversation
            .first { it.kind == ConversationItemKind.QUESTION }.text

        // 参数确实是格式化过的 JSON（含换行/缩进），而不是原始单行。
        assertTrue("参数应已格式化为可读 JSON", text.contains("\n"))
        assertEquals(listOf("A", "B"), parseQuestions(text).single().options?.map { it.label })
    }

    /** `ask_question` 是历史别名，同样要渲染成可交互卡片而不是只读 JSON 行。 */
    @Test
    fun legacyToolNameAlsoProducesAQuestionItem() {
        val store = store()
        store.openSession()
        store.acceptFrame(
            """{"sessionId":"s1","seq":2,"time":1800000002,"event":{"type":"tool/call","callId":"c1","name":"ask_question","arguments":{"questions":[{"id":"q1","question":"选？"}]}}}"""
        )

        assertTrue(
            store.snapshot().conversation.any { it.kind == ConversationItemKind.QUESTION }
        )
    }

    /** 只读预览：没有待答请求时，卡片仍应能从参数渲染题目（历史回放）。 */
    @Test
    fun historicalQuestionWithoutPendingRequestStillParses() {
        val store = store()
        store.openSession()
        store.acceptFrame(
            """{"sessionId":"s1","seq":2,"time":1800000002,"event":{"type":"tool/call","callId":"c1","name":"ask_user_question","arguments":{"questions":[{"id":"q1","question":"历史题","options":[{"label":"是"}]}]}}}"""
        )

        val text = store.snapshot().conversation
            .first { it.kind == ConversationItemKind.QUESTION }.text

        assertTrue(store.snapshot().pendingQuestions.isEmpty())
        assertEquals(listOf("是"), parseQuestions(text).single().options?.map { it.label })
    }

    /**
     * 把 JSON 嵌成外层 JSON 的**字符串**字段（供应商常以字符串承载 arguments）。
     *
     * 必须保留外层引号并转义内部引号；漏掉外层引号会得到一个语法非法的帧，
     * 于是测的是「解析失败」而不是目标行为（这个坑实测踩过一次）。
     */
    private fun embeddedJsonString(raw: String): String = buildString {
        append('"')
        raw.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

    /**
     * 题干以 `text` 承载时，选项**同样必须**到达卡片（本次修复的回归点）。
     *
     * 此前 `parseQuestions` 不认 `text`，于是同一份参数「网关认得、卡片认不得」，
     * 卡片会退化成「无法解析这道提问的参数」——既没有选项，也没有提交/跳过，
     * 正是用户反馈的「字太大、没有选择、没有后续操作」里的后两项。
     */
    @Test
    fun questionCarriedInTextFieldStillYieldsOptionsToTheCard() {
        val store = store()
        store.openSession()
        store.acceptFrame(
            """{"sessionId":"s1","seq":2,"time":1800000002,"event":{"type":"tool/call","callId":"c1","name":"ask_user_question","arguments":{"questions":[{"id":"q1","text":"用哪个方案？","options":[{"label":"甲"},{"label":"乙"}]}]}}}"""
        )

        val text = store.snapshot().conversation
            .first { it.kind == ConversationItemKind.QUESTION }.text
        val parsed = parseQuestions(text)

        assertEquals(1, parsed.size)
        assertEquals("用哪个方案？", parsed.single().question)
        assertEquals(listOf("甲", "乙"), parsed.single().options?.map { it.label })
    }

    /**
     * 题干用 `text` 时，提问帧与时间线 tool/call 也必须能关联上。
     *
     * `matchesQuestionCall` 依赖 `parseQuestions`；解析不出题目就关联不上，
     * 内联卡片会落到 `request == null` 分支，用户便无法作答。
     */
    @Test
    fun textFieldQuestionStillMatchesItsPendingRequest() {
        val callArguments = """{"questions":[{"id":"q1","text":"用哪个方案？"}]}"""

        assertTrue(
            matchesQuestionCall(
                listOf(com.clarklevis.dsh.shared.protocol.GatewayQuestion(id = "q1", question = "用哪个方案？")),
                callArguments
            )
        )
    }
}
