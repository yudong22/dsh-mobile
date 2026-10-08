package com.clarklevis.dsh.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val drawerPageShape = RoundedCornerShape(48.dp)

/**
 * 侧边抽屉，按参考截图优化后的结构：
 *
 * 设备行（图标 + 在线绿点 + 主机名 + 展开箭头）→ 新建任务胶囊 →
 * `任务 (n)` 区块（条目只有单行标题）→ `空间 (n)` 区块 →
 * 次级入口（插件 / 定时任务）→ 分隔线 → 账户卡。
 *
 * 会话的长按菜单可重命名 / 归档，是首页任务列表之外唯一的会话管理入口，必须保留。
 * 「插件」入口为本产品既有功能，参考截图无对应项，按不因改版丢能力的原则保留。
 */
@Composable
internal fun WorkspaceDrawer(
    sessions: List<SessionSummary>,
    gatewayLabel: String,
    connection: GatewayConnectionState,
    deviceIsServer: Boolean,
    devices: List<DrawerDeviceOption>,
    onSelectDevice: (String) -> Unit,
    onPairNewDevice: () -> Unit,
    spaces: List<GatewayWorkspace>,
    selectedWorkspaceId: String?,
    ungroupedSelected: Boolean,
    accountName: String,
    accountPlan: String,
    accountQuota: String?,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onArchiveSession: (String) -> Unit,
    onSelectWorkspace: (String?) -> Unit,
    onPlugins: () -> Unit,
    content: @Composable (openDrawer: () -> Unit, canScrollVertically: Boolean) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val drawerWidth = (maxWidth * 0.84f).coerceAtMost(360.dp)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val flingThresholdPx = with(density) { 400.dp.toPx() }
        var offsetPx by remember { mutableFloatStateOf(0f) }
        var horizontalDragActive by remember { mutableStateOf(false) }
        var animationJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val palette = dshPalette()
        val progress = (offsetPx / drawerWidthPx).coerceIn(0f, 1f)
        // 两个区块各自可折叠，与标题上的箭头语义一致。
        var tasksExpanded by remember { mutableStateOf(true) }
        var spacesExpanded by remember { mutableStateOf(true) }

        fun settle(open: Boolean, velocity: Float = 0f) {
            animationJob?.cancel()
            animationJob = scope.launch {
                animate(
                    initialValue = offsetPx,
                    targetValue = if (open) drawerWidthPx else 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(stiffness = 450f, dampingRatio = 0.86f)
                ) { value, _ -> offsetPx = value.coerceIn(0f, drawerWidthPx) }
            }
        }
        BackHandler(enabled = progress > 0f) { settle(open = false) }

        Box(Modifier.fillMaxSize().background(palette.drawerCanvas)) {
            Column(
                modifier = Modifier.width(drawerWidth).fillMaxHeight()
                    .safeDrawingPadding()
                    .graphicsLayer {
                        alpha = 0.6f + 0.4f * progress
                        scaleX = 0.9f + 0.1f * progress
                        scaleY = 0.9f + 0.1f * progress
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .testTag("workspace-drawer")
                    .then(if (progress == 0f) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                // 设备行：图标（右上角状态点）+ 主机名 + 状态副标题 + 展开箭头。
                // 点击展开设备下拉：切换已配对设备，或「新增匹配」进入扫码 / 手动配对。
                // 连接中禁止切换设备：此时 activeGraph 正在换代，切换会与握手竞争。
                var deviceMenuExpanded by remember { mutableStateOf(false) }
                val connectionTitle = dshConnectionDetailText(connection)
                Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            enabled = !connection.dshIsTransitioning
                        ) { deviceMenuExpanded = true }
                        .padding(start = 30.dp, end = 24.dp, top = 10.dp, bottom = 10.dp)
                        .testTag("drawer-device-row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    DeviceGlyph(
                        isServer = deviceIsServer,
                        connection = connection,
                        palette = palette
                    )
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            gatewayLabel,
                            color = palette.textPrimary,
                            // 主机名可能很长（如 denisMacBook-M5.local），按宽度自动降档字号，
                            // 尽量完整展示而不是直接省略成 "denisMacBook-…"。
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 13.sp,
                                maxFontSize = 19.sp,
                                stepSize = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        // 已连接是常态，不占副标题；过渡态/失败态才说明当前状态。
                        if (connectionTitle != null) {
                            Text(
                                text = connectionTitle,
                                color = dshConnectionDotColor(connection, palette),
                                fontSize = 12.sp,
                                maxLines = 1,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                modifier = Modifier.testTag("drawer-device-status")
                            )
                        }
                    }
                    if (connection.dshIsTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp).testTag("drawer-device-connecting"),
                            strokeWidth = 2.dp,
                            color = DshColors.Amber
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_question_chevron_down),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(palette.textPrimary)
                        )
                    }
                }
                    DeviceDropdown(
                        expanded = deviceMenuExpanded,
                        devices = devices,
                        palette = palette,
                        onDismiss = { deviceMenuExpanded = false },
                        onSelectDevice = { id ->
                            deviceMenuExpanded = false
                            onSelectDevice(id)
                        },
                        onPairNewDevice = {
                            deviceMenuExpanded = false
                            onPairNewDevice()
                        }
                    )
                }

                DshNewTaskButton(
                    label = "新建任务",
                    onClick = onNewSession,
                    // 未连接时新建会直接失败（prepareNewSession 会拒绝），
                    // 这里先置灰，避免用户点了才看到报错。
                    enabled = !connection.dshBlocksNetworkActions,
                    modifier = Modifier.padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 8.dp),
                    testTag = "drawer-new-task"
                )

                DrawerSectionHeader(
                    label = "任务",
                    count = sessions.size,
                    expanded = tasksExpanded,
                    onToggle = { tasksExpanded = !tasksExpanded },
                    palette = palette
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("drawer-task-list"),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (tasksExpanded) {
                        if (sessions.isEmpty()) {
                            item {
                                // 连接中列表必然为空（会话来自宿主 sessions 帧），
                                // 此时给加载态而不是「暂无会话」，否则会误导成「真的没有会话」。
                                if (connection.dshIsTransitioning) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 30.dp, vertical = 10.dp)
                                            .testTag("drawer-sessions-loading"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = DshColors.Amber
                                        )
                                        Text(
                                            text = dshConnectionDetailText(connection) ?: "正在连接…",
                                            color = palette.textTertiary,
                                            fontSize = 15.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        "暂无会话",
                                        color = palette.textTertiary,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(sessions, key = { _, session -> session.id }) { index, session ->
                                DrawerSessionRow(
                                    session = session,
                                    palette = palette,
                                    isLast = index == sessions.lastIndex,
                                    onClick = { onOpenSession(session.id) },
                                    onRename = onRenameSession,
                                    onArchive = onArchiveSession
                                )
                            }
                        }
                    }
                    // 空间区块：与「任务」并列的第二组，对应截图的「空间 (n)」。
                    item {
                        DrawerSectionHeader(
                            label = "空间",
                            count = spaces.size + 1,
                            expanded = spacesExpanded,
                            onToggle = { spacesExpanded = !spacesExpanded },
                            palette = palette
                        )
                    }
                    if (spacesExpanded) {
                        item {
                            DrawerSpaceRow(
                                label = "未分组",
                                iconRes = R.drawable.ic_tab_experts,
                                selected = ungroupedSelected,
                                palette = palette,
                                testTag = "drawer-space-ungrouped",
                                onClick = { onSelectWorkspace(null) }
                            )
                        }
                        itemsIndexed(spaces, key = { _, w -> w.workspaceId }) { _, workspace ->
                            DrawerSpaceRow(
                                label = workspace.title,
                                iconRes = R.drawable.ic_tab_projects,
                                selected = workspace.workspaceId == selectedWorkspaceId,
                                palette = palette,
                                testTag = "drawer-space-${workspace.workspaceId}",
                                onClick = { onSelectWorkspace(workspace.workspaceId) }
                            )
                        }
                    }
                }

                // 次级入口放在滚动列表「之外」：它们原先在列表末尾，会话一多就被顶出屏幕，
                // 必须滚动才能看到（实测在模拟器上 插件 不可见）。这里是固定可达区域。
                // 定时任务已下移到主底栏，抽屉不再重复入口。
                DrawerItem("插件", R.drawable.ic_drawer_plugin, "drawer-plugins", palette, onPlugins)

                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                DshAccountCard(
                    name = accountName,
                    planLabel = accountPlan,
                    quotaLabel = accountQuota
                )
            }

            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { translationX = offsetPx }
                    .then(
                        if (progress > 0f) Modifier
                            .dropShadow(
                                shape = drawerPageShape,
                                shadow = Shadow(
                                    radius = 25.dp * progress,
                                    color = Color.Black.copy(alpha = 0.18f * progress),
                                    offset = DpOffset(x = -4.dp * progress, y = 0.dp)
                                )
                            )
                            .clip(drawerPageShape)
                        else Modifier
                    )
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            animationJob?.cancel()
                            offsetPx = (offsetPx + delta).coerceIn(0f, drawerWidthPx)
                        },
                        onDragStarted = { horizontalDragActive = true },
                        onDragStopped = { velocity ->
                            horizontalDragActive = false
                            val open = when {
                                velocity > flingThresholdPx -> true
                                velocity < -flingThresholdPx -> false
                                else -> offsetPx >= drawerWidthPx * 0.5f
                            }
                            settle(open, velocity)
                        }
                    )
                    .background(palette.canvas)
                    .testTag("workspace-drawer-main")
            ) {
                content({ settle(open = true) }, !horizontalDragActive && offsetPx == 0f)
                if (progress > 0.98f) {
                    Box(
                        Modifier.fillMaxSize().clickable(
                            role = Role.Button,
                            onClickLabel = "关闭侧边栏"
                        ) { settle(open = false) }
                    )
                }
            }
        }
    }
}

