package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 首页最近会话的投影：**已按项目过滤的会话**按最近活动时间倒序，**不截断**。
 *
 * 入参是 [workspaceScopedSessions] 的结果而不是原始会话表——项目过滤有且只有一份实现
 * （见 [workspaceScopedSessions]），首页与抽屉都必须复用它，不能各写一套。
 *
 * 排序在这里显式做：网关的 `sessions` 帧本身按 `lastActivityEpochSeconds` 倒序，
 * 但离线缓存的恢复路径不保证顺序，不重排会让冷启动后的首页顺序漂移。
 *
 * 不截断是有意的：首页列表就是这一屏的主体，条数上限交给滚动本身。
 */
internal fun homeRecentSessions(scopedSessions: List<SessionSummary>): List<SessionSummary> =
    scopedSessions.sortedByDescending(SessionSummary::lastActivityEpochSeconds)

/**
 * 首页任务列表：**整页滚动**的会话列表（不再是嵌套在首页里的独立滚动容器）。
 *
 * 首页与「任务列表」原本是两个概念：首页有品牌头 + 新建任务 + 高度受限的内嵌列表，
 * 内嵌列表自己滚动。结果是同一屏里存在两个滚动容器——品牌头固定不动、列表在中间滚，
 * 用户要先判断「我该滑哪一块」。现在合并为**一个**整页滚动的 LazyColumn：
 * 品牌头、新建任务、任务列表都是它的 item / items，一起滚出屏幕。
 *
 * 「最近活跃」标题行已去掉：整页就是任务列表本身，不再需要一个分区标题来说明这件事
 * （连接状态已由品牌头的连接点与副标题表达）。
 *
 * [allSessions] 是**已按当前项目过滤**的完整列表，本区块只排序、不截断。
 * 列表为空时按连接相位给四档文案，不留空白。
 */
internal fun LazyListScope.taskListItems(
    allSessions: List<SessionSummary>,
    connection: GatewayConnectionState,
    onOpenSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onArchiveSession: (String) -> Unit
) {
    val sessions = homeRecentSessions(allSessions)
    if (sessions.isEmpty()) {
        item(key = "home-recent-empty") {
            HomeRecentEmptyState(connection = connection)
        }
        return
    }
    // 公共「分组圆角列表」行样式（与项目页同一套，DshGroupedList.kt）：
    // 整组一个圆角容器 + 行间分隔线，行内仍是 状态点 + 标题 + 时间。
    // 长按菜单（重命名/归档）由 SessionRowActions 承载，保持不变。
    item(key = "home-session-list") {
        DshGroupedSection(label = "任务", showCount = true, count = sessions.size) {
            sessions.forEachIndexed { index, session ->
                if (index > 0) DshGroupedRowDivider()
                SessionRowActions(
                    session = session,
                    onClick = { onOpenSession(session.id) },
                    onRename = onRenameSession,
                    onArchive = onArchiveSession,
                    modifier = Modifier.testTag("home-session-${session.id}")
                ) {
                    SessionActivityDot(session = session)
                    Text(
                        text = session.title,
                        color = dshPalette().textPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = if (session.isRunning) "运行中" else relativeTime(session.lastActivityEpochSeconds),
                        color = if (session.isRunning) DshColors.Success else dshPalette().textTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.testTag("home-session-time-${session.id}"),
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
    }
}

/**
 * 行首状态点：运行中绿（与抽屉一致）、有未读用主色、其余用三级文字色。
 * 未读在首页尤其重要——它是「这条内容变了，点进去看」的唯一提示。
 */
@Composable
private fun SessionActivityDot(session: SessionSummary) {
    val palette = dshPalette()
    StatusIndicatorDot(
        color = when {
            session.isRunning -> DshColors.Success
            session.hasUnread -> palette.primary
            else -> palette.textTertiary
        },
        modifier = Modifier.size(7.dp),
        glowing = session.isRunning
    )
}

/**
 * 空态：文案取决于连接相位，而不是笼统地说「没有会话」。
 * 连接中列表确实常常为空（会话来自宿主的 `sessions` 帧），此时说「没有会话」是误导；
 * 但离线缓存播种后连接中也可能已有内容，那种情况根本不会走到这个空态。
 */
@Composable
private fun HomeRecentEmptyState(connection: GatewayConnectionState) {
    val palette = dshPalette()
    val message = when (connection.dshPhase) {
        DshConnectionPhase.IN_PROGRESS -> "正在连接…会话到达后会自动出现在这里。"
        DshConnectionPhase.ATTENTION -> "连接不可用。恢复连接后这里会显示当前项目的最近会话。"
        // 这里引用了首页按钮的文案，改名时必须一起改，否则空态会指向一个不存在的按钮。
        // 文案指向右下角 FAB「＋」（本轮 UI 统一后新建任务入口已从页头下移）。
        DshConnectionPhase.ONLINE -> "当前项目还没有任务，点右下角「＋」开始一个。"
        DshConnectionPhase.IDLE -> "连接设备后，这里会按最近活动时间列出当前项目的会话。"
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(palette.surface, DshCardCornerRadius)
            .border(1.dp, palette.cardBorder, DshCardCornerRadius)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("home-recent-empty"),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = message,
            color = palette.textTertiary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

/**
 * 标题右侧的连接短标签。
 *
 * 首页已有两处连接指示（品牌头的 `header-connection-dot` 与底栏），
 * 这里不是「补上缺失的指示」，而是让「最近活跃」区块自带上下文：会话可能来自离线缓存，
 * 光看列表分不清「这些是刚同步的」还是「这些是断网前的」。
 */
internal fun homeConnectionBadge(connection: GatewayConnectionState): String = when (connection.dshPhase) {
    DshConnectionPhase.ONLINE -> "已连接"
    DshConnectionPhase.IN_PROGRESS -> dshConnectionDetailText(connection).orEmpty()
    DshConnectionPhase.ATTENTION -> dshConnectionDetailText(connection).orEmpty()
    DshConnectionPhase.IDLE -> "离线"
}
