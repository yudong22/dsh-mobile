package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 首页「最近活跃会话」的展示条数上限。
 *
 * 上限存在的理由不是性能（列表最多几百条），而是**版面比例**：当前项目卡 + 新建会话已占掉
 * 首屏上半部，再把整个项目的会话铺满会把「最近活跃」淹没成长列表。超出部分由标题行的
 * 「全部 N」入口承担——它打开抽屉（同一条 `workspaceScopedSessions` 过滤结果，含完整长按菜单），
 * 因此这里截断不会让任何会话变得不可达。
 */
internal const val HOME_RECENT_SESSION_LIMIT = 6

/**
 * 首页最近会话的投影：**已按项目过滤的会话**里，按最近活动时间倒序取前 [limit] 条。
 *
 * 入参是 [workspaceScopedSessions] 的结果而不是原始会话表——项目过滤有且只有一份实现
 * （见 [workspaceScopedSessions]），首页与抽屉都必须复用它，不能各写一套。
 *
 * 排序在这里显式做：网关的 `sessions` 帧本身按 `lastActivityEpochSeconds` 倒序，
 * 但离线缓存的恢复路径不保证顺序，不重排会让冷启动后的首页顺序漂移。
 */
internal fun homeRecentSessions(
    scopedSessions: List<SessionSummary>,
    limit: Int = HOME_RECENT_SESSION_LIMIT
): List<SessionSummary> = scopedSessions
    .sortedByDescending(SessionSummary::lastActivityEpochSeconds)
    .take(limit.coerceAtLeast(0))

/**
 * 标题行的「全部 N」入口是否出现：**仅当确实被截断**时出现。
 *
 * 抽成纯函数是为了让这条契约可测：`totalCount` 与展示条数是两个独立量，
 * 若调用方误把展示条数当成总数传进来，入口会静默消失且不报错。
 */
internal fun homeRecentShowsAllEntry(totalCount: Int, shownCount: Int): Boolean =
    totalCount > shownCount

/**
 * 首页的「最近活跃」区块：标题行（标题 + 连接短标签 + 截断时的「全部 N」）
 * 与最多 [HOME_RECENT_SESSION_LIMIT] 条会话行。
 *
 * [allSessions] 是**已按当前项目过滤**的完整列表；本区块自行截断，所以「全部 N」用的总数
 * 与展示条数不可能由调用方传错。
 *
 * 三种态各有明确文案，不留空白：
 *  - 已连接但当前项目没有会话 → 「当前项目还没有会话…」；
 *  - 连接中且列表为空（尚无离线缓存播种）→ 进度文案，不能说成「没有会话」；
 *  - 未连接但有缓存/历史会话 → 照常展示，标题右侧标成「离线」而不是「已连接」。
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
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
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
            Spacer(Modifier.weight(1f))
            // 「全部 N」只在确实被截断时给出：没有更多内容时留一个点了没反应的入口只是噪音。
            if (homeRecentShowsAllEntry(totalCount = allSessions.size, shownCount = sessions.size)) {
                Text(
                    text = "抽屉查看全部 ${allSessions.size}",
                    color = palette.primary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onShowAll)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("home-recent-show-all"),
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (sessions.isEmpty()) {
            HomeRecentEmptyState(connection = connection)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp)),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                sessions.forEachIndexed { index, session ->
                    // 按 session.id 加 key：本区块是**非 lazy** Column，而列表会随最近活动
                    // 时间重排（一次 turn/end 就能让某行跳到顶部）。不加 key 时行内状态
                    // （展开的菜单 / 正在重命名的对话框）会留在原索引上，于是「正在改 A」
                    // 会挂到移动过来的 B 上，确认时改错会话且丢掉刚输入的文字。
                    key(session.id) {
                        SessionRowActions(
                            session = session,
                            onClick = { onOpenSession(session.id) },
                            onRename = onRenameSession,
                            onArchive = onArchiveSession,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
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
            .background(palette.surface, RoundedCornerShape(20.dp))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp))
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
 * 首页已经有两处连接指示（品牌头的 `header-connection-dot` 与项目卡的 `ConnectionDot`），
 * 这里不是「补上缺失的指示」，而是让「最近活跃」区块自带上下文：会话可能来自离线缓存，
 * 光看列表分不清「这些是刚同步的」还是「这些是断网前的」。
 */
internal fun homeConnectionBadge(connection: GatewayConnectionState): String = when (connection.dshPhase) {
    DshConnectionPhase.ONLINE -> "已连接"
    DshConnectionPhase.IN_PROGRESS -> dshConnectionDetailText(connection).orEmpty()
    DshConnectionPhase.ATTENTION -> dshConnectionDetailText(connection).orEmpty()
    DshConnectionPhase.IDLE -> "离线"
}