/**
 * 抽屉内的会话行：参考截图里只有单行标题（字号明显大于区块标题），
 * 运行/未读用左侧小圆点表达，不再显示相对时间以免挤压长标题。
 *
 * 长按弹出菜单可重命名 / 归档——首页任务列表移除后，这里是会话管理
 * （重命名、删除）的唯一入口，不能再退回成纯展示行。
 */
@Composable
private fun DrawerSessionRow(
    session: SessionSummary,
    palette: DshPalette,
    isLast: Boolean,
    onClick: () -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String) -> Unit
) {
    var menuVisible by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(session.title) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { menuVisible = true }
            )
            .padding(horizontal = 30.dp, vertical = 13.dp)
            .testTag("drawer-session-${session.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (session.isRunning || session.hasUnread) {
            Box(
                Modifier.size(6.dp).background(
                    if (session.isRunning) DshColors.Success else palette.primary,
                    CircleShape
                )
            )
        }
        Text(
            text = session.title,
            color = palette.textPrimary,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
    if (!isLast) {
        Box(
            Modifier.fillMaxWidth().padding(start = 30.dp)
                .height(1.dp)
                .background(palette.divider)
        )
    }
    SessionContextMenu(
        expanded = menuVisible,
        onDismissRequest = { menuVisible = false },
        onRename = {
            title = session.title
            menuVisible = false
            renaming = true
        },
        onArchive = {
            menuVisible = false
            archiving = true
        }
    )
    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false },
        title = { Text("重命名会话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onRename(session.id, title)
                renaming = false
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } }
    )
    if (archiving) AlertDialog(
        onDismissRequest = { archiving = false },
        title = { Text("删除会话？") },
        text = { Text("会话将被归档并从列表隐藏，历史记录会保留。") },
        confirmButton = {
            TextButton(onClick = { onArchive(session.id); archiving = false }) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = { archiving = false }) { Text("取消") } }
    )
}

