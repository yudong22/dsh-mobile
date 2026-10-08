package com.clarklevis.dsh.android.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clarklevis.dsh.android.AndroidNotificationSessionRoute
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.DshAndroidApplication
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.domain.SessionSummary
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.gateway.GatewayRuntimeState
import com.clarklevis.dsh.shared.protocol.GatewayWorkspace
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch

private const val ROUTE_WORKSPACE = "workspace"
private const val ROUTE_CONVERSATION = "conversation"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SETTINGS_AGENT_PRESETS = "settings/agent-presets"
private const val ROUTE_SETTINGS_DEFAULT_MODEL = "settings/default-model"
private const val ROUTE_PLUGINS = "plugins"
private const val ROUTE_SCHEDULED_TASKS = "scheduled-tasks"

/** 底部「工作区」标签的目的地。 */
private const val ROUTE_PROJECTS = "projects"

@Composable
internal fun DshProductApp(
    stateHolder: AndroidSharedStateHolder,
    onPickImage: () -> Unit,
    notificationRoute: AndroidNotificationSessionRoute?,
    onNotificationRouteOpened: (AndroidNotificationSessionRoute) -> Unit
) {
    val navController = rememberNavController()
    // 用派生状态做 key，而不是整个 `snapshot`：后者是单个 mutableStateOf，流式回复只改
    // conversation 也会让这里每 token 重启一次（并连带让本作用域重组）。
    val notificationSessions = stateHolder.homeSessions
    LaunchedEffect(notificationRoute, notificationSessions) {
        val route = notificationRoute ?: return@LaunchedEffect
        if (notificationSessions.none { it.id == route.sessionId }) return@LaunchedEffect
        stateHolder.selectSession(route.sessionId)
        navController.navigate(ROUTE_CONVERSATION) {
            popUpTo(ROUTE_WORKSPACE)
            launchSingleTop = true
        }
        onNotificationRouteOpened(route)
    }
    // 用有效配色判断而不是系统主题：用户把「界面」设为浅色而系统为深色时，
    // isSystemInDarkTheme() 会与实际渲染的 palette 分叉，导致状态栏图标与画布对比度反转。
    val dark = dshPalette().isDark
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    NavHost(
        navController = navController,
        startDestination = ROUTE_WORKSPACE,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
        }
    ) {
        composable(ROUTE_WORKSPACE) {
            val workspaceScope = rememberCoroutineScope()
            WorkspaceScreen(
                stateHolder = stateHolder,
                onOpenSession = { id ->
                    stateHolder.selectSession(id)
                    navController.navigate(ROUTE_CONVERSATION)
                },
                onNewSession = {
                    workspaceScope.launch {
                        if (stateHolder.prepareNewSession()) {
                            navController.navigate(ROUTE_CONVERSATION)
                        }
                    }
                },
                onSettings = { navController.navigate(ROUTE_SETTINGS) },
                onPlugins = { navController.navigate(ROUTE_PLUGINS) },
                onScheduledTasks = { navController.navigate(ROUTE_SCHEDULED_TASKS) },
                onProjects = { navController.navigate(ROUTE_PROJECTS) }
            )
        }
        composable(ROUTE_CONVERSATION) {
            ConversationScreen(stateHolder, onPickImage, navController::popBackStack)
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                stateHolder = stateHolder,
                onBack = navController::popBackStack,
                onOpenAgentPresets = { navController.navigate(ROUTE_SETTINGS_AGENT_PRESETS) },
                onOpenDefaultModel = { navController.navigate(ROUTE_SETTINGS_DEFAULT_MODEL) }
            )
        }
        composable(ROUTE_SETTINGS_AGENT_PRESETS) {
            AgentPresetSelectionScreen(stateHolder, navController::popBackStack)
        }
        composable(ROUTE_SETTINGS_DEFAULT_MODEL) {
            DefaultModelSelectionScreen(stateHolder, navController::popBackStack)
        }
        composable(ROUTE_PLUGINS) {
            DrawerDestinationScreen("插件", "插件功能尚未接入", navController::popBackStack)
        }
        composable(ROUTE_SCHEDULED_TASKS) {
            ScheduledTasksScreen(
                stateHolder = stateHolder,
                onBack = navController::popBackStack,
                onOpenSession = { sessionId ->
                    stateHolder.selectSession(sessionId)
                    navController.navigate(ROUTE_CONVERSATION) {
                        popUpTo(ROUTE_WORKSPACE)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_PROJECTS) {
            ProjectsTabScreen(
                stateHolder = stateHolder,
                onBack = navController::popBackStack
            )
        }
    }
    stateHolder.platformError?.let { error ->
        DshAlertDialog(
            title = "DeepSeek Harness",
            message = error,
            onDismissRequest = stateHolder::clearPlatformError,
            dismissLabel = "好",
            onDismissClick = stateHolder::clearPlatformError,
            confirmLabel = "重新连接",
            onConfirm = {
                stateHolder.clearPlatformError()
                stateHolder.connect()
            }
        )
    }
}

@Composable
private fun DrawerDestinationScreen(title: String, message: String, onBack: () -> Unit) {
    val palette = dshPalette()
    Scaffold(
        containerColor = palette.canvas,
        topBar = { DshPageHeader(title = title, onBack = onBack) }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = palette.textSecondary)
        }
    }
}

