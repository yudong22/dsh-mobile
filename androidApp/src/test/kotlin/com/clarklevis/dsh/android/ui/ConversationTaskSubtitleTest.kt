package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.shared.protocol.GatewayTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页头副标题（会话任务进度）。
 *
 * 这里的断言同时是**用词守卫**：副标题只描述 `taskSnapshot` 这份待办清单，
 * 而它常被误读成「后台任务」。协议里没有后台任务/子代理这个概念
 * （见 `Docs/v1.9.7-conversation-detail-plan.md` §2.4），
 * 所以文案里出现「后台」二字就应当让测试失败。
 */
class ConversationTaskSubtitleTest {

    private fun task(status: String) = GatewayTask(content = "任务", status = status)

    /**
     * 无任务时给**固定文案**，不是 null。
     *
     * 这条是「副标题有时候没了」的回归守卫：null 会让副标题整行空掉，
     * 而它命中两种情况——网关未声明 tasks 能力、以及会话还没有待办。
     */
    @Test
    fun `no tasks falls back to a fixed subtitle`() {
        assertEquals(NO_TASK_SUBTITLE, conversationTaskSubtitle(null))
        assertEquals(NO_TASK_SUBTITLE, conversationTaskSubtitle(emptyList()))
    }

    /** 无论有没有任务，副标题都非空且非空白——页头高度不应随之跳动。 */
    @Test
    fun `subtitle is never blank`() {
        val samples = listOf(null, emptyList(), listOf(task("in_progress")), listOf(task("completed")))
        samples.forEach { tasks ->
            assertTrue(
                "副标题不得为空（会导致页头空掉一行）：tasks=$tasks",
                conversationTaskSubtitle(tasks).isNotBlank()
            )
        }
    }

    @Test
    fun `counts running and pending work`() {
        val subtitle = conversationTaskSubtitle(
            listOf(task("in_progress"), task("pending"), task("pending"))
        )
        assertEquals("3 个任务 · 1 进行中 · 2 待处理", subtitle)
    }

    @Test
    fun `lists active before pending before completed`() {
        val subtitle = conversationTaskSubtitle(
            listOf(task("completed"), task("pending"), task("in_progress"))
        )
        assertEquals("3 个任务 · 1 进行中 · 1 待处理 · 1 已完成", subtitle)
    }

    @Test
    fun `all completed still reports the total`() {
        val subtitle = conversationTaskSubtitle(listOf(task("completed"), task("completed")))
        assertEquals("2 个任务 · 2 已完成", subtitle)
    }

    /** 未知状态归入「待处理」，与 TaskGoalUi 的既有口径一致（不能凭空多出一类）。 */
    @Test
    fun `unknown status counts as pending`() {
        val subtitle = conversationTaskSubtitle(listOf(task("something-else")))
        assertEquals("1 个任务 · 1 待处理", subtitle)
    }

    /**
     * 用词守卫：文案**不得**出现「后台」。
     * 一旦有人把它改成「3 个后台任务」，这条会立刻失败——因为那会承诺一个
     * 协议里并不存在的功能。
     */
    @Test
    fun `subtitle never claims background tasks`() {
        val samples = listOf(
            listOf(task("in_progress")),
            listOf(task("pending"), task("pending")),
            listOf(task("completed")),
            listOf(task("in_progress"), task("pending"), task("completed"))
        )
        samples.forEach { tasks ->
            val subtitle = conversationTaskSubtitle(tasks)
            assertTrue(
                "副标题不应声称「后台任务」（协议里没有该概念）：$subtitle",
                !subtitle.contains("后台")
            )
        }
    }
}