/** 抽屉里区块标题的文案：`任务 (5)`、`空间 (1)`，与参考截图一致。 */
internal fun drawerSectionTitle(label: String, count: Int): String = "$label ($count)"

/** 设备图标 + 右上角在线绿点，对应截图里显示器图标上的状态点。 */
@Composable
private fun DeviceGlyph(
    isServer: Boolean,
    connection: GatewayConnectionState,
    palette: DshPalette
) {
    Box(Modifier.size(28.dp)) {
        Image(
            painter = painterResource(
                if (isServer) R.drawable.ic_gateway_server else R.drawable.ic_gateway_pc
            ),
            contentDescription = null,
            modifier = Modifier.size(26.dp).align(Alignment.BottomStart),
            colorFilter = ColorFilter.tint(palette.textPrimary)
        )
        // 状态点：已连接绿、连接中琥珀、失败红、未连接灰。
        // 未连接也画一个灰点，表示「已配对但当前不在线」，避免与「没有设备」混淆。
        Box(
            Modifier.size(8.dp).align(Alignment.TopEnd)
                .background(dshConnectionDotColor(connection, palette), CircleShape)
                .testTag("drawer-device-dot")
        )
    }
}

/** `任务 (5) ⌄` 形态的区块标题：可点击折叠，展开时箭头向下。 */
@Composable
private fun DrawerSectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    palette: DshPalette
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = 30.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
            .testTag("drawer-section-$label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = drawerSectionTitle(label, count),
            color = palette.textSecondary,
            fontSize = 15.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Image(
            painter = painterResource(R.drawable.ic_question_chevron_down),
            contentDescription = null,
            modifier = Modifier.size(17.dp).rotate(if (expanded) 0f else -90f),
            colorFilter = ColorFilter.tint(palette.textSecondary)
        )
    }
}