/** 二级页面统一顶栏：返回圆钮 + 居中标题。 */
@Composable
internal fun DshPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val palette = dshPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.canvas)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarCircleButton(
            iconRes = R.drawable.ic_back_chevron,
            description = "返回",
            onClick = onBack
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        actions()
    }
}

@Composable
private fun WorkspaceScreen(
    stateHolder: AndroidSharedStateHolder,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onPlugins: () -> Unit,
    onScheduledTasks: () -> Unit,
    onProjects: () -> Unit
) {
    var showManualPairing by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showWorkspaceMenu by rememberSaveable { mutableStateOf(false) }
    var showDirectoryBrowser by rememberSaveable { mutableStateOf(false) }
    var showRuntimeSettings by rememberSaveable { mutableStateOf(false) }
    // 底栏「扫码」展开的认证菜单（扫码 / 手动输入）。原先挂在顶栏，现已下移。
    var showAuthMenu by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(stateHolder.gatewayState.connection) { stateHolder.refreshProductState() }

    val workspaces = stateHolder.availableWorkspaces
    val selectedWorkspace = stateHolder.activeWorkspace
    val ungroupedSelected = stateHolder.isUngroupedWorkspaceSelected
    // 经派生状态读取，而不是直接读 stateHolder.snapshot：后者是单个 mutableStateOf，
    // 任何字段变化（例如只改 conversation 的流式发布）都会让整个首页失效重组。
    val homeSessions = stateHolder.homeSessions
    // 抽屉的会话列表：随选择的工作区过滤。首页本体的会话列表与搜索框已移除。
    val sessions = remember(homeSessions, workspaces, stateHolder.selectedWorkspaceId) {
        workspaceScopedSessions(
            sessions = homeSessions,
            workspaces = workspaces,
            selectedWorkspaceId = stateHolder.selectedWorkspaceId
        )
    }
    val palette = dshPalette()

    // O(会话 × 工作区) 的统计放在 remember 里：直接写在 item 体内会随每次重组重跑。
    val ungroupedSessionCount = remember(homeSessions, workspaces) {
        homeSessions.count { session ->
            session.isVisibleInHistory && workspaces.none { session.id in it.sessionIds }
        }
    }
    // 当前主机是否为「服务器」类型，决定抽屉设备图标的形态。
    val application = LocalContext.current.applicationContext as? DshAndroidApplication
    // 从 PackageManager 读版本号，避免像 "v1.8.0" 那样写死后在发版时忘记同步
    // （buildConfig 未启用，所以没有 BuildConfig.VERSION_NAME 可用）。
    val context = LocalContext.current
    val appVersionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName}"
        }.getOrNull()
    }
    val hosts = application?.hosts
    val activeGatewayIsServer = hosts?.activeProfile?.server == true
    // 未连接时新建会失败（prepareNewSession 会拒绝），因此这里与抽屉一致地置灰，
    // 避免用户点了才看到报错。
    val canStartNewSession = !stateHolder.gatewayState.connection.dshBlocksNetworkActions
    // 抽屉设备下拉的数据源：已配对设备 + 在线/选中态。
    val drawerDevices = remember(hosts?.profiles, hosts?.activeId, hosts?.onlineIds) {
        hosts?.profiles.orEmpty().map { profile ->
            DrawerDeviceOption(
                id = profile.localId,
                name = profile.displayName,
                isServer = profile.server,
                online = profile.localId in hosts?.onlineIds.orEmpty(),
                selected = profile.localId == hosts?.activeId
            )
        }
    }
    // 底部标签栏必须放在抽屉的「滑动页面」内部：抽屉打开时它随页面向右滑走，
    // 与参考截图一致；若留在 Scaffold 的 bottomBar 上，抽屉展开后标签栏会悬在抽屉上方。
    WorkspaceDrawer(
        sessions = sessions,
        gatewayLabel = stateHolder.activeGatewayDisplayName(),
        connection = stateHolder.gatewayState.connection,
        deviceIsServer = activeGatewayIsServer,
        devices = drawerDevices,
        onSelectDevice = { id ->
            hosts?.profiles?.firstOrNull { it.localId == id }?.let { hosts.select(it) }
        },
        onPairNewDevice = { showQrScanner = true },
        spaces = workspaces,
        selectedWorkspaceId = stateHolder.selectedWorkspaceId,
        ungroupedSelected = ungroupedSelected,
        // 账户卡展示产品身份而不是设备名：设备名已在上一行的设备下拉里，
        // 重复显示会让人误以为「账户名 = 设备名」。
        accountName = "DeepSeek Harness",
        accountPlan = "标准版",
        accountQuota = appVersionLabel,
        onOpenSession = onOpenSession,
        onNewSession = onNewSession,
        onRenameSession = stateHolder::renameSession,
        onArchiveSession = stateHolder::archiveSession,
        onSelectWorkspace = { id -> stateHolder.selectWorkspace(id) },
        onPlugins = onPlugins
    ) { openDrawer, canScrollVertically ->
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .statusBarsPadding()
                    .testTag("workspace-screen"),
                contentPadding = PaddingValues(horizontal = 18.dp),
                userScrollEnabled = canScrollVertically
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        DshBrandHeader(
                            title = "DeepSeek Harness",
                            subtitle = runtimeHeaderSubtitle(
                                deviceLabel = stateHolder.activeGatewayDisplayName(),
                                workspaceLabel = selectedWorkspace?.title
                                    ?: if (ungroupedSelected) "未分组" else null
                            ),
                            connection = stateHolder.gatewayState.connection,
                            onOpenDrawer = openDrawer,
                            onOpenRuntimeSettings = { showRuntimeSettings = true }
                        )
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.fillMaxWidth()) {
                            WorkspaceCard(
                                workspace = selectedWorkspace,
                                ungrouped = ungroupedSelected,
                                ungroupedCount = ungroupedSessionCount,
                                state = stateHolder.gatewayState,
                                onClick = { showWorkspaceMenu = true }
                            )
                            WorkspaceSelectionMenu(
                                expanded = showWorkspaceMenu,
                                workspaces = workspaces,
                                selectedWorkspaceId = stateHolder.selectedWorkspaceId,
                                onSelect = { workspaceId ->
                                    stateHolder.selectWorkspace(workspaceId)
                                    showWorkspaceMenu = false
                                },
                                onAddWorkspace = {
                                    showWorkspaceMenu = false
                                    showDirectoryBrowser = true
                                },
                                onDismiss = { showWorkspaceMenu = false }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        DshNewTaskButton(
                            label = "新建会话",
                            onClick = onNewSession,
                            enabled = canStartNewSession
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            Box {
                DshBottomTabBar(
                    selected = DshTab.TASKS,
                    onSelect = { tab ->
                        when (tab) {
                            DshTab.TASKS -> Unit
                            DshTab.PROJECTS -> onProjects()
                            DshTab.SCHEDULES -> onScheduledTasks()
                            DshTab.SETTINGS -> onSettings()
                            // 扫码是全局动作，不做页面跳转：在底栏原位展开认证菜单
                            // （扫码 / 手动输入），与顶栏原先的行为一致。
                            DshTab.SCAN -> showAuthMenu = true
                        }
                    }
                )
                // 菜单锚在底栏左上角区域；向上弹以免超出屏幕底部。
                GatewayAuthenticationMenuContent(
                    expanded = showAuthMenu,
                    onDismissRequest = { showAuthMenu = false },
                    onScan = {
                        stateHolder.clearPlatformError()
                        showQrScanner = true
                    },
                    onManualEntry = {
                        stateHolder.clearPlatformError()
                        showManualPairing = true
                    }
                )
            }
        }
    }

    if (showQrScanner) {
        GatewayQrScannerScreen(
            onCode = { payload ->
                showQrScanner = false
                stateHolder.pair(payload)
            },
            onCancel = { showQrScanner = false },
            onFailure = { message ->
                showQrScanner = false
                stateHolder.showPlatformError(message)
            }
        )
    }
    if (showManualPairing) {
        ManualGatewayPairingSheet(stateHolder) { showManualPairing = false }
    }
    if (showDirectoryBrowser) {
        WorkspaceDirectoryBrowserSheet(
            stateHolder = stateHolder,
            onDismiss = { showDirectoryBrowser = false }
        )
    }
    if (showRuntimeSettings) {
        RuntimeSettingsSheet(
            stateHolder = stateHolder,
            deviceLabel = stateHolder.activeGatewayDisplayName(),
            workspaceLabel = selectedWorkspace?.title ?: "未分组",
            onDismiss = { showRuntimeSettings = false }
        )
    }
}

