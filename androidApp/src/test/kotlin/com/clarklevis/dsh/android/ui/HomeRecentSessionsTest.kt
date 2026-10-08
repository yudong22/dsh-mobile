package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace
import org.junit.Assert.assertEquals
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

    /**
     * 列表**不截断**：首页就是要展示当前项目的完整会话列表，上限交给滚动本身。
     *
     * 这条以前断言的是「截断到 6 条」。截断会让稍早的任务只能开抽屉才找得到，
     * 而抽屉只是同一份数据的第二个视图，所以上限已整条移除。
     */
    @Test
    fun `keeps every session instead of truncating`() {
        val sessions = (1..9).map { index -> session("s$index", at = index.toDouble()) }

        val recent = homeRecentSessions(sessions)

        assertEquals(sessions.size, recent.size)
        assertEquals(
            listOf("s9", "s8", "s7", "s6", "s5", "s4", "s3", "s2", "s1"),
            recent.map { it.id }
        )
    }

    /** 条数远超一屏时同样不丢：排序后原样返回，与下限无关。 */
    @Test
    fun `keeps every session for lists far larger than one screen`() {
        val sessions = (1..200).map { index -> session("s$index", at = index.toDouble()) }

        val recent = homeRecentSessions(sessions)

        assertEquals(200, recent.size)
        assertEquals("s200", recent.first().id)
        assertEquals("s1", recent.last().id)
    }

    @Test
    fun `empty input yields an empty list`() {
        assertEquals(emptyList<SessionSummary>(), homeRecentSessions(emptyList()))
    }

    /**
     * 抽屉「活跃」区块只收近 24 小时有活动的任务：落在窗口内的保留、按最近活动倒序；
     * 更早的与未来时间戳都不算活跃。用固定时钟（nowMillis）避免依赖墙上时间。
     */
    @Test
    fun `active drawer list keeps only last-24h sessions in recent order`() {
        val now = 1_000_000L // 任意固定秒基，单位毫秒
        val sessions = listOf(
            session("twoHoursAgo", at = (now / 1_000) - 2 * 3_600.0),
            session("fresh", at = (now / 1_000) - 60.0),
            session("twoDaysAgo", at = (now / 1_000) - 2 * 24 * 3_600.0),
            session("twoHoursAgoToo", at = (now / 1_000) - 3 * 3_600.0)
        )

        val active = drawerActiveSessions(sessions, nowMillis = now)

        assertEquals(listOf("fresh", "twoHoursAgo", "twoHoursAgoToo"), active.map { it.id })
    }

    /** 没有任何会话落在 24 小时窗口内时，活跃区块应整段跳过（返回空列表）。 */
    @Test
    fun `active drawer list is empty when nothing happened in the last 24h`() {
        val now = 1_000_000L
        val sessions = listOf(
            session("twoDaysAgo", at = (now / 1_000) - 2 * 24 * 3_600.0),
            session("aWeekAgo", at = (now / 1_000) - 7 * 24 * 3_600.0)
        )

        assertTrue(drawerActiveSessions(sessions, nowMillis = now).isEmpty())
    }

    /** 恰好 24 小时前的活动应被排除：窗口是「近 24 小时以内」，不取等号边界之外。 */
    @Test
    fun `active drawer list excludes exactly 24h old activity`() {
        val now = 1_000_000L
        val sessions = listOf(
            session("boundary", at = (now / 1_000) - 24 * 3_600.0),
            session("inside", at = (now / 1_000) - 23 * 3_600.0)
        )

        assertEquals(listOf("inside"), drawerActiveSessions(sessions, nowMillis = now).map { it.id })
    }

    /** 草稿（尚无会话正文）不进历史，也不应出现在活跃区块。 */
    @Test
    fun `active drawer list hides draft sessions with no conversation`() {
        val now = 1_000_000L
        val draft = session("blank", at = (now / 1_000) - 60.0).copy(hasConversation = false)
        val real = session("real", at = (now / 1_000) - 60.0)

        assertEquals(
            listOf("real"),
            drawerActiveSessions(listOf(draft, real), nowMillis = now).map { it.id }
        )
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
