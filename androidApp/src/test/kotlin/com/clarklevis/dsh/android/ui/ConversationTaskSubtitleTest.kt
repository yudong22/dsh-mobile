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

    // ---- 运行中：不得写「暂无任务」（用户反馈）----

    /**
     * **运行中且没有清单时，绝不能写「暂无任务」。**
     *
     * harness 的 `todos` 是「常驻计划」折叠——每轮 `turn/start` 都会清空它
     * （`packages/todo/tool-todo/src/index.ts:119-133`）。所以「列表为空」在运行中
     * 是**正常状态**，而 Agent 恰恰正在干活。写「暂无任务」会与事实相反。
     */
    @Test
    fun `running without tasks never claims there is no work`() {
        val subtitle = conversationTaskSubtitle(tasks = null, isRunning = true)

        assertEquals(RUNNING_SUBTITLE, subtitle)
        assertTrue(
            "运行中不得写「暂无任务」，那与「正在干活」矛盾：$subtitle",
            !subtitle.contains(NO_TASK_SUBTITLE)
        )
    }

    /** 空列表与 null 同义（都是「被 turn/start 清掉」），运行中一视同仁。 */
    @Test
    fun `running with an empty list also reports running`() {
        assertEquals(RUNNING_SUBTITLE, conversationTaskSubtitle(emptyList(), isRunning = true))
    }

    /** 空闲时才用「暂无任务」占位，保持页头行高稳定。 */
    @Test
    fun `idle without tasks keeps the placeholder`() {
        assertEquals(NO_TASK_SUBTITLE, conversationTaskSubtitle(null, isRunning = false))
        assertEquals(NO_TASK_SUBTITLE, conversationTaskSubtitle(emptyList(), isRunning = false))
    }

    // ---- 运行中：总结上一轮（用户提出的诉求）----

    /**
     * 运行中且拿得到**上一轮**清单时，总结它并标明「上一轮」。
     *
     * 用户的原话是「之前的任务也可以总结一下」：本轮刚 `turn/start` 清空了 `todos`，
     * 但上一轮做过什么仍然有价值，且数据确实存在（历史保留了每个 `todo/write` 快照）。
     */
    @Test
    fun `running summarizes the previous round when available`() {
        val previous = listOf(task("completed"), task("completed"), task("in_progress"))

        val subtitle = conversationTaskSubtitle(tasks = null, isRunning = true, recentTasks = previous)

        assertEquals("3 个任务 · 1 进行中 · 2 已完成（上一轮）", subtitle)
    }

    /** 「上一轮」是必须的限定语：否则用户会把它当成本轮的进度。 */
    @Test
    fun `previous round summary is explicitly labelled`() {
        val subtitle = conversationTaskSubtitle(
            tasks = null,
            isRunning = true,
            recentTasks = listOf(task("completed"))
        )

        assertTrue("必须标明这是上一轮，避免与本轮进度混淆：$subtitle", subtitle.contains("上一轮"))
    }

    /** 上一轮清单为空时不加「（上一轮）」，回落「运行中」。 */
    @Test
    fun `empty previous round falls back to running`() {
        assertEquals(
            RUNNING_SUBTITLE,
            conversationTaskSubtitle(tasks = null, isRunning = true, recentTasks = emptyList())
        )
    }

    /**
     * 本轮**已有**清单时直接用它，不显示「上一轮」。
     *
     * 否则同一页面上会出现两套数字（本轮清单 + 上一轮总结），用户无法判断哪个是当前进度。
     */
    @Test
    fun `current round list wins over the previous round`() {
        val subtitle = conversationTaskSubtitle(
            tasks = listOf(task("in_progress")),
            isRunning = true,
            recentTasks = listOf(task("completed"), task("completed"))
        )

        assertEquals("1 个任务 · 1 进行中", subtitle)
        assertTrue("本轮有清单时不应再出现上一轮：$subtitle", !subtitle.contains("上一轮"))
    }

    /** 空闲时即使有「上一轮」清单也不用它——那是历史，不该占据当前状态位。 */
    @Test
    fun `idle ignores the previous round list`() {
        assertEquals(
            NO_TASK_SUBTITLE,
            conversationTaskSubtitle(tasks = null, isRunning = false, recentTasks = listOf(task("completed")))
        )
    }

    /** 无论输入组合如何，副标题都非空——页头高度不应随之跳动。 */
    @Test
    fun `subtitle stays non blank for every combination`() {
        val lists = listOf<List<GatewayTask>?>(null, emptyList(), listOf(task("in_progress")), listOf(task("completed")))
        lists.forEach { current ->
            lists.forEach { previous ->
                listOf(true, false).forEach { running ->
                    val subtitle = conversationTaskSubtitle(current, running, previous)
                    assertTrue(
                        "副标题不得为空：current=$current running=$running previous=$previous",
                        subtitle.isNotBlank()
                    )
                }
            }
        }
    }
}
