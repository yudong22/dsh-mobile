package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecentSessionsTest {
    @Test
    fun `orders by most recent activity`() {
        val recent = homeRecentSessions(
            listOf(
                session("older", at = 100.0),
                session("newest", at = 300.0),
                session("middle", at = 200.0)
            )
        )

        assertEquals(listOf("newest", "middle", "older"), recent.map(SessionSummary::id))
    }

    @Test
    fun `re-sorts because the cache restore path does not guarantee order`() {
        // 缓存恢复出来的顺序是落盘时的顺序，不保证与 lastActivity 一致。
        val recent = homeRecentSessions(
            listOf(session("stale", at = 10.0), session("fresh", at = 900.0))
        )

        assertEquals(listOf("fresh", "stale"), recent.map { it.id })
    }

    @Test
    fun `is fed the project scoped list so the current project filter cannot be bypassed`() {
        // 首页的输入必须是 workspaceScopedSessions 的结果：这里断言这条链路的语义，
        // 而不是在 homeRecentSessions 里再实现一遍项目过滤。
        val sessions = listOf(
            session("inProject", at = 300.0),
            session("otherProject", at = 400.0),
            session("loose", at = 500.0)
        )
        val workspaces = listOf(
            workspace("w1", "inProject"),
            workspace("w2", "otherProject")
        )

        val scoped = workspaceScopedSessions(sessions, workspaces, "w1")

        assertEquals(listOf("inProject"), homeRecentSessions(scoped).map { it.id })
        assertEquals(
            listOf("loose"),
            homeRecentSessions(
                workspaceScopedSessions(
                    sessions,
                    workspaces,
                    AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID
                )
            ).map { it.id }
        )
        // 没有任何项目时，「当前项目」退化成全部会话。
        assertEquals(
            listOf("loose", "otherProject", "inProject"),
            homeRecentSessions(workspaceScopedSessions(sessions, emptyList(), null)).map { it.id }
        )
    }

    @Test
    fun `hides draft sessions that have no conversation yet`() {
        val draft = session("blank", at = 500.0).copy(hasConversation = false)
        val real = session("real", at = 100.0)
        val workspaces = listOf(workspace("w1", "blank", "real"))

        assertEquals(
            listOf("real"),
            homeRecentSessions(workspaceScopedSessions(listOf(draft, real), workspaces, "w1"))
                .map { it.id }
        )
    }

    @Test
    fun `truncates to the default limit and keeps the newest`() {
        val sessions = (1..9).map { index -> session("s$index", at = index.toDouble()) }

        val recent = homeRecentSessions(sessions)

        assertEquals(HOME_RECENT_SESSION_LIMIT, recent.size)
        assertEquals(listOf("s9", "s8", "s7", "s6", "s5", "s4"), recent.map { it.id })
    }

    @Test
    fun `zero limit yields no rows and is not treated as unlimited`() {
        val sessions = listOf(session("a", at = 100.0))

        assertEquals(emptyList<SessionSummary>(), homeRecentSessions(sessions, limit = 0))
        // 负数是调用方 bug，退化成空列表而不是 take(-1) 的异常。
        assertEquals(emptyList<SessionSummary>(), homeRecentSessions(sessions, limit = -3))
    }

    /**
     * 「全部 N」入口的契约：**仅当确实被截断**时出现。
     *
     * 这条以前没有覆盖：`totalCount` 与展示条数是两个独立量，调用方误把展示条数当总数传进来时
     * 入口会静默消失、且不报错，所以必须把判定本身钉住。
     */
    @Test
    fun `show-all entry appears only when the list was actually truncated`() {
        assertFalse(homeRecentShowsAllEntry(totalCount = 6, shownCount = 6))
        assertFalse(homeRecentShowsAllEntry(totalCount = 0, shownCount = 0))
        assertTrue(homeRecentShowsAllEntry(totalCount = 7, shownCount = 6))
        assertTrue(homeRecentShowsAllEntry(totalCount = 40, shownCount = 6))
    }

    /** 上限与判定必须自洽：恰好等于上限时不该出现入口，多一条才出现。 */
    @Test
    fun `limit and show-all entry agree at the boundary`() {
        val exactlyAtLimit = (1..HOME_RECENT_SESSION_LIMIT).map { session("s$it", at = it.toDouble()) }
        val oneMore = exactlyAtLimit + session("extra", at = 0.0)

        val atLimit = homeRecentSessions(exactlyAtLimit)
        val overLimit = homeRecentSessions(oneMore)

        assertFalse(homeRecentShowsAllEntry(exactlyAtLimit.size, atLimit.size))
        assertTrue(homeRecentShowsAllEntry(oneMore.size, overLimit.size))
    }

    @Test
    fun `connection badge maps every phase to a user visible word`() {
        assertEquals("已连接", homeConnectionBadge(GatewayConnectionState.CONNECTED))
        assertEquals("正在连接…", homeConnectionBadge(GatewayConnectionState.CONNECTING))
        assertEquals("正在认证…", homeConnectionBadge(GatewayConnectionState.AUTHENTICATING))
        assertEquals("等待网络…", homeConnectionBadge(GatewayConnectionState.WAITING_FOR_NETWORK))
        assertEquals("连接失败", homeConnectionBadge(GatewayConnectionState.FAILED))
        assertEquals("离线", homeConnectionBadge(GatewayConnectionState.DISCONNECTED))
        assertEquals("离线", homeConnectionBadge(GatewayConnectionState.SUSPENDED))
    }

    private fun session(id: String, at: Double) =
        SessionSummary(id = id, title = id, lastActivityEpochSeconds = at, isRunning = false, hasUnread = false)

    private fun workspace(id: String, vararg sessionIds: String) =
        GatewayWorkspace(id, "/$id", id, sessionIds.toList(), "now", "now")
}
