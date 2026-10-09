package com.clarklevis.dsh.android.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    // ---- 抽屉已从应用外壳收敛到任务详情页（本轮改动）----
    // 此前抽屉包住整个 NavHost（v1.9.0 点 5），首页也能唤起；但首页已有完整任务列表，
    // 抽屉里的会话列表与之重复。现在抽屉只挂在任务详情页内部——那里的任务间切换
    // 才是它的高频场景。设备下拉、配对、账户卡等状态仍在这里准备，传给详情页。
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

    // 抽屉的数据由外壳准备（设备列表、版本号、会话投影），组件本体只挂任务详情页。
    // openSession 保留「已在会话页只切换选中」的语义：抽屉在详情页内打开，
    // 连续切换 N 个任务不能压 N 层栈。
    val drawerCallbacks = WorkspaceDrawerCallbacks(
        sessions = sessions,
        gatewayLabel = stateHolder.activeGatewayDisplayName(),
        connection = stateHolder.gatewayState.connection,
        deviceIsServer = hosts?.activeProfile?.server == true,
        devices = drawerDevices,
        onSelectDevice = { id ->
            hosts?.profiles?.firstOrNull { it.localId == id }?.let { hosts.select(it) }
        },
        onPairNewDevice = { showQrScanner = true },
        accountName = "dsh-mobile",
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
        onPlugins = { navController.navigate(ROUTE_PLUGINS) }
    )

    val tab = currentTab
    // 底栏在两种情况下不显示：下钻页（tab == null），或键盘弹出时（见下方注释）。
    // 只有**确实显示**时才消费导航栏 inset——否则下钻页的列表会把 inset 扣掉，
    // 内容滑到导航栏底下。
    val showsBottomBar = tab != null && !WindowInsets.isImeVisible
    // 扫码/手动配对入口：底栏 Tab 移除后，挂到任务/项目页右上角的
    // [DshScanMenuButton]（通过 CompositionLocal 下发，页面无需层层传参）。
    val scanActions = ScanActions(
        onScanRequested = {
            stateHolder.clearPlatformError()
            showQrScanner = true
        },
        onManualEntryRequested = {
            stateHolder.clearPlatformError()
            showManualPairing = true
        }
    )
    CompositionLocalProvider(LocalScanActions provides scanActions) {
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
            // 底栏切换**一律无横向动画**：四个 Tab 是平级目的地，左右滑动是
            // 「进入下一层」的语义，用在平级切换上会让人以为进了一层。
            // 判据是「起止路由都是 Tab 根目的地」——不依赖具体的选中项，
            // 因此任何两个 Tab 之间互切都是瞬时。
            // 详情页（下钻）不在 [TAB_ROOT_ROUTES] 内，进出仍保留滑动语义。
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
                // 任务详情页自带抽屉（本轮改动）：抽屉从应用外壳收敛到这里，
                // 任务间切换/设备切换在详情页内完成，首页不再有抽屉入口。
                WorkspaceDrawerHost(
                    callbacks = drawerCallbacks,
                    content = {
                        ConversationScreen(
                            stateHolder = stateHolder,
                            onPickImage = onPickImage,
                            onBack = navController::popBackStack
                        )
                    }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    stateHolder = stateHolder,
                    appVersionLabel = appVersionLabel,
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
                    onOpenSession = ::openSession,
                    // FAB「＋」的新建动作：宿主网关没有「新建定时任务」协议（Web 端的
                    // 「新建」同样引导开新会话），因此跳到首页并新建任务——定时任务
                    // 由 Agent 在会话里创建，创建后回本页刷新可见。
                    onNewTask = {
                        shellScope.launch {
                            navController.navigate(ROUTE_WORKSPACE) {
                                popUpTo(ROUTE_WORKSPACE) { inclusive = true }
                                launchSingleTop = true
                            }
                            if (stateHolder.prepareNewSession()) {
                                navController.navigate(ROUTE_CONVERSATION)
                            }
                        }
                    }
                )
            }
            composable(ROUTE_PROJECTS) {
                ProjectsTabScreen(
                    stateHolder = stateHolder,
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
                onSelectTab = ::navigateToTab
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
 * 任务详情页是**下钻页**，不返回 Tab（因此也不挂底栏，见下方 [dshTabForRoute]）。
 */
internal fun dshTabForRoute(route: String?): DshTab? = when (route) {
    ROUTE_WORKSPACE -> DshTab.TASKS
    ROUTE_PROJECTS -> DshTab.PROJECTS
    ROUTE_SCHEDULED_TASKS -> DshTab.SCHEDULES
    ROUTE_SETTINGS -> DshTab.SETTINGS
    // 任务详情页**不挂底栏**：它是下钻页（返回按钮在页头左上角），
    // 底栏占掉的 60+dp 全部还给对话内容。任务间切换走返回列表再进，
    // 或直接用系统返回手势退出详情。
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
 * 判据是**两端都是 Tab 根目的地且不相同**。会话页不在其中：它是下钻页，
 * 进出都应保留滑动语义。
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

/**
 * 页面顶栏的**唯一**实现：左侧按钮 + 居中标题 + 右侧动作区。
 *
 * 层级规则（本轮重整）：**Tab 根页面**（任务列表/项目/定时任务/设置）不渲染左侧按钮——
 * 它们是平级目的地，切换走底栏，「后退」没有语义；**下钻页**（任务详情等）传 [onBack]
 * 渲染返回钮。抽屉按钮（[onOpenDrawer]）与返回钮互斥：详情页包在抽屉宿主里时左侧
 * 是抽屉钮，返回改走系统返回手势。
 *
 * 标题居中、字号统一取 [DshPageTitleFontSize]（18sp）。左侧无按钮时渲染等宽空占位，
 * 保证标题在**整屏**居中而不是在剩余空间里居中。
 */
@Composable
internal fun DshPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    connection: GatewayConnectionState? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    /**
     * 右侧动作区的宽度。默认与左侧按钮槽位等宽，使标题**整屏居中**。
     * 需要放多个动作时显式给一个更大的值——左侧占位会自动取同样宽度，
     * 标题仍居中（两侧对称）。不要让动作区自适应增长，那会把标题推离中线。
     */
    actionsWidth: androidx.compose.ui.unit.Dp? = null,
    actions: @Composable () -> Unit = {}
) {
    val palette = dshPalette()
    val sideSlotWidth = actionsWidth ?: DshPageHeaderCircleButtonSize
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.canvas)
            .statusBarsPadding()
            .height(DshPageHeaderHeight)
            .padding(horizontal = DshPageHeaderHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧槽位：抽屉钮（详情页）> 返回钮（下钻页）> 空占位（Tab 根页面）。
        // 宽度与右侧动作区**对称**，标题因此落在整屏中线上。
        Box(
            Modifier.width(sideSlotWidth).height(DshPageHeaderCircleButtonSize),
            contentAlignment = Alignment.Center
        ) {
            when {
                onOpenDrawer != null -> DshDrawerButton(
                    onClick = onOpenDrawer,
                    size = DshPageHeaderCircleButtonSize,
                    testTag = "brand-drawer-button"
                )
                onBack != null -> TopBarCircleButton(
                    iconRes = R.drawable.ic_back_chevron,
                    description = "返回",
                    onClick = onBack
                )
            }
        }
        // 标题 + 可选副标题。**两种页面共用同一套行高**：即使没有副标题，
        // 标题也占据完整的两行区块（副标题位置留空），因此四个页面的标题
        // 落在同一基线上，切换 Tab 时不会上下跳。
        // 标题 + 副标题作为**一整块**点击区（首页 → 任务运行设置）。
        // 保留原来的 testTag 与 contentDescription：读屏用户需要知道「点这里进设置」，
        // 而测试也依赖该 tag（AndroidUiParityDeviceTest 断言它可见）。
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onTitleClick)
                            .semantics { contentDescription = "任务运行设置" }
                            .testTag("brand-runtime-settings-button")
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = DshPageTitleFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            // 副标题槽位：有内容时显示，无内容时留一个等高占位——
            // 用 Box 固定高度而不是条件渲染，保证标题的垂直位置在两种页面间一致。
            Box(
                modifier = Modifier.height(DshHeaderSubtitleSlotHeight),
                contentAlignment = Alignment.Center
            ) {
                if (subtitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (connection != null) {
                            StatusIndicatorDot(
                                color = dshConnectionDotColor(connection, palette),
                                modifier = Modifier.size(7.dp).testTag("header-connection-dot"),
                                glowing = connection == GatewayConnectionState.CONNECTED
                            )
                        }
                        Text(
                            text = subtitle,
                            color = palette.textTertiary,
                            fontSize = DshHeaderSubtitleFontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        // `›` 紧跟在副标题之后，而不是整行末尾：整块都是点击区，
                        // 箭头贴着「未分组」才读得出「点这里进设置」。
                        if (onTitleClick != null) {
                            Image(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("header-runtime-settings-chevron"),
                                colorFilter = ColorFilter.tint(palette.textTertiary)
                            )
                        }
                    }
                }
            }
        }
        // 右侧动作区**必须与左侧槽位等宽**，标题才是整屏居中而不是在剩余空间里居中。
        //
        // 此前这里放宽成自适应宽度以容纳「抽屉 + 更多」两个按钮，代价是标题被推离中线
        // （真机实测定时任务页标题偏左 28dp）。需要放多个动作的页面应改用
        // [DshPageHeader.actionsWidth] 显式声明，并同步给左侧占位同样的宽度，
        // 而不是让动作区无限增长。
        Box(
            Modifier.width(actionsWidth ?: DshPageHeaderCircleButtonSize),
            contentAlignment = Alignment.Center
        ) {
            actions()
        }
    }
}

