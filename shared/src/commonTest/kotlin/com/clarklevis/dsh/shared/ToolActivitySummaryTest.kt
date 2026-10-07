package com.clarklevis.dsh.shared

import com.clarklevis.dsh.shared.projection.ToolActivitySummaryFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolActivitySummaryTest {
    @Test
    fun fileWriteShowsPathAndContentLineCountWithoutInventingDiff() {
        val summary = ToolActivitySummaryFormatter.summarize("write", """{"file_path":"app/src/Main.kt","content":"one\ntwo\n"}""")
        assertEquals("写入", summary.label)
        assertEquals("app/src/Main.kt", summary.detail)
        assertEquals("2 行", summary.annotation)
    }

    @Test
    fun commandAndSearchSummariesExposeUsefulArguments() {
        assertEquals("./gradlew assembleDebug", ToolActivitySummaryFormatter.summarize(
            "bash", """{"command":"./gradlew\nassembleDebug"}"""
        ).detail)
        assertEquals("class Main · src", ToolActivitySummaryFormatter.summarize(
            "grep", """{"pattern":"class Main","path":"src"}"""
        ).detail)
    }

    @Test
    fun descriptionTakesPriorityOverCommandAndFilePath() {
        val description = "Copy APK to workspace root and verify it"
        val arguments = """{"command":"cp app.apk output.apk","description":"$description"}"""
        assertEquals(description, ToolActivitySummaryFormatter.summarize("bash", arguments).detail)
        assertEquals("创建入口文件", ToolActivitySummaryFormatter.summarize(
            "write", """{"path":"Main.kt","description":"创建入口文件","content":"main()"}"""
        ).detail)
    }

    @Test
    fun blankDescriptionFallsBackAndEncodedArgumentsPreserveDescription() {
        assertEquals("pwd", ToolActivitySummaryFormatter.summarize(
            "bash", """{"command":"pwd","description":"  "}"""
        ).detail)
        val encoded = kotlinx.serialization.json.JsonPrimitive(
            """{"command":"pwd","description":"Check workspace"}"""
        ).toString()
        assertEquals("Check workspace", ToolActivitySummaryFormatter.summarize("bash", encoded).detail)
    }

    /** `ask_user_question` 必须有专用文案，否则时间线只会显示一个裸工具名。 */
    @Test
    fun askUserQuestionSummaryShowsQuestionTextAndCount() {
        val summary = ToolActivitySummaryFormatter.summarize(
            "ask_user_question",
            """{"questions":[{"id":"q1","question":"选用哪种模式？","options":[{"label":"A"}]},{"id":"q2","question":"是否继续？"}]}"""
        )
        assertEquals("提问", summary.label)
        assertEquals("选用哪种模式？", summary.detail)
        assertEquals("2 个问题", summary.annotation)
    }

    /** 别名写出与单题情况也要安全收敛。 */
    @Test
    fun askQuestionAliasAndSingleQuestionSummaryAreHandled() {
        val single = ToolActivitySummaryFormatter.summarize(
            "ask_question",
            """{"questions":[{"id":"q1","question":"确认吗？"}]}"""
        )
        assertEquals("提问", single.label)
        assertEquals("确认吗？", single.detail)
        assertEquals("", single.annotation)
    }

    @Test
    fun invalidOrUnknownToolArgumentsRemainSafe() {
        val summary = ToolActivitySummaryFormatter.summarize("custom_tool", "partial {")
        assertEquals("custom_tool", summary.label)
        assertEquals("", summary.detail)
        assertEquals("", summary.annotation)
    }
}