/** 浅色体系下的次级圆形按钮：次级面底 + 主文字色图标。 */
@Composable
internal fun DshTonalCircleButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Box(
        modifier = modifier
            .size(44.dp)
            .background(palette.surfaceMuted, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(palette.textPrimary)
        )
    }
}

@Composable
private fun WorkspaceCard(
    workspace: GatewayWorkspace?,
    ungrouped: Boolean,
    ungroupedCount: Int,
    state: GatewayRuntimeState,
    onClick: () -> Unit
) {
    val palette = dshPalette()
    Row(
        Modifier.fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(20.dp))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
            .testTag("workspace-card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_folder_outline),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(palette.primary)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (ungrouped) "未分组" else workspace?.title ?: "DeepseekHarnessProject",
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                if (ungrouped) "$ungroupedCount 个未归属会话" else workspace?.path ?: "通过 Mobile Gateway 连接",
                color = palette.textTertiary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        ConnectionDot(state)
        Image(
            painter = painterResource(R.drawable.ic_question_chevron_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.textTertiary),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ConnectionDot(state: GatewayRuntimeState) {
    val color = when (state.connection) {
        GatewayConnectionState.CONNECTED -> DshColors.Success
        GatewayConnectionState.FAILED -> DshColors.Danger
        GatewayConnectionState.CONNECTING, GatewayConnectionState.AUTHENTICATING,
        GatewayConnectionState.WAITING_FOR_NETWORK -> DshColors.Amber
        else -> dshPalette().textTertiary
    }
    StatusIndicatorDot(
        color = color,
        modifier = Modifier.size(8.dp),
        glowing = state.connection == GatewayConnectionState.CONNECTED
    )
}

@Composable
internal fun WhaleIcon(
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
) {
    Image(
        painter = painterResource(R.drawable.deepseek_whale),
        contentDescription = "DeepSeek",
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

internal fun workspaceScopedSessions(
    sessions: List<SessionSummary>,
    workspaces: List<GatewayWorkspace>,
    selectedWorkspaceId: String?
): List<SessionSummary> {
    val selected = workspaces.firstOrNull { it.workspaceId == selectedWorkspaceId }
        ?: workspaces.firstOrNull()
    if (
        selectedWorkspaceId == AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID ||
        selected == null
    ) {
        val assigned = workspaces.flatMapTo(mutableSetOf()) { it.sessionIds }
        return sessions.filter { it.isVisibleInHistory && it.id !in assigned }
    }
    return sessions.filter { it.isVisibleInHistory && it.id in selected.sessionIds }
}

internal fun relativeTime(
    epochSeconds: Double,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val eventMillis = (epochSeconds * 1_000).toLong().coerceAtMost(nowMillis)
    val elapsed = ((nowMillis - eventMillis) / 1_000).coerceAtLeast(0)
    val dayDifference = localDayIndex(nowMillis, timeZone) - localDayIndex(eventMillis, timeZone)
    return when {
        dayDifference == 1L -> "昨天"
        dayDifference == 2L -> "前天"
        dayDifference > 2L -> "$dayDifference 天前"
        elapsed < 60 -> "刚刚"
        elapsed < 3_600 -> "${elapsed / 60} 分钟前"
        else -> "${elapsed / 3_600} 小时前"
    }
}

/**
 * 列表滚动时每个可见行都会调用 [relativeTime]，而它每行要算两次日期索引。
 * `Calendar.getInstance()` 每次新建实例并查时区表，几十行 × 每帧会造成持续分配。
 *
 * 按 [TimeZone] 缓存可变实例复用：本函数是纯计算、无 I/O，只在 UI 线程调用。
 * 必须 `clear()` 后再设 `timeInMillis`，否则残留字段会污染结果。
 */
private val relativeTimeCalendarByZone = mutableMapOf<TimeZone, Calendar>()

private fun localDayIndex(epochMillis: Long, timeZone: TimeZone): Long {
    val calendar = relativeTimeCalendarByZone.getOrPut(timeZone) { Calendar.getInstance(timeZone) }
    calendar.clear()
    calendar.timeInMillis = epochMillis
    val previousYear = calendar.get(Calendar.YEAR).toLong() - 1
    return previousYear * 365 +
        previousYear / 4 -
        previousYear / 100 +
        previousYear / 400 +
        calendar.get(Calendar.DAY_OF_YEAR)
}

/** 当前主机的可读名称，用于顶栏副标题与抽屉账户卡。未配对时不重复产品名。 */
private fun AndroidSharedStateHolder.activeGatewayDisplayName(): String =
    gatewayDisplayName.takeIf { it.isNotBlank() } ?: "未连接设备"