/**
 * 首页正文（= 任务列表）：页头固定 + 新建任务 + 任务列表整页滚动。
 *
 * 页头**不参与滚动**：它和底栏一样是常驻 chrome。此前品牌头是列表的第一个 item，
 * 会随内容滚出屏幕，滚到下面就没有任何入口能回顶部或开抽屉；
 * 而列表内嵌滚动时它又是钉住的——同一个页头两种行为，取决于内容多少。
 *
 * 页头走统一的 [DshPageHeader]：与项目/定时任务/设置**同一高度(56dp)、同一按钮尺寸
 * (46dp)、同一标题字号(18sp)与同一居中规则**。此前首页用的是另一套品牌头
 * （无固定高度、47dp 按钮、17–20sp 自适应标题），切页面时页头整体跳动。
 * 抽屉按钮已随抽屉一起移到任务详情页（本轮改动），首页页头左侧不再有按钮。
 *
 * 页头之下是**单一** [LazyColumn]：新建任务与任务列表一起滚出屏幕。
 * 「最近活跃」分区标题已去掉——整页就是任务列表，不需要一个标题再声明一次。
 */
@Composable
private fun WorkspaceScreen(
    stateHolder: AndroidSharedStateHolder,
    sessions: List<SessionSummary>,
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

    // 统一走 DshTabScaffold：页头（设备|项目副标题 + 配对入口）+ 正文 + 右下角 FAB。
    // 布局几何集中在骨架里，本页只提供语义（标题、副标题、FAB 的动作与禁用）。
    DshTabScaffold(
        title = "dsh-mobile",
        subtitle = runtimeHeaderSubtitle(
            deviceLabel = stateHolder.activeGatewayDisplayName(),
            workspaceLabel = selectedWorkspace?.title
                ?: if (ungroupedSelected) "未分组" else null
        ),
        connection = stateHolder.gatewayState.connection,
        onTitleClick = { showRuntimeSettings = true },
        // 任务列表是通栏列表：白底与行同色，滚动时整屏一体（参考微信通讯录）。
        // 项目/定时任务页仍用画布灰，白色卡片才会浮起来。
        bodyIsWhite = true,
        modifier = Modifier.testTag("workspace-screen"),
        fab = DshFabSpec(
            onClick = onNewSession,
            contentDescription = "新建任务",
            testTag = "new-task-button",
            // 未连接时新建会失败（prepareNewSession 会拒绝），先置灰，
            // 避免用户点了才看到报错；空态文案里也指向这个入口。
            enabled = canStartNewSession
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = DshScreenHorizontalPadding,
                end = DshScreenHorizontalPadding,
                top = DshSectionSpacing,
                // FAB 避让统一取公共常量，避免最后一条被 FAB 压住。
                bottom = DshFabReservedHeight
            )
        ) {
            // 顶部胶囊「新建任务」已移到右下角 FAB（本轮 UI 统一：所有新增动作
            // 一律右下角，与项目页/定时任务页一致），列表从页头下直接开始。
            // 任务列表本身就是这一页：不再有「最近活跃」标题，也没有内嵌滚动容器。
            taskListItems(
                allSessions = sessions,
                connection = stateHolder.gatewayState.connection,
                onOpenSession = onOpenSession,
                onRenameSession = stateHolder::renameSession,
                onArchiveSession = stateHolder::archiveSession
            )
        }
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
internal fun AndroidSharedStateHolder.activeGatewayDisplayName(): String =
    gatewayDisplayName.takeIf { it.isNotBlank() } ?: "未连接设备"
