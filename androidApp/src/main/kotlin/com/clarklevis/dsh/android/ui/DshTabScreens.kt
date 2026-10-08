package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.R

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
    onBack: () -> Unit,
    onProjectSelected: () -> Unit = onBack
) {
    val palette = dshPalette()
    var showDirectoryBrowser by remember { mutableStateOf(false) }
    val workspaces = stateHolder.availableWorkspaces
    // 用派生状态读取，避免整个「项目」页因 conversation 的每 token 发布会话而重组。
    val sessions = stateHolder.homeSessions
    val ungroupedSessionCount = remember(sessions, workspaces) {
        sessions.count { session ->
            session.isVisibleInHistory && workspaces.none { session.id in it.sessionIds }
        }
    }
    // 刷新以连接相位为 key：原先是 LaunchedEffect(Unit)，若首次进入时离线，
    // 请求被状态层静默跳过，之后连接恢复也不会重跑，页面会长期停在缓存或空态。
    val connection = stateHolder.gatewayState.connection
    LaunchedEffect(connection) { stateHolder.refreshProductState(force = true) }
    // 目录浏览要读远端目录，离线时必须禁用（此前「＋」始终可点，点了才弹「请先连接」）。
    val canBrowse = !connection.dshBlocksNetworkActions
    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            DshPageHeader(title = "项目", onBack = onBack) {
                DshTonalCircleButton(
                    iconRes = R.drawable.ic_add,
                    // description 保持稳定（读屏与测试都依赖它），禁用**原因**由下方
                    // 的 footer 文案承载，而不是塞进无障碍标签里。
                    description = "添加项目",
                    enabled = canBrowse,
                    onClick = { showDirectoryBrowser = true }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).testTag("projects-list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProjectCard(
                    title = "未分组",
                    detail = "$ungroupedSessionCount 个未归属会话",
                    selected = stateHolder.isUngroupedWorkspaceSelected,
                    testTag = "project-card-ungrouped",
                    onClick = {
                        stateHolder.selectWorkspace(AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID)
                        onProjectSelected()
                    }
                )
            }
            items(workspaces, key = { it.workspaceId }) { workspace ->
                ProjectCard(
                    title = workspace.title,
                    detail = workspace.path,
                    selected = workspace.workspaceId == stateHolder.selectedWorkspaceId,
                    testTag = "project-card-${workspace.workspaceId}",
                    onClick = {
                        stateHolder.selectWorkspace(workspace.workspaceId)
                        onProjectSelected()
                    }
                )
            }
            // 空态必须区分「真的没有项目」和「连不上所以看不到」：
            // 离线时沿用旧的「还没有项目」会误导用户以为网关上确实没有项目
            // （对比首页空态已按 dshPhase 分四档，HomeRecentSessions.kt:206）。
            if (workspaces.isEmpty()) {
                item {
                    Text(
                        if (connection.dshPhase == DshConnectionPhase.ONLINE) {
                            "还没有项目。点击右上角「＋」把网关上的目录添加为项目。"
                        } else {
                            "${homeConnectionBadge(connection)}，连接后可查看网关上的项目。"
                        },
                        color = palette.textTertiary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            // 离线时「＋」被禁用，必须说明原因，否则是一个「点了没反应」的死入口。
            if (!canBrowse) {
                item {
                    Text(
                        "未连接网关，暂时无法添加项目。",
                        color = palette.textTertiary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showDirectoryBrowser) {
        WorkspaceDirectoryBrowserSheet(
            stateHolder = stateHolder,
            onDismiss = { showDirectoryBrowser = false }
        )
    }
}

@Composable
private fun ProjectCard(
    title: String,
    detail: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = dshPalette()
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(20.dp))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) palette.primary else palette.cardBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_folder_outline),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(if (selected) palette.primary else palette.textSecondary)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                detail,
                color = palette.textTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        if (selected) {
            Box(Modifier.size(18.dp).background(palette.primary, CircleShape), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_menu_check),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    colorFilter = ColorFilter.tint(palette.surface)
                )
            }
        }
    }
}
