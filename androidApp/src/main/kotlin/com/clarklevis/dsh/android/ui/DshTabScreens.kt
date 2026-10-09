package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace

/**
 * 「项目」标签：工作区（项目）列表，支持切换与新增。
 *
 * 这是首页之外**唯一**的项目切换入口：首页的项目卡已退化为纯指示（此前它另挂了一个
 * `WorkspaceSelectionMenu` 下拉，与这里功能重复，v1.8.3 已删除）。
 *
 * [onProjectSelected] 在选中某个项目后调用，用来回到任务列表——「点项目」的意图是
 * 「切到这个项目去看它的任务」，留在项目页会让人以为没生效。**已选中的项目行也要调用**：
 * 选中本身是幂等的（`applyWorkspaceSelection` 对同值提前返回），若把导航也一起 gate 掉，
 * 那一行的点击就变成了死行。
 */
@Composable
internal fun ProjectsTabScreen(
    stateHolder: AndroidSharedStateHolder,
    onProjectSelected: () -> Unit
) {
    val palette = dshPalette()
    var showDirectoryBrowser by remember { mutableStateOf(false) }
    val workspaces = stateHolder.availableWorkspaces
    // 用派生状态读取，避免整个「项目」页因 conversation 的每 token 发布会话而重组。
    val sessions = stateHolder.homeSessions
    // 每个项目的行内详情（最新任务 / 时间 / 运行态）：按 workspaceId 建一次索引，
    // 列表滚动期间不再逐行扫描 sessions（此前 workspaceScopedSessions 每行都要过滤全表）。
    val latestByWorkspace = remember(sessions, workspaces) {
        buildWorkspaceActivityIndex(sessions, workspaces)
    }
    val pendingApprovalSessionIds = remember(stateHolder.snapshot.pendingApprovals) {
        stateHolder.snapshot.pendingApprovals.mapTo(mutableSetOf()) { it.sessionId }
    }
    // 刷新以连接相位为 key：原先是 LaunchedEffect(Unit)，若首次进入时离线，
    // 请求被状态层静默跳过，之后连接恢复也不会重跑，页面会长期停在缓存或空态。
    val connection = stateHolder.gatewayState.connection
    LaunchedEffect(connection) { stateHolder.refreshProductState(force = true) }
    // 目录浏览要读远端目录，离线时必须禁用（此前「＋」始终可点，点了才弹「请先连接」）。
    val canBrowse = !connection.dshBlocksNetworkActions
    // 统一走 DshTabScaffold：页头（设备副标题 + 配对入口）+ 正文 + 右下角 FAB。
    // 本页只提供语义：列表内容、FAB 的动作与离线禁用条件。
    DshTabScaffold(
        title = "项目",
        subtitle = stateHolder.activeGatewayDisplayName(),
        connection = connection,
        fab = DshFabSpec(
            onClick = { showDirectoryBrowser = true },
            contentDescription = "添加项目",
            testTag = "projects-fab",
            // 目录浏览要读远端目录，离线时必须禁用；禁用**原因**由下方 DshHintText 承载，
            // 不塞进无障碍标签。
            enabled = canBrowse
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("projects-list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DshGroupedSection(label = "未分组") {
                    val activity = latestByWorkspace[UNGROUPED_ACTIVITY_KEY]
                    DshGroupedRow(
                        title = "未分组",
                        tileLabel = DshTileInitials("未分组"),
                        secondLine = activity?.toSecondLine(pendingApprovalSessionIds),
                        fallbackSubtitle = "未归属到任何项目的任务",
                        timeLabel = activity?.let { relativeTime(it.lastActivityEpochSeconds) },
                        timeHighlighted = activity?.let { it.isRunning || it.sessionId in pendingApprovalSessionIds } == true,
                        selected = stateHolder.isUngroupedWorkspaceSelected,
                        // 测试直接点击该行（AndroidUiParityDeviceTest），tag 必须挂在
                        // 可点击的 Row 上而不是分组容器——容器不可点，点击会变成死区。
                        testTag = "project-card-ungrouped",
                        onClick = {
                            stateHolder.selectWorkspace(AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID)
                            onProjectSelected()
                        }
                    )
                }
            }
            if (workspaces.isNotEmpty()) {
                item {
                    DshGroupedSection(label = "项目", showCount = true, count = workspaces.size) {
                        workspaces.forEachIndexed { index, workspace ->
                            val activity = latestByWorkspace[workspace.workspaceId]
                            if (index > 0) DshGroupedRowDivider()
                            DshGroupedRow(
                                title = workspace.title,
                                tileLabel = DshTileInitials(workspace.title),
                                secondLine = activity?.toSecondLine(pendingApprovalSessionIds),
                                fallbackSubtitle = workspace.path,
                                timeLabel = activity?.let { relativeTime(it.lastActivityEpochSeconds) },
                                timeHighlighted = activity?.let { it.isRunning || it.sessionId in pendingApprovalSessionIds } == true,
                                selected = workspace.workspaceId == stateHolder.selectedWorkspaceId,
                                testTag = "project-card-${workspace.workspaceId}",
                                onClick = {
                                    stateHolder.selectWorkspace(workspace.workspaceId)
                                    onProjectSelected()
                                }
                            )
                        }
                    }
                }
            }
            // 空态必须区分「真的没有项目」和「连不上所以看不到」：
            // 离线时沿用旧的「还没有项目」会误导用户以为网关上确实没有项目
            // （对比首页空态已按 dshPhase 分四档，HomeRecentSessions.kt:206）。
            if (workspaces.isEmpty()) {
                item {
                    DshHintText(
                        if (connection.dshPhase == DshConnectionPhase.ONLINE) {
                            "还没有项目。点右下角「＋」把网关上的目录添加为项目。"
                        } else {
                            "${homeConnectionBadge(connection)}，连接后可查看网关上的项目。"
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            // 离线时 FAB 被禁用，必须说明原因，否则是一个「点了没反应」的死入口。
            if (!canBrowse) {
                item {
                    DshHintText(
                        "未连接网关，暂时无法添加项目。",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(DshFabReservedHeight)) }
        }
    }
    if (showDirectoryBrowser) {
        WorkspaceDirectoryBrowserSheet(
            stateHolder = stateHolder,
            onDismiss = { showDirectoryBrowser = false }
        )
    }
}

/** 「未分组」在 [buildWorkspaceActivityIndex] 结果里的 key：真实 workspaceId 不会是它。 */
private const val UNGROUPED_ACTIVITY_KEY = "__ungrouped__"

/** 一行项目可展示的「最新动态」：该项目最近一次活动的任务摘要。 */
internal data class WorkspaceActivity(
    val sessionId: String,
    val taskTitle: String,
    val lastActivityEpochSeconds: Double,
    val isRunning: Boolean
)

/**
 * 把会话按项目归组后取每组的最新一条：项目行详情（任务名 / 时间 / 状态点）的数据源。
 *
 * 归组语义与 [workspaceScopedSessions] 一致——「未分组」= 不在任何项目的 `sessionIds`
 * 里的可见会话；这是项目过滤的**第二份消费者**，若语义改动必须两处同步。
 * 每组只留 `lastActivityEpochSeconds` 最大的那条，列表行的「最新任务」即它。
 */
internal fun buildWorkspaceActivityIndex(
    sessions: List<SessionSummary>,
    workspaces: List<GatewayWorkspace>
): Map<String, WorkspaceActivity> {
    val latest = HashMap<String, WorkspaceActivity>()
    val assigned = HashSet<String>()
    fun consider(key: String, session: SessionSummary) {
        val current = latest[key]
        if (current == null || session.lastActivityEpochSeconds > current.lastActivityEpochSeconds) {
            latest[key] = WorkspaceActivity(
                sessionId = session.id,
                taskTitle = session.title,
                lastActivityEpochSeconds = session.lastActivityEpochSeconds,
                isRunning = session.isRunning
            )
        }
    }
    for (workspace in workspaces) {
        val byId = sessions.associateByTo(HashMap()) { it.id }
        for (sessionId in workspace.sessionIds) {
            val session = byId[sessionId] ?: continue
            if (!session.isVisibleInHistory) continue
            assigned += sessionId
            consider(workspace.workspaceId, session)
        }
    }
    for (session in sessions) {
        if (session.isVisibleInHistory && session.id !in assigned) {
            consider(UNGROUPED_ACTIVITY_KEY, session)
        }
    }
    return latest
}

/** 把项目「最新动态」映射成公共行组件的副行：状态点（待批准=琥珀 / 运行=绿 / 其他=灰）+ 任务名。 */
private fun WorkspaceActivity.toSecondLine(pendingApprovalSessionIds: Set<String>): DshRowSecondLine =
    DshRowSecondLine(
        text = taskTitle,
        accessibleText = "最新任务 $taskTitle",
        dotColor = { palette ->
            when {
                sessionId in pendingApprovalSessionIds -> DshColors.Amber
                isRunning -> DshColors.Success
                else -> palette.textTertiary
            }
        }
    )
