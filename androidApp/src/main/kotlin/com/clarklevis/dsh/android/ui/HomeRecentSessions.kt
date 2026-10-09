package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 会话行的高度：`vertical = 18.dp` 是行内上下内边距，配合 16sp 标题后单行约 61dp。
 *
 * 抽成常量而不是内联，是因为它同时决定列表的「密度」与可点区域：此前 11dp 时
 * 行高约 47dp，长标题与时间挤在一起；加高后每屏条数变少，但一行更像一个可点目标。
 */
internal val HOME_SESSION_ROW_PADDING = PaddingValues(horizontal = 14.dp, vertical = 18.dp)

/**
 * 首页最近会话的投影：**已按项目过滤的会话**按最近活动时间倒序，**不截断**。
 *
 * 入参是 [workspaceScopedSessions] 的结果而不是原始会话表——项目过滤有且只有一份实现
 * （见 [workspaceScopedSessions]），首页与抽屉都必须复用它，不能各写一套。
 *
 * 排序在这里显式做：网关的 `sessions` 帧本身按 `lastActivityEpochSeconds` 倒序，
 * 但离线缓存的恢复路径不保证顺序，不重排会让冷启动后的首页顺序漂移。
 *
 * 不截断是有意的：首页列表就是这一屏的主体，条数上限交给滚动本身。此前截到 6 条时
 * 「最近活跃」覆盖不到稍早的任务，用户还得开抽屉才能找到，而抽屉只是同一份数据的第二个视图。
 */
internal fun homeRecentSessions(scopedSessions: List<SessionSummary>): List<SessionSummary> =
    scopedSessions.sortedByDescending(SessionSummary::lastActivityEpochSeconds)

/**
 * 首页的「最近活跃」区块：标题行（标题 + 连接短标签）与**完整**的会话列表。
 *
 * [allSessions] 是**已按当前项目过滤**的完整列表，本区块只排序、不再截断。
 *
 * 三种态各有明确文案，不留空白：
 *  - 已连接但当前项目没有任务 → 「当前项目还没有任务…」；
 *  - 连接中且列表为空（尚无离线缓存播种）→ 进度文案，不能说成「没有会话」；
 *  - 未连接但有缓存/历史会话 → 照常展示，标题右侧标成「离线」而不是「已连接」。
 *
 * 列表是 [LazyColumn]（会话可以有很多条）并独占 [modifier] 给出的剩余高度：条数不受限，
 * 一屏放得下多少就显示多少，其余在卡片内滚动。卡片面随视口一起撑满，让整块列表读起来
 * 是一个连续的表面，而不是浮在中间的一小条。
 *
 * 会话行复用 [SessionRowActions]，因此首页也能长按重命名 / 归档，不必先开抽屉。
 */
@Composable
internal fun HomeRecentSessions(
    allSessions: List<SessionSummary>,
    connection: GatewayConnectionState,
    onOpenSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onArchiveSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    canScrollVertically: Boolean = true
) {
    val palette = dshPalette()
    val sessions = remember(allSessions) { homeRecentSessions(allSessions) }
    Column(modifier = modifier.fillMaxWidth().testTag("home-recent-sessions")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "最近活跃",
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = homeConnectionBadge(connection),
                color = if (connection == GatewayConnectionState.CONNECTED) {
                    DshColors.Success
                } else {
                    palette.textTertiary
                },
                fontSize = 13.sp,
                modifier = Modifier.testTag("home-recent-status"),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Spacer(Modifier.height(10.dp))
        if (sessions.isEmpty()) {
            // 空态卡按内容高度收拢而不是撑满：一条提示占满整屏反而更像加载失败。
            HomeRecentEmptyState(connection = connection)
        } else {
            LazyColumn(
                // weight(1f)：吃掉标题行之后的剩余高度，让列表卡撑满底栏上方的空间。
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(palette.surface, DshCardCornerRadius)
                    .border(1.dp, palette.cardBorder, DshCardCornerRadius)
                    .clip(DshCardCornerRadius)
                    .testTag("home-session-list"),
                contentPadding = PaddingValues(bottom = 4.dp),
                // 抽屉横向拖动期间交出滚动权：否则在手势仲裁里和抽屉抢同一次拖动。
                userScrollEnabled = canScrollVertically
            ) {
                // key = session.id：列表会随最近活动时间重排（一次 turn/end 就能让某行跳顶），
                // 不设 key 时行内状态（展开的菜单 / 正在重命名的对话框）会留在原索引上，
                // 于是「正在改 A」会挂到移动过来的 B 上，确认时改错会话且丢掉刚输入的文字。
                itemsIndexed(sessions, key = { _, session -> session.id }) { index, session ->
                    SessionRowActions(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onRename = onRenameSession,
                        onArchive = onArchiveSession,
                        contentPadding = HOME_SESSION_ROW_PADDING,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("home-session-${session.id}")
                    ) {
                        SessionActivityDot(session = session)
                        Text(
                            text = session.title,
                            color = palette.textPrimary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                        Text(
                            text = if (session.isRunning) {
                                "运行中"
                            } else {
                                relativeTime(session.lastActivityEpochSeconds)
                            },
                            color = if (session.isRunning) DshColors.Success else palette.textTertiary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.testTag("home-session-time-${session.id}"),
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                    if (index != sessions.lastIndex) {
                        Box(
                            Modifier.fillMaxWidth().padding(start = 14.dp)
                                .height(1.dp)
                                .background(palette.divider)
                        )
                    }
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
        DshConnectionPhase.ONLINE -> "当前项目还没有任务，点上面的「新建任务」开始一个。"
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
