package com.clarklevis.dsh.android.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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

@OptIn(ExperimentalLayoutApi::class)
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

    // ---- 抽屉与底栏提升到应用外壳（v1.9.0 点 5 + 点 6）----
    // 此前抽屉只包住首页正文，因此会话页拿不到它；底栏也只有首页有。
    // 现在两者都挂在 NavHost 之上，所有核心页面共享同一份抽屉状态与会话投影
    // （`workspaceScopedSessions` 保持唯一实现，不复制成两份）。
    val application = LocalContext.current.applicationContext as? DshAndroidApplication
    val hosts = application?.hosts
    val workspaces = stateHolder.availableWorkspaces
    val selectedWorkspace = stateHolder.activeWorkspace
    val ungroupedSelected = stateHolder.isUngroupedWorkspaceSelected
    val homeSessions = stateHolder.homeSessions
    val sessions = remember(homeSessions, workspaces, stateHolder.selectedWorkspaceId) {
        workspaceScopedSessions(
            sessions = homeSessions,
            workspaces = workspaces,
            selectedWorkspaceId = stateHolder.selectedWorkspaceId
        )
    }
    // 抽屉「活跃」区块：近 24 小时有活动的任务，**跨全部项目**（不限当前项目），
    // 按最近活动倒序。`drawerActiveSessions` 只做 24h 窗口筛选与排序，输入用未做项目过滤的
    // homeSessions，这样活跃区能露出其它项目的任务；点它走 onOpenSession 直接切进对话页，
    // 跨项目切换由既有的 selectSession 路径负责，不在这里再引入项目过滤源。
    val drawerActiveSessions = remember(homeSessions) {
        drawerActiveSessions(homeSessions)
    }
    val context = LocalContext.current
    val appVersionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName}"
        }.getOrNull()
    }
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
    // 扫码与手动配对原先挂在首页内部；提升后从任意核心页面都能唤起。
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showManualPairing by rememberSaveable { mutableStateOf(false) }
    val shellScope = rememberCoroutineScope()

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = dshTabForRoute(currentRoute)

    /** 底栏切换：以首页为栈底替换顶层目的地，避免反复点 Tab 无界压栈。 */
    fun navigateToTab(tab: DshTab) {
        val route = when (tab) {
            DshTab.TASKS -> ROUTE_WORKSPACE
            DshTab.PROJECTS -> ROUTE_PROJECTS
            DshTab.SCHEDULES -> ROUTE_SCHEDULED_TASKS
            DshTab.SETTINGS -> ROUTE_SETTINGS
            DshTab.SCAN -> return
        }
        if (route == currentRoute) return
        navController.navigate(route) {
            popUpTo(ROUTE_WORKSPACE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * 打开某个任务。
     *
     * 已经在会话页时**只切换选中**、不再导航：抽屉可以在会话页打开（点 5），
     * 若这里仍裸 `navigate`，连点 N 个任务就会压入 N 层重复的会话条目，
     * 返回要按 N 次。会话内容本身由 `selectedSessionId` 驱动重渲染，无需新条目。
     */
    fun openSession(sessionId: String) {
        stateHolder.selectSession(sessionId)
        if (!requiresConversationNavigation(currentRoute)) return
        navController.navigate(ROUTE_CONVERSATION) {
            popUpTo(ROUTE_WORKSPACE)
            launchSingleTop = true
        }
    }

    WorkspaceDrawer(
        sessions = sessions,
        drawerActiveSessions = drawerActiveSessions,
        gatewayLabel = stateHolder.activeGatewayDisplayName(),
        connection = stateHolder.gatewayState.connection,
        deviceIsServer = hosts?.activeProfile?.server == true,
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
        onOpenSession = ::openSession,
        onNewSession = {
            // prepareNewSession 是 suspend，必须在协程里调用。
            shellScope.launch {
                navController.navigate(ROUTE_WORKSPACE) { launchSingleTop = true }
                if (stateHolder.prepareNewSession()) {
                    navController.navigate(ROUTE_CONVERSATION) {
                        popUpTo(ROUTE_WORKSPACE)
                        launchSingleTop = true
                    }
                }
            }
        },
        onRenameSession = stateHolder::renameSession,
        onArchiveSession = stateHolder::archiveSession,
        onSelectWorkspace = { id -> stateHolder.selectWorkspace(id) },
        onPlugins = { navController.navigate(ROUTE_PLUGINS) }
    ) { openDrawer, canScrollVertically ->
        val tab = currentTab
        // 底栏在两种情况下不显示：下钻页（tab == null），或键盘弹出时（见下方注释）。
        // 只有**确实显示**时才消费导航栏 inset——否则下钻页的列表会把 inset 扣掉，
        // 内容滑到导航栏底下。
        val showsBottomBar = tab != null && !WindowInsets.isImeVisible
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_WORKSPACE,
                // 底栏显示时它已占据底部区域（自身带 navigationBarsPadding），因此在这一层
                // 把导航栏 inset 消费掉：页内再调 `navigationBarsPadding()` 或 Scaffold 默认
                // contentWindowInsets 时解析为 0，避免同一条 inset 计两次而多出一条空白
                // （四个 Tab 目的地都会再取一次）。
                modifier = Modifier.weight(1f).then(
                    if (showsBottomBar) {
                        Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                    } else {
                        Modifier
                    }
                ),
                // 底栏切换是「平级跳转」，不该有横向滑入动画：那是下钻（进入详情）的语义，
                // 用在平级切换上会让人以为进了一层。这里按**起止路由是否都是 Tab 目的地**
                // 判定（而不是按单个目的地），因此：
                // - 首页 ↔ 项目/定时任务/设置：瞬时切换（点 2）；
                // - 首页 → 会话详情：保留左滑（真实下钻）；
                // - 会话详情 → 返回：保留右滑。
                enterTransition = {
                    if (isTabToTab(initialState.destination.route, targetState.destination.route)) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
                    }
                },
                exitTransition = {
                    if (isTabToTab(initialState.destination.route, targetState.destination.route)) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
                    }
                },
                popEnterTransition = {
                    if (isTabToTab(initialState.destination.route, targetState.destination.route)) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
                    }
                },
                popExitTransition = {
                    if (isTabToTab(initialState.destination.route, targetState.destination.route)) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
                    }
                }
            ) {
                composable(ROUTE_WORKSPACE) {
                    WorkspaceScreen(
                        stateHolder = stateHolder,
                        sessions = sessions,
                        openDrawer = openDrawer,
                        canScrollVertically = canScrollVertically,
                        onOpenSession = { id ->
                            stateHolder.selectSession(id)
                            navController.navigate(ROUTE_CONVERSATION)
                        },
                        onNewSession = {
                            // prepareNewSession 是 suspend，必须在协程里调用。
                            shellScope.launch {
                                if (stateHolder.prepareNewSession()) {
                                    navController.navigate(ROUTE_CONVERSATION)
                                }
                            }
                        }
                    )
                }
                composable(ROUTE_CONVERSATION) {
                    ConversationScreen(
                        stateHolder = stateHolder,
                        onPickImage = onPickImage,
                        onBack = navController::popBackStack,
                        onOpenDrawer = openDrawer
                    )
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
                        onOpenSession = ::openSession
                    )
                }
                composable(ROUTE_PROJECTS) {
                    ProjectsTabScreen(
                        stateHolder = stateHolder,
                        onBack = navController::popBackStack,
                        // 选中项目后回到任务列表：项目页恒由首页压栈，故 popBackStack 即回到
                        // ROUTE_WORKSPACE。这样「点项目」的意图（去看它的任务）才有结果。
                        onProjectSelected = navController::popBackStack
                    )
                }
            }
            // 核心页面保留完整切换导航（点 6）。二级下钻页（插件、Agent 预设、
            // 默认模型）返回 null，不挂底栏——它们是下钻而非目的地。
            //
            // 底栏是 NavHost 的**兄弟节点**：页面切换时它保持不动（符合「常驻导航」的
            // 预期），同时仍在抽屉的「滑动页面」内部，抽屉打开时会随内容一起右移。
            //
            // 键盘弹出时隐藏底栏（`showsBottomBar` 的第二个条件）：`adjustResize` 下键盘
            // 缩小 layoutHeight，会把底栏顶到键盘正上方，与同样 `.imePadding()` 上浮的
            // 输入框叠加，吃掉约 84dp 输入区。首页从不暴露此问题（它没有输入框）。
            if (showsBottomBar) {
                DshBottomBarHost(
                    selected = requireNotNull(tab),
                    onSelectTab = ::navigateToTab,
                    onScanRequested = {
                        stateHolder.clearPlatformError()
                        showQrScanner = true
                    },
                    onManualEntryRequested = {
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
    stateHolder.platformError?.let { error ->
        // 「重新连接」只在连接**真的需要用户介入**时给出：
        // - 过渡态（连接中/认证中）说明重连已在进行，此时再给一个「重新连接」
        //   只会诱导用户重复触发，而两个按钮的差别也说不清；
        // - 未连接/已暂停属于空闲态，重连同样会自行发生（回前台时
        //   applicationDidBecomeActive → scheduleReconnectLocked）。
        // 只有连接失败或等待网络才需要人决定是否重试。
        val needsManualReconnect = stateHolder.gatewayState.connection.dshPhase ==
            DshConnectionPhase.ATTENTION
        DshAlertDialog(
            title = "DeepSeek Harness",
            message = error,
            onDismissRequest = stateHolder::clearPlatformError,
            dismissLabel = "好",
            onDismissClick = stateHolder::clearPlatformError,
            confirmLabel = if (needsManualReconnect) "重新连接" else null,
            onConfirm = if (needsManualReconnect) {
                {
                    stateHolder.clearPlatformError()
                    stateHolder.connect()
                }
            } else {
                null
            }
        )
    }
}

/**
 * 路由 → 底栏选中项。返回 `null` 表示该路由是二级下钻页，不显示底栏。
 *
 * 会话页归入「任务列表」：它是任务列表里某一条任务的详情，不是独立目的地。
 * （因此它虽然显示底栏，却**不算**平级 Tab 目的地——见 [isTabToTab]。）
 */
internal fun dshTabForRoute(route: String?): DshTab? = when (route) {
    ROUTE_WORKSPACE, ROUTE_CONVERSATION -> DshTab.TASKS
    ROUTE_PROJECTS -> DshTab.PROJECTS
    ROUTE_SCHEDULED_TASKS -> DshTab.SCHEDULES
    ROUTE_SETTINGS -> DshTab.SETTINGS
    else -> null
}

/** 底栏四个平级目的地。用来判定「这次跳转是不是 Tab 切换」。 */
private val TAB_ROOT_ROUTES = setOf(
    ROUTE_WORKSPACE,
    ROUTE_PROJECTS,
    ROUTE_SCHEDULED_TASKS,
    ROUTE_SETTINGS
)

/**
 * 在 [currentRoute] 上打开一个任务时，是否需要**导航**到会话页。
 *
 * 已经在会话页时返回 `false`：抽屉可以在会话页打开（点 5），用它连续切换 N 个任务
 * 若是每次都裸 `navigate`，就会压入 N 层重复的会话条目，返回要按 N 次。
 * 会话内容本身由 `selectedSessionId` 驱动重渲染，不需要新的返回栈条目。
 */
internal fun requiresConversationNavigation(currentRoute: String?): Boolean =
    currentRoute != ROUTE_CONVERSATION

/**
 * 两个路由之间是否属于「底栏平级切换」——决定要不要播放横向滑动动画。
 *
 * 判据是**两端都是 Tab 根目的地且不相同**。刻意不包含会话页：虽然它挂底栏
 * （归在「任务列表」下），但进入它是下钻，应当保留滑动语义。
 */
internal fun isTabToTab(from: String?, to: String?): Boolean =
    from != null && to != null && from != to && from in TAB_ROOT_ROUTES && to in TAB_ROOT_ROUTES

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

/**
 * 首页正文：品牌头 + 新建任务 + 最近活跃任务列表（撑满剩余高度）。
 *
 * 抽屉与底栏已上移到 [DshProductApp] 的应用外壳（v1.9.0 点 5/6），
 * 本函数只负责「滑动页面」内的内容。`sessions` 由外壳统一投影后传入，
 * 保证首页与抽屉永远来自同一份 `workspaceScopedSessions` 结果。
 *
 * 「当前项目」卡已移除：目录名与连接点在品牌头副标题里已有，卡片本身不可点，
 * 占掉的 66dp 只是把首屏真正要看的内容（会话列表）往下推。切换/新增项目走底栏「项目」Tab。
 *
 * 会话列表**不截断**并独占剩余高度：列表自身是 LazyColumn，条数超出一屏时在卡片内滚动，
 * 品牌头与「新建任务」保持钉住。此前截断到 6 条，会让「最近活跃」覆盖不到稍早的任务，
 * 而这正是用户最常点进来的入口。
 */
@Composable
private fun WorkspaceScreen(
    stateHolder: AndroidSharedStateHolder,
    sessions: List<SessionSummary>,
    openDrawer: () -> Unit,
    canScrollVertically: Boolean,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit
) {
    var showRuntimeSettings by rememberSaveable { mutableStateOf(false) }
    // 连接相位变化是重连边沿，此时缓存已陈旧，必须强制刷新（不能被 TTL 合并挡掉）。
    LaunchedEffect(stateHolder.gatewayState.connection) { stateHolder.refreshProductState(force = true) }

    val selectedWorkspace = stateHolder.activeWorkspace
    val ungroupedSelected = stateHolder.isUngroupedWorkspaceSelected
    // 未连接时新建会失败（prepareNewSession 会拒绝），因此这里与抽屉一致地置灰，
    // 避免用户点了才看到报错。
    val canStartNewSession = !stateHolder.gatewayState.connection.dshBlocksNetworkActions

    // 列表用 weight(1f) 吃掉品牌头/按钮之后的剩余高度：内容不足一屏时底栏上方
    // 不会留一块空白，超出时在列表内部滚动。
    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .testTag("workspace-screen")
    ) {
        Spacer(Modifier.height(12.dp))
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
        DshNewTaskButton(
            // 与抽屉里的同名按钮保持一致（此前首页叫「新建会话」、抽屉叫
            // 「新建任务」，同一个动作两个叫法）。
            label = "新建任务",
            onClick = onNewSession,
            enabled = canStartNewSession
        )
        Spacer(Modifier.height(22.dp))
        HomeRecentSessions(
            allSessions = sessions,
            connection = stateHolder.gatewayState.connection,
            onOpenSession = onOpenSession,
            onRenameSession = stateHolder::renameSession,
            onArchiveSession = stateHolder::archiveSession,
            modifier = Modifier.weight(1f),
            canScrollVertically = canScrollVertically
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = dshPalette()
    // 与「新建任务」按钮一致的禁用表达：降低图标与底色的对比度，明确表示此刻点不了
    // （ProjectTab 用它表达「未连接网关，无法添加项目」）。
    val contentColor = if (enabled) palette.textPrimary else palette.textTertiary
    Box(
        modifier = modifier
            .size(44.dp)
            .background(palette.surfaceMuted, CircleShape)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(contentColor)
        )
    }
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

/**
 * 抽屉「活跃」区块的数据投影：**近 24 小时内有活动的任务**，按最近活动倒序。
 *
 * 入参 [scopedSessions] 是不限项目的全量会话（`homeSessions`）——「活跃」区块**跨全部项目**
 * 露出近 24h 任务，方便从其它项目快速切进来。项目过滤只作用于下方「任务」历史区块，
 * 不在这里重复实现（避免再引入一个项目过滤源）。
 * 这里只做 24 小时窗口筛选与排序：
 *  - `lastActivityEpochSeconds` 是网关 / 离线缓存给出的活动时刻（秒）；
 *  - 以当前时刻为界，活动时刻严格大于 `now - 24h` 才算「活跃」；
 *  - `nowMillis` 默认取 `System.currentTimeMillis()`，但单测可注入固定时钟，避免依赖墙上时间。
 *
 * 空输入、或没有任何会话落在窗口内时返回空列表——抽屉据此整段跳过「活跃」区块，
 * 不暴露「活跃 (0)」这类空洞分组。
 *
 * 下界用 `>` 而非 `>=`：恰好满 24 小时前的活动算「一天前」，不计入「近 24 小时」，
 * 避免把一整天前的任务当成刚活跃过。
 */
internal fun drawerActiveSessions(
    scopedSessions: List<SessionSummary>,
    nowMillis: Long = System.currentTimeMillis()
): List<SessionSummary> {
    val cutoffSeconds = (nowMillis / 1_000) - 24 * 3_600
    return scopedSessions
        .filter { it.lastActivityEpochSeconds > cutoffSeconds && it.isVisibleInHistory }
        .sortedByDescending(SessionSummary::lastActivityEpochSeconds)
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
