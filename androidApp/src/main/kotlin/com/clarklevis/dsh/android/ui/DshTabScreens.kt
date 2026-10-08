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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace

/**
 * 「项目」标签：工作区（项目）列表，支持切换与新增。
 *
 * 复用既有的 WorkspaceSelectionMenu 数据源与目录浏览器，不新建状态。
 */
@Composable
internal fun ProjectsTabScreen(
    stateHolder: AndroidSharedStateHolder,
    onBack: () -> Unit
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
    LaunchedEffect(Unit) { stateHolder.refreshProductState() }
    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            DshPageHeader(title = "项目", onBack = onBack) {
                DshTonalCircleButton(
                    iconRes = R.drawable.ic_add,
                    description = "添加项目",
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
                    onClick = { stateHolder.selectWorkspace(AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID) }
                )
            }
            items(workspaces, key = { it.workspaceId }) { workspace ->
                ProjectCard(
                    title = workspace.title,
                    detail = workspace.path,
                    selected = workspace.workspaceId == stateHolder.selectedWorkspaceId,
                    onClick = { stateHolder.selectWorkspace(workspace.workspaceId) }
                )
            }
            if (workspaces.isEmpty()) {
                item {
                    Text(
                        "还没有项目。点击右上角「＋」把网关上的目录添加为项目。",
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