/** 空间条目：左侧线性图标 + 名称，选中项用主色区分。 */
@Composable
private fun DrawerSpaceRow(
    label: String,
    iconRes: Int,
    selected: Boolean,
    palette: DshPalette,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            colorFilter = ColorFilter.tint(if (selected) palette.primary else palette.textPrimary)
        )
        Text(
            text = label,
            color = if (selected) palette.primary else palette.textPrimary,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun DrawerItem(
    label: String,
    iconRes: Int,
    tag: String,
    palette: DshPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.textSecondary),
            modifier = Modifier.size(21.dp)
        )
        Text(
            label,
            color = palette.textPrimary,
            fontSize = 16.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

/** 抽屉「设备」下拉里的一台可选设备。 */
internal data class DrawerDeviceOption(
    val id: String,
    val name: String,
    val isServer: Boolean,
    val online: Boolean,
    val selected: Boolean
)

/** 设备下拉：列出已配对设备 + 末尾「新增匹配」。 */
@Composable
private fun DeviceDropdown(
    expanded: Boolean,
    devices: List<DrawerDeviceOption>,
    palette: DshPalette,
    onDismiss: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onPairNewDevice: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(268.dp).testTag("drawer-device-menu"),
        shape = RoundedCornerShape(22.dp),
        containerColor = palette.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        if (devices.isEmpty()) {
            DropdownMenuItem(
                text = { Text("尚未配对设备", color = palette.textTertiary, fontSize = 15.sp) },
                enabled = false,
                onClick = {}
            )
        } else {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                device.name,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                            Text(
                                (if (device.isServer) "服务器" else "电脑") +
                                    if (device.online) " · 在线" else " · 离线",
                                color = palette.textTertiary,
                                fontSize = 12.sp,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    },
                    leadingIcon = {
                        Image(
                            painter = painterResource(
                                if (device.isServer) R.drawable.ic_gateway_server
                                else R.drawable.ic_gateway_pc
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(palette.textSecondary)
                        )
                    },
                    trailingIcon = if (device.selected) {
                        {
                            Image(
                                painter = painterResource(R.drawable.ic_menu_check),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                colorFilter = ColorFilter.tint(palette.primary)
                            )
                        }
                    } else null,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = { onSelectDevice(device.id) }
                )
            }
        }
        HorizontalDivider(color = palette.divider)
        DropdownMenuItem(
            text = { Text("新增匹配", color = palette.primary, fontSize = 16.sp) },
            leadingIcon = {
                Image(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(palette.primary)
                )
            },
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.testTag("drawer-device-pair-new"),
            onClick = onPairNewDevice
        )
    }
}
