package com.clarklevis.dsh.android.ui

import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.AttachmentLoadState
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.android.platform.AndroidAttachmentThumbnailer
import com.clarklevis.dsh.android.platform.AndroidPreparedImage
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.protocol.GatewayModelItem
import com.clarklevis.dsh.shared.protocol.GatewayReasoningEffort
import com.clarklevis.dsh.shared.projection.ConversationItem
import com.clarklevis.dsh.shared.projection.ConversationItemKind
import com.clarklevis.dsh.shared.protocol.GatewayImageAttachment
import com.clarklevis.dsh.shared.protocol.GatewayPendingQuestionRequest
import com.clarklevis.dsh.shared.protocol.GatewayQuestion
import com.clarklevis.dsh.shared.protocol.GatewayQuestionAnswer
import com.clarklevis.dsh.shared.protocol.GatewayTask
import com.clarklevis.dsh.shared.projection.TrajectoryNode
import com.clarklevis.dsh.shared.projection.TrajectoryNodeKind
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ConversationScreen(
    stateHolder: AndroidSharedStateHolder,
    onPickImage: () -> Unit,
    onBack: () -> Unit
) {
    val palette = dshPalette()
    val session = stateHolder.snapshot.sessions.firstOrNull { it.id == stateHolder.snapshot.selectedSessionId }
    val title = session?.title ?: "新建 DeepSeek Harness"
    val agentPresetId = stateHolder.sessionAgentPreset.agentPreset
        .takeIf { stateHolder.sessionAgentPreset.sessionId == stateHolder.snapshot.selectedSessionId }
        ?: session?.agentPreset ?: stateHolder.snapshot.agentPresetDefault
    val agentPresetName = stateHolder.snapshot.agentPresets
        .firstOrNull { it.id == agentPresetId }
        ?.name
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissInput: () -> Unit = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    var showStats by remember { mutableStateOf(false) }
    var showWorkspaceFiles by remember { mutableStateOf(false) }
    LaunchedEffect(stateHolder.snapshot.selectedSessionId) {
        if (stateHolder.snapshot.selectedSessionId == null) onBack()
        else stateHolder.refreshSessionControls()
    }
    LaunchedEffect(pagerState.currentPage) { stateHolder.setTrajectoryActive(pagerState.currentPage == 1) }
    DisposableEffect(stateHolder) {
        onDispose {
            stateHolder.setTrajectoryActive(false)
            stateHolder.leaveSessionAgentPreset()
        }
    }

    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            // 页头复用 DshPageHeader：与四个 Tab 根页**完全同一几何**
            // （56dp 高、40dp 圆钮、18sp 居中标题）。
            //
            // 层级规则：详情页是**下钻页**，左侧渲染返回钮回到任务列表（= 首页）；
            // 抽屉钮放在返回钮**右侧**（用户要求 + 设计稿
            // `Docs/design/conversation-detail.html:34-36` 的左侧双钮槽位），
            // 「更多」独占右侧动作区。左右槽位由 DshPageHeader 按按钮数同源推导，
            // 因此标题仍整屏居中。
            //
            // 此前抽屉钮被塞进 `actions` 的 Row 里，而右侧槽位被写死 40dp——
            // 两个 40dp 钮重叠 63px、靠右那个被挤出屏幕（真机 uiautomator 实测，
            // 见 Docs/v1.9.7-conversation-detail-plan.md §1.1）。现在抽屉走
            // `onOpenDrawer`，进左侧槽位，不再需要手写 Row。
            DshPageHeader(
                title = title,
                onBack = {
                    dismissInput()
                    onBack()
                },
                // 副标题显示会话任务进度（「3 个任务 · 1 进行中」）。
                // 此前副标题槽位始终空着——页头为此**永久预留了 16dp 高度**
                // （DshPageHeader 的 subtitle slot），却什么也不显示。
                // 数据源与输入框上方的任务面板同源（taskSnapshot）。
                subtitle = conversationTaskSubtitle(stateHolder.snapshot.taskSnapshot?.tasks),
                onOpenDrawer = LocalDrawerOpener.current,
                actions = {
                    // 右侧只剩「更多」；ConversationMoreMenu 自带 40dp 圆钮。
                    ConversationMoreMenu(
                        canBrowseFiles = stateHolder.snapshot.selectedSessionId != null &&
                            stateHolder.gatewayState.connection == GatewayConnectionState.CONNECTED &&
                            "file-downloads" in stateHolder.gatewayState.capabilities,
                        onBrowseFiles = { showWorkspaceFiles = true },
                        // 对话 / 轨迹 折进「更多」：它们原先以分段控件形式常驻在页头下方，
                        // 一直占掉一行可视高度，而多数时间用户只看「对话」。
                        selectedPage = pagerState.currentPage,
                        onSelectPage = { target ->
                            dismissInput()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        agentPresetLabel = agentPresetDisplayName(agentPresetId, agentPresetName),
                        connection = stateHolder.gatewayState.connection
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                if (page == 0) {
                    ConversationPage(
                        stateHolder = stateHolder,
                        onPickImage = onPickImage,
                        onShowFullStats = { showStats = true },
                        onDismissInput = dismissInput
                    )
                }
                else TrajectoryPage(
                    nodes = stateHolder.trajectoryNodes,
                    isActive = pagerState.currentPage == 1
                )
            }
        }
    }
    if (showStats) {
        SessionStatsSheet(stateHolder.snapshot.statsSnapshot) { showStats = false }
    }
    if (showWorkspaceFiles) {
        WorkspaceFilesBottomSheet(stateHolder) { showWorkspaceFiles = false }
    }
}

@Composable
internal fun TopBarCircleButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Box(
        modifier = modifier.size(DshPageHeaderCircleButtonSize)
            // 表面（投影 + 圆底 + 描边）与抽屉钮共用同一个修饰符：
            // 两者现在并排出现在页头左侧，必须完全同款。
            .dshHeaderCircleButtonSurface(palette)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = palette.textPrimary
        )
    }
}

/**
 * 页头「更多」菜单：任务详情页的全部次级操作都收在这里。
 *
 * 为什么把「对话 / 轨迹」也折进来：它们原本是页头下方的常驻分段控件，**永久占掉一行
 * 可视高度**，而绝大多数时间用户只看对话。折进菜单后首屏全部留给内容。
 *
 * 菜单里带当前状态（哪个页签被选中）与连接状态——原来是直接画在页头上的，
 * 收进菜单后不能丢掉这些信息。
 */
@Composable
internal fun ConversationMoreMenu(
    canBrowseFiles: Boolean,
    onBrowseFiles: () -> Unit,
    selectedPage: Int = 0,
    onSelectPage: (Int) -> Unit = {},
    agentPresetLabel: String? = null,
    connection: GatewayConnectionState? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = dshPalette()
    Box {
        TopBarCircleButton(
            iconRes = R.drawable.ic_more_horizontal,
            description = "更多",
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(228.dp).testTag("conversation-more-menu"),
            offset = DpOffset(x = (-220).dp, y = 8.dp),
            shape = RoundedCornerShape(26.dp),
            containerColor = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(
                0.7.dp,
                palette.cardBorder
            )
        ) {
            // 视图切换：勾选当前页签，点击即切。
            ConversationMoreMenuItem(
                title = "对话",
                iconRes = R.drawable.ic_tab_tasks,
                selected = selectedPage == 0,
                testTag = "conversation-more-page-0"
            ) {
                expanded = false
                onSelectPage(0)
            }
            ConversationMoreMenuItem(
                title = "轨迹",
                iconRes = R.drawable.ic_drawer_schedule,
                selected = selectedPage == 1,
                testTag = "conversation-more-page-1"
            ) {
                expanded = false
                onSelectPage(1)
            }
            HorizontalDivider(color = palette.divider)
            ConversationMoreMenuItem(
                title = "工作区文件",
                iconRes = R.drawable.ic_folder_outline,
                enabled = canBrowseFiles,
                testTag = "conversation-more-files"
            ) {
                expanded = false
                onBrowseFiles()
            }
            // 原先画在页头右侧的信息：连接状态与 Agent 预设。收进菜单后仍然可见，
            // 否则「当前用的是哪个预设」在详情页就无处可查了。
            if (agentPresetLabel != null || connection != null) {
                HorizontalDivider(color = palette.divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connection != null) {
                        StatusIndicatorDot(
                            color = dshConnectionDotColor(connection, palette),
                            modifier = Modifier.size(7.dp),
                            glowing = connection == GatewayConnectionState.CONNECTED
                        )
                    }
                    Text(
                        text = agentPresetLabel.orEmpty(),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationMoreMenuItem(
    title: String,
    iconRes: Int,
    enabled: Boolean = true,
    selected: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val palette = dshPalette()
    DropdownMenuItem(
        text = {
            Text(
                title,
                color = if (selected) palette.primary else palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) palette.primary else palette.textPrimary
            )
        },
        // 选中态用勾号表达：否则「当前在对话还是轨迹」在菜单里读不出来。
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_menu_check),
                    contentDescription = "当前视图",
                    modifier = Modifier.size(20.dp),
                    tint = palette.primary
                )
            }
        } else null,
        modifier = Modifier.height(58.dp).then(
            if (testTag != null) Modifier.testTag(testTag) else Modifier
        ),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 20.dp),
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationPage(
    stateHolder: AndroidSharedStateHolder,
    onPickImage: () -> Unit,
    onShowFullStats: () -> Unit,
    onDismissInput: () -> Unit
) {
    val density = LocalDensity.current
    val sessionId = stateHolder.snapshot.selectedSessionId
    var bottomContentHeight by remember { mutableStateOf(160.dp) }
    var imagePreview by remember(sessionId) { mutableStateOf<ConversationImagePreviewRequest?>(null) }
    var isPinnedToBottom by remember(sessionId) { mutableStateOf(true) }
    var scrollToBottomToken by remember(sessionId) { mutableIntStateOf(0) }
    val imeIsVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeIsVisible, sessionId) {
        if (imeIsVisible) scrollToBottomToken += 1
    }
    val approval = stateHolder.snapshot.pendingApprovals.firstOrNull {
        stateHolder.snapshot.selectedSessionId == null || it.sessionId == stateHolder.snapshot.selectedSessionId
    }
    val question = stateHolder.snapshot.pendingQuestions.firstOrNull {
        stateHolder.snapshot.selectedSessionId == null || it.sessionId == stateHolder.snapshot.selectedSessionId
    }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.key(sessionId) {
            ConversationTimeline(
                stateHolder = stateHolder,
                bottomContentHeight = bottomContentHeight,
                scrollToBottomToken = scrollToBottomToken,
                onPinnedToBottomChanged = { isPinnedToBottom = it },
                onUserInteraction = onDismissInput,
                onPreviewImages = { attachments, initialIndex ->
                    imagePreview = ConversationImagePreviewRequest(attachments, initialIndex)
                }
            )
        }
        val showInitialHistoryOverlay = shouldShowInitialHistoryOverlay(
            isLoading = stateHolder.snapshot.selectedHistoryIsLoading,
            isLoadingOlder = stateHolder.snapshot.selectedHistoryIsLoadingOlder,
            hasLocalContent = stateHolder.snapshot.conversation.isNotEmpty()
        )
        AnimatedVisibility(
            visible = showInitialHistoryOverlay,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(bottom = bottomContentHeight)
                .zIndex(1f),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            HistoryLoadingOverlay(
                loadedEventCount = stateHolder.snapshot.selectedHistoryLoadedEventCount,
                totalEventCount = stateHolder.snapshot.selectedHistoryTotalEventCount
            )
        }
        ConversationBottomFade(
            modifier = Modifier.align(Alignment.BottomCenter),
            contentHeight = bottomContentHeight
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .onSizeChanged { size ->
                    bottomContentHeight = with(density) { size.height.toDp() }
                }
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(
                visible = stateHolder.snapshot.conversation.isNotEmpty() && !isPinnedToBottom,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.85f)
            ) {
                ScrollToBottomButton(
                    isGenerating = stateHolder.snapshot.sessions
                        .firstOrNull { it.id == sessionId }
                        ?.isRunning == true,
                    onClick = { scrollToBottomToken += 1 }
                )
            }
            if (approval != null) {
                ApprovalRequestCard(
                    request = approval,
                    status = stateHolder.snapshot.approvalRequestStatuses[approval.rpcId]
                        ?: com.clarklevis.dsh.shared.facade.SharedApprovalStatusSnapshot("idle"),
                    commandPreview = stateHolder.snapshot.approvalCommandPreviews[approval.rpcId],
                    details = stateHolder.snapshot.approvalDetails[approval.rpcId],
                    onDecision = { stateHolder.respondToApproval(approval.rpcId, it) }
                )
            } else {
                AnimatedHumanQuestionPanel(
                    request = question,
                    onAnswer = { answeredRequest, answers ->
                        stateHolder.answerQuestion(
                            answeredRequest.rpcId,
                            answeredRequest.sessionId,
                            answers
                        )
                    },
                    onCancel = { cancelledRequest ->
                        stateHolder.cancelQuestion(cancelledRequest.rpcId, cancelledRequest.sessionId)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Pending interaction cards replace normal composer
                        // chrome. Stats/tasks/goals must not consume height and
                        // push the approval or question actions off screen.
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionAgentPresetControl(
                                state = stateHolder.sessionAgentPreset,
                                onSelect = stateHolder::selectSessionAgentPreset,
                                onRetry = stateHolder::refreshSessionAgentPreset
                            )
                            stateHolder.snapshot.statsSnapshot?.let { snapshot ->
                                SessionStatsBanner(
                                    snapshot = snapshot,
                                    sessionId = sessionId,
                                    modifier = Modifier.weight(1f),
                                    onViewFullStats = onShowFullStats
                                )
                            }
                        }
                        TaskGoalPanels(
                            stateHolder = stateHolder,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        SlashCommandMenus(
                            stateHolder = stateHolder,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Composer(stateHolder, onPickImage, onDismissInput)
                    }
                }
            }
        }
    }
    imagePreview?.let { request ->
        ConversationImagePreviewDialog(
            request = request,
            thumbnails = stateHolder.attachmentThumbnails,
            onDismiss = { imagePreview = null }
        )
    }
}

internal fun shouldShowInitialHistoryOverlay(
    isLoading: Boolean,
    isLoadingOlder: Boolean,
    hasLocalContent: Boolean
): Boolean = isLoading && !isLoadingOlder && !hasLocalContent

internal fun historyLoadingProgressText(loadedEventCount: Int, totalEventCount: Int?): String = when {
    totalEventCount != null -> "正在加载历史记录 · $loadedEventCount/$totalEventCount"
    loadedEventCount > 0 -> "正在自动加载更早记录 · 已同步 $loadedEventCount 个事件"
    else -> "正在从 Mobile Gateway 同步会话内容…"
}

@Composable
internal fun HistoryLoadingOverlay(
    loadedEventCount: Int,
    totalEventCount: Int?
) {
    val palette = dshPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .testTag("history-loading-overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 4.dp,
                color = palette.primary,
                trackColor = palette.primary.copy(alpha = 0.20f)
            )
            Text(
                "正在加载历史记录",
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                historyLoadingProgressText(loadedEventCount, totalEventCount),
                color = palette.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConversationBottomFade(
    contentHeight: Dp,
    modifier: Modifier = Modifier
) {
    val background = dshPalette().canvas
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((contentHeight - 44.dp).coerceAtLeast(0.dp))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.3f to background,
                        1f to background
                    )
                )
            )
    )
}

@Composable
private fun ConversationTimeline(
    stateHolder: AndroidSharedStateHolder,
    bottomContentHeight: Dp,
    scrollToBottomToken: Int,
    onPinnedToBottomChanged: (Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    onPreviewImages: (List<GatewayImageAttachment>, Int) -> Unit
) {
    val latestItems = stateHolder.snapshot.conversation
    val selectedSessionId = stateHolder.snapshot.selectedSessionId
    val latestHistoryLoading = selectedSessionId in stateHolder.historyPagingSessionIds
    var items by remember(selectedSessionId) { mutableStateOf(latestItems) }
    var hasHistoryLoadingRow by remember(selectedSessionId) {
        mutableStateOf(latestHistoryLoading)
    }
    val selectedSessionIsRunning = stateHolder.snapshot.sessions
        .firstOrNull { it.id == selectedSessionId }
        ?.isRunning == true
    val displayEntries = remember(items) { makeConversationDisplayEntries(items) }
    val activeStreamingMessageId = remember(items, selectedSessionIsRunning) {
        activeStreamingAssistantMessageId(items, selectedSessionIsRunning)
    }
    val timelineEntries = remember(displayEntries, activeStreamingMessageId) {
        makeConversationTimelineEntries(displayEntries, activeStreamingMessageId)
    }
    val attachmentIdsByTimelineId = remember(timelineEntries) {
        timelineEntries.associate { entry ->
            entry.id to entry.images.mapTo(mutableSetOf()) { it.attachmentId }
        }
    }
    val hasInitialContent = timelineEntries.isNotEmpty()
    val lastTimelineIndex = (
        timelineEntries.lastIndex + if (hasHistoryLoadingRow) 1 else 0
    ).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = lastTimelineIndex
    )
    var initialPositionApplied by remember { mutableStateOf(false) }
    var isPinnedToBottom by remember { mutableStateOf(true) }
    var programmaticScrollCount by remember { mutableIntStateOf(0) }
    var timelineUpdatesPaused by remember(selectedSessionId) { mutableStateOf(false) }
    val isUserTimelineScrolling by remember(listState) {
        derivedStateOf {
            listState.isScrollInProgress && programmaticScrollCount == 0
        }
    }
    LaunchedEffect(isUserTimelineScrolling) {
        if (isUserTimelineScrolling) {
            timelineUpdatesPaused = true
        } else if (timelineUpdatesPaused) {
            // fling 结束后留一帧稳定窗口，再把滚动期间积累的 token 一次性提交给列表。
            delay(TIMELINE_STREAM_RESUME_DELAY_MILLISECONDS)
            timelineUpdatesPaused = false
        }
    }
    LaunchedEffect(
        latestItems,
        latestHistoryLoading,
        timelineUpdatesPaused,
        isUserTimelineScrolling
    ) {
        if (!timelineUpdatesPaused && !isUserTimelineScrolling) {
            items = latestItems
            hasHistoryLoadingRow = latestHistoryLoading
        }
    }
    val markdownPreloader = rememberDshMarkdownPreloader()
    // 预取 effect 只能以「结构」为 key。此前用 timelineEntries 本身作 key，而它每个 token
    // 都是新实例，导致每 token 取消并重启 snapshotFlow、并重置 distinctUntilChanged 状态。
    // 现在只依赖行数与预取器，最新列表内容经 rememberUpdatedState 读取。
    val latestTimelineEntries by rememberUpdatedState(timelineEntries)
    val latestActiveStreamingMessageId by rememberUpdatedState(activeStreamingMessageId)
    val timelineEntryCount = timelineEntries.size
    LaunchedEffect(listState, timelineEntryCount, markdownPreloader) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: latestTimelineEntries.lastIndex
            val last = visible.lastOrNull()?.index ?: latestTimelineEntries.lastIndex
            first to last
        }
            .distinctUntilChanged()
            .collectLatest { (first, last) ->
                val entries = latestTimelineEntries
                val start = (first - MARKDOWN_PREFETCH_ROWS).coerceAtLeast(0)
                val end = (last + MARKDOWN_PREFETCH_ROWS).coerceAtMost(entries.lastIndex)
                if (start <= end) {
                    markdownPreloader.preload(
                        entries.subList(start, end + 1)
                            .filterIsInstance<ConversationTimelineEntry.AssistantMarkdown>()
                            .filterNot { it.messageId == latestActiveStreamingMessageId }
                            .map(ConversationTimelineEntry.AssistantMarkdown::markdown)
                    )
                }
            }
    }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val targetHeight = with(density) { 240.dp.roundToPx() }
    LaunchedEffect(windowInfo.containerSize.width, targetHeight) {
        stateHolder.updateThumbnailTargetSize(windowInfo.containerSize.width, targetHeight)
    }
    // 附件可见性 effect：**必须与上面的预取 effect 一样只以「结构」为 key**。
    //
    // 此前 key 是 `items`，而它每个流式 token 都是新实例（快照按 32ms 节奏重建，
    // 见 AndroidSharedStateHolder.kt:2579），于是这条 effect 每秒被取消并重启约 31 次，
    // 每次都把 distinctUntilChanged 状态清零、必然重新回调一次
    // `updateVisibleAttachments`（该方法会展开整个会话的图片集合并对两个 LRU 做
    // retainKeys / mapKeys，见 AndroidSharedStateHolder.kt:1917-1946、2426-2437、2545-2553）。
    //
    // 现在只依赖「可见行数 + 附件映射」这两个结构性输入；映射本身经
    // rememberUpdatedState 读取，不再作为 key。
    val latestAttachmentIdsByTimelineId by rememberUpdatedState(attachmentIdsByTimelineId)
    LaunchedEffect(listState, timelineEntryCount) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.flatMapTo(mutableSetOf()) { visible ->
                latestAttachmentIdsByTimelineId[visible.key.toString()].orEmpty()
            }
        }.distinctUntilChanged().collect(stateHolder::updateVisibleAttachments)
    }
    LaunchedEffect(hasInitialContent) {
        if (!initialPositionApplied && hasInitialContent) {
            programmaticScrollCount += 1
            try {
                listState.scrollToTimelineBottom(lastTimelineIndex, animated = false)
                initialPositionApplied = true
            } finally {
                programmaticScrollCount -= 1
            }
        }
    }
    val hasStreamingItem = selectedSessionIsRunning
    LaunchedEffect(
        timelineEntries.size,
        items.lastOrNull()?.text?.length,
        initialPositionApplied,
        hasStreamingItem
    ) {
        if (initialPositionApplied && isPinnedToBottom && timelineEntries.isNotEmpty()) {
            programmaticScrollCount += 1
            try {
                listState.scrollToTimelineBottom(
                    lastTimelineIndex,
                    animated = !hasStreamingItem
                )
            } finally {
                programmaticScrollCount -= 1
            }
        }
    }
    LaunchedEffect(scrollToBottomToken) {
        if (scrollToBottomToken > 0 && timelineEntries.isNotEmpty()) {
            programmaticScrollCount += 1
            try {
                listState.scrollToTimelineBottom(lastTimelineIndex, animated = true)
            } finally {
                programmaticScrollCount -= 1
            }
        }
    }
    LaunchedEffect(listState, initialPositionApplied) {
        if (!initialPositionApplied) return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.isPinnedToBottom(),
                programmaticScrollCount > 0
            )
        }
            .distinctUntilChanged()
            .collect { (isScrolling, pinned, isProgrammaticScroll) ->
                // A new streamed row briefly makes the old last row stop being the
                // final item before auto-scroll runs. Only a real user scroll may
                // unpin; automatic movement must not flash the jump button.
                if (shouldPublishPinnedState(isScrolling, pinned, isProgrammaticScroll)) {
                    isPinnedToBottom = pinned
                    onPinnedToBottomChanged(pinned)
                }
            }
    }
    LaunchedEffect(listState, stateHolder.snapshot.selectedHistoryHasMore, initialPositionApplied) {
        if (!initialPositionApplied) return@LaunchedEffect
        snapshotFlow {
            HistoryPagingGestureState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                isScrollInProgress = listState.isScrollInProgress,
                lastScrolledBackward = listState.lastScrolledBackward,
                isProgrammaticScroll = programmaticScrollCount > 0
            )
        }
            .distinctUntilChanged()
            .collect { gesture ->
                if (
                    shouldLoadOlderHistory(gesture) &&
                    stateHolder.snapshot.selectedHistoryHasMore
                ) {
                    stateHolder.loadOlderHistory()
                }
            }
    }
    val palette = dshPalette()
    Box(
        Modifier
            .fillMaxSize()
            .testTag("conversation-timeline")
            .pointerInput(onUserInteraction) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    onUserInteraction()
                }
            }
    ) {
        if (items.isEmpty() && !stateHolder.snapshot.selectedHistoryIsLoading) {
            val error = stateHolder.snapshot.selectedHistoryError
            if (error == null) {
                EmptyConversation(Modifier.align(Alignment.Center).padding(bottom = 150.dp))
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp).padding(bottom = 150.dp)
                        .testTag("history-load-failure"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "历史记录加载失败",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (error.contains("refuses this format")) {
                            "Host 无法读取旧格式会话，请修复或升级 Host 后重试。原始记录未修改。"
                        } else error,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 6
                    )
                    TextButton(onClick = stateHolder::reloadSelectedHistory) {
                        Text("重新加载历史", color = palette.primary)
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp)
                // 滚动容器的独立锚点：外层 Box 也叫 conversation-timeline，但语义树里
                // 带滚动动作的是这个 LazyColumn。设备测试要「确定性地滚到某条消息」
                // 必须落在真正可滚动的节点上（见 ConversationKeyboardDeviceTest）。
                .testTag("conversation-timeline-list")
                .alpha(if (hasInitialContent && !initialPositionApplied) 0f else 1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = bottomContentHeight + 22.dp
            )
        ) {
            if (hasHistoryLoadingRow) {
                item("history-loading") {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = palette.primary
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在加载更早记录…", fontSize = 12.sp, color = palette.textSecondary)
                    }
                }
            }
            items(
                items = timelineEntries,
                key = ConversationTimelineEntry::id,
                contentType = ConversationTimelineEntry::contentType
            ) { entry ->
                when (entry) {
                    is ConversationTimelineEntry.Display -> when (val display = entry.entry) {
                        is ConversationDisplayEntry.Message -> ConversationRow(
                            item = display.item,
                            thumbnails = stateHolder.attachmentThumbnails,
                            attachmentStates = stateHolder.attachmentStates,
                            onRetryAttachment = stateHolder::retryAttachment,
                            onPreviewImages = onPreviewImages
                        )
                        is ConversationDisplayEntry.Process -> ConversationProcessRow(display.group)
                        is ConversationDisplayEntry.Question -> {
                            // 关联待答请求：优先按题目 id 集合匹配，回落「本 session 最后一个待答提问」。
                            val pending = stateHolder.snapshot.pendingQuestions.firstOrNull { request ->
                                request.sessionId == selectedSessionId &&
                                    matchesQuestionCall(request.questions, display.call.text)
                            } ?: stateHolder.snapshot.pendingQuestions
                                .lastOrNull { it.sessionId == selectedSessionId }
                            AskQuestionToolCard(
                                request = pending,
                                callArguments = display.call.text,
                                result = display.result,
                                isSubmitting = false,
                                onAnswer = { answers ->
                                    pending?.let {
                                        stateHolder.answerQuestion(it.rpcId, it.sessionId, answers)
                                    }
                                },
                                onCancel = { pending?.let { stateHolder.cancelQuestion(it.rpcId, it.sessionId) } }
                            )
                        }
                    }
                    is ConversationTimelineEntry.AssistantHeader -> AssistantMessageHeader(
                        item = entry.item,
                        thumbnails = stateHolder.attachmentThumbnails,
                        states = stateHolder.attachmentStates,
                        onRetry = stateHolder::retryAttachment
                    )
                    is ConversationTimelineEntry.AssistantMarkdown -> DshLazyMarkdownText(
                        markdown = entry.markdown,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    is ConversationTimelineEntry.AssistantFooter -> Box(
                        Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 12.dp)
                    ) {
                        CopyButton(entry.text)
                    }
                }
            }
        }
    }
}

private const val TIMELINE_STREAM_RESUME_DELAY_MILLISECONDS = 80L
private const val MARKDOWN_PREFETCH_ROWS = 24

internal data class HistoryPagingGestureState(
    val firstVisibleItemIndex: Int,
    val isScrollInProgress: Boolean,
    val lastScrolledBackward: Boolean,
    val isProgrammaticScroll: Boolean
)

internal fun shouldLoadOlderHistory(gesture: HistoryPagingGestureState): Boolean =
    gesture.firstVisibleItemIndex <= 2 &&
        gesture.isScrollInProgress &&
        gesture.lastScrolledBackward &&
        !gesture.isProgrammaticScroll

private fun androidx.compose.foundation.lazy.LazyListState.isPinnedToBottom(): Boolean {
    return isTimelinePinnedToBottom(
        totalItems = layoutInfo.totalItemsCount,
        canScrollForward = canScrollForward
    )
}

internal fun timelineBottomScrollDelta(
    lastItemOffset: Int,
    lastItemSize: Int,
    viewportEndOffset: Int,
    afterContentPadding: Int
): Float = (
    lastItemOffset + lastItemSize + afterContentPadding - viewportEndOffset
).coerceAtLeast(0).toFloat()

internal suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToTimelineBottom(
    lastItemIndex: Int,
    animated: Boolean
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == lastItemIndex }) {
        if (animated) animateScrollToItem(lastItemIndex) else scrollToItem(lastItemIndex)
    }
    fun remainingDistance(): Float {
        val lastItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastItemIndex }
            ?: return 0f
        return timelineBottomScrollDelta(
            lastItemOffset = lastItem.offset,
            lastItemSize = lastItem.size,
            viewportEndOffset = layoutInfo.viewportEndOffset,
            afterContentPadding = layoutInfo.afterContentPadding
        )
    }
    val distance = remainingDistance()
    if (distance > 0f) {
        if (animated) animateScrollBy(distance) else scrollBy(distance)
    }
    // The last row can remeasure while Markdown, images, or the composer are
    // settling. End with an exact correction so the list has no forward range.
    val correction = remainingDistance()
    if (correction > 0f) scrollBy(correction)
}

internal fun isTimelinePinnedToBottom(
    totalItems: Int,
    canScrollForward: Boolean
): Boolean = totalItems == 0 || !canScrollForward

internal fun shouldPublishPinnedState(
    isScrolling: Boolean,
    pinned: Boolean,
    isProgrammaticScroll: Boolean
): Boolean = pinned || (isScrolling && !isProgrammaticScroll)

@Composable
internal fun ScrollToBottomButton(isGenerating: Boolean, onClick: () -> Unit) {
    val palette = dshPalette()
    val shape = CircleShape
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(bottom = 4.dp).size(48.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 12.dp,
                    spread = 0.dp,
                    color = palette.floatingShadow,
                    offset = DpOffset(0.dp, 4.dp)
                )
            )
            .semantics {
                contentDescription = if (isGenerating) "正在生成，滚动到最新消息" else "滚动到最新消息"
            }
            .testTag("scroll-to-bottom"),
        shape = shape,
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.7.dp,
            palette.cardBorder
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = palette.primary
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = palette.textPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    val palette = dshPalette()
    Column(modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhaleIcon(Modifier.width(52.dp).height(39.dp))
        Text("操作远端 DSH Agent", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "发送任务后，工具调用、推理进度和最终回复会通过 Mobile Gateway 实时返回。",
            color = palette.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    stateHolder: AndroidSharedStateHolder,
    onPickImage: () -> Unit,
    onDismissInput: () -> Unit
) {
    val palette = dshPalette()
    val shape = DshCardCornerRadius
    val shadowColor = palette.floatingShadow
    // 主操作色块上的前景：浅色主题主色为深蓝，用白；深色主题主色偏亮，改用深色文字保证对比度。
    val primaryActionForeground = if (palette.isDark) palette.canvas else Color.White
    val keyboardController = LocalSoftwareKeyboardController.current
    val queueEditFocusRequester = remember { FocusRequester() }
    var observedQueueEditCount by remember { mutableStateOf(stateHolder.queueDraftRestoreCount) }
    LaunchedEffect(stateHolder.queueDraftRestoreCount) {
        if (stateHolder.queueDraftRestoreCount > observedQueueEditCount) {
            observedQueueEditCount = stateHolder.queueDraftRestoreCount
            queueEditFocusRequester.requestFocus()
        }
    }
    var inputIsFocused by remember { mutableStateOf(false) }
    var observedSuccessfulSendCount by remember(stateHolder) {
        mutableStateOf(stateHolder.successfulMessageSendCount)
    }
    LaunchedEffect(inputIsFocused) {
        if (inputIsFocused) keyboardController?.show() else keyboardController?.hide()
    }
    LaunchedEffect(stateHolder.successfulMessageSendCount) {
        if (stateHolder.successfulMessageSendCount > observedSuccessfulSendCount) {
            observedSuccessfulSendCount = stateHolder.successfulMessageSendCount
            onDismissInput()
        }
    }
    var inputValue by remember(stateHolder) {
        mutableStateOf(
            TextFieldValue(
                text = stateHolder.messageDraft,
                selection = TextRange(stateHolder.messageDraft.length)
            )
        )
    }
    LaunchedEffect(stateHolder.messageDraft) {
        if (inputValue.text != stateHolder.messageDraft) {
            inputValue = TextFieldValue(
                text = stateHolder.messageDraft,
                selection = TextRange(stateHolder.messageDraft.length)
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (stateHolder.selectedQueueItems.isEmpty()) 0.dp else (-24).dp)
    ) {
        AnimatedVisibility(
            visible = stateHolder.selectedQueueItems.isNotEmpty(),
            enter = expandVertically(tween(280), expandFrom = Alignment.Bottom) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120)),
            modifier = Modifier.fillMaxWidth()
        ) {
            QueueDock(stateHolder)
        }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 0.dp,
                        color = shadowColor,
                        offset = DpOffset(x = 0.dp, y = 3.dp)
                    )
            ),
            shape = shape,
            color = palette.surface,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, palette.cardBorder)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stateHolder.preparedImages.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stateHolder.preparedImages.size) { index ->
                            val image = stateHolder.preparedImages[index]
                            key(image.outgoing.base64Data) {
                                PreparedImagePreview(image) { stateHolder.removePreparedImage(index) }
                            }
                        }
                    }
                }
                val commandToken = stateHolder.slashCommands.commandToken
                val commandHint = stateHolder.slashCommands.argumentHint
                BasicTextField(
                    value = inputValue,
                    onValueChange = {
                        inputValue = it
                        stateHolder.messageDraft = it.text
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp)
                        .heightIn(min = 38.dp, max = 120.dp)
                        .onFocusChanged { inputIsFocused = it.isFocused }
                        .focusRequester(queueEditFocusRequester)
                        .testTag("composer-input"),
                    // 字号走全局正文 token（15sp），不再用 bodyLarge（16sp）：
                    // 设计稿要求占位 15sp，且详情页此前是**唯一**比列表页正文大一号的页面
                    // （见 Docs/v1.9.7-conversation-detail-plan.md §1.3）。
                    textStyle = TextStyle(
                        fontSize = DshBodyFontSize,
                        color = palette.textPrimary,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    cursorBrush = SolidColor(palette.primary),
                    visualTransformation = slashCommandVisualTransformation(commandToken, palette.primary),
                    decorationBox = { field ->
                        Box {
                            if (stateHolder.messageDraft.isEmpty()) {
                                Text(
                                    "描述你想要构建的内容",
                                    color = palette.textTertiary,
                                    fontSize = DshBodyFontSize,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            } else if (
                                commandToken != null &&
                                commandHint != null &&
                                stateHolder.messageDraft.trimEnd() == commandToken
                            ) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = Color.Transparent)) {
                                            append(stateHolder.messageDraft)
                                        }
                                        if (!stateHolder.messageDraft.last().isWhitespace()) append(" ")
                                        withStyle(
                                            SpanStyle(
                                                color = palette.textTertiary
                                            )
                                        ) {
                                            append(commandHint)
                                        }
                                    },
                                    fontSize = DshBodyFontSize,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                            field()
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ComposerIconButton(R.drawable.ic_photo_stack, "添加图片", onPickImage)
                    PermissionControl(stateHolder, Modifier.width(82.dp))
                    Spacer(Modifier.width(2.dp))
                    ModelControl(
                        stateHolder = stateHolder,
                        modifier = Modifier.weight(1f)
                    )
                    ContextUsageRing(stateHolder)
                    // 发送 / 停止：**单一状态源**。
                    //
                    // 此前「是否可点」与「多透明」各看一个信号——可点性看 canSend，
                    // 透明度看 composerHasContent——于是会出现「不透明但点不动」
                    // （有草稿但未连接/预设未就绪）和「半透明却能点」两种自相矛盾的状态。
                    //
                    // 现在两者都由同一个 `sendButtonEnabled` 决定；不可点时用
                    // palette.disabledPrimary 实色（浅蓝），而不是把主色降透明度——
                    // alpha 叠在画布上会「变淡与变脏分不开」，这是
                    // DshTheme.kt:207-217 已经记录过的既有决策（DshFab 同款）。
                    val stopMode = stateHolder.showsSessionStopButton
                    val sendButtonEnabled = if (stopMode) {
                        stateHolder.canCancelSelectedSession
                    } else {
                        stateHolder.canSend
                    }
                    // 禁用态前景不能沿用白色：白字压在浅蓝 #BBD1FB 上只有约 1.5:1。
                    // 与 DshFab（DshGroupedList.kt:318-325）取同一套推导——
                    // 浅色主题禁用底是浅蓝，用主色画图标读作「同一个按钮」；
                    // 深色主题禁用底是暗蓝，白图标对比度最优。
                    val sendButtonForeground = when {
                        sendButtonEnabled -> primaryActionForeground
                        palette.isDark -> palette.onPrimary
                        else -> palette.primary
                    }
                    Box(
                        Modifier.size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (sendButtonEnabled) palette.primary else palette.disabledPrimary
                            )
                            .clickable(
                                role = Role.Button,
                                enabled = sendButtonEnabled,
                                onClick = if (stopMode) {
                                    stateHolder::cancelSelectedSession
                                } else {
                                    { stateHolder.sendMessage() }
                                }
                            )
                            .semantics {
                                contentDescription = if (stopMode) "停止生成" else "发送"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (stopMode) {
                            Box(
                                Modifier.size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(sendButtonForeground)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_up),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = sendButtonForeground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparedImagePreview(image: AndroidPreparedImage, onRemove: () -> Unit) {
    val preview by produceState<PreparedImagePreviewState>(PreparedImagePreviewState.Loading, image.outgoing.base64Data) {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { Base64.decode(image.outgoing.base64Data, Base64.NO_WRAP) }.getOrNull()
        }
        val bitmap = bytes?.let { AndroidAttachmentThumbnailer().decode(it, 256, 256)?.asImageBitmap() }
        value = bitmap?.let(PreparedImagePreviewState::Ready) ?: PreparedImagePreviewState.Unavailable
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width = 82.dp, height = 72.dp)
            .clip(shape)
            .background(dshPalette().surfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        when (val state = preview) {
            is PreparedImagePreviewState.Ready -> Image(
                bitmap = state.bitmap,
                contentDescription = image.outgoing.name ?: "待发送图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            PreparedImagePreviewState.Loading -> Text("正在加载图片…", fontSize = 10.sp)
            PreparedImagePreviewState.Unavailable -> Text("无法预览图片", fontSize = 10.sp)
        }
        Text(
            "×",
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = "移除图片" },
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

private sealed interface PreparedImagePreviewState {
    data object Loading : PreparedImagePreviewState
    data class Ready(val bitmap: ImageBitmap) : PreparedImagePreviewState
    data object Unavailable : PreparedImagePreviewState
}

@Composable
private fun QueueDock(stateHolder: AndroidSharedStateHolder) {
    QueueDock(
        sessionId = stateHolder.snapshot.selectedSessionId,
        items = stateHolder.selectedQueueItems,
        pendingItemId = stateHolder.queueState.pendingItemId,
        enabled = stateHolder.gatewayState.connection == GatewayConnectionState.CONNECTED &&
            "queue-control" in stateHolder.gatewayState.capabilities,
        running = stateHolder.snapshot.sessions.firstOrNull { it.id == stateHolder.snapshot.selectedSessionId }?.isRunning == true,
        onAction = stateHolder::updateQueuedMessage
    )
}

@Composable
internal fun QueueDock(
    sessionId: String?,
    items: List<com.clarklevis.dsh.shared.facade.SharedQueueItem>,
    pendingItemId: String?,
    enabled: Boolean,
    running: Boolean,
    onAction: (String, String) -> Unit
) {
    var expanded by remember(sessionId) { mutableStateOf(false) }
    if (items.isEmpty()) return
    val palette = dshPalette()
    val actionsEnabled = enabled && pendingItemId == null
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        Modifier.fillMaxWidth()
            .dropShadow(
                shape = shape,
                shadow = Shadow(radius = 8.dp, color = palette.floatingShadow, offset = DpOffset(0.dp, 2.dp))
            )
            .background(palette.surfaceMuted, shape)
            // 底部延伸到输入框圆角后方，按钮保留在输入框外。
            .padding(bottom = 24.dp)
    ) {
        if (items.size > 1) {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_question_bubble),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                    tint = palette.textSecondary
                )
                Text(
                    "排队消息 · ${items.size}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                Icon(
                    painterResource(if (expanded) R.drawable.ic_question_chevron_down else R.drawable.ic_chevron_up),
                    contentDescription = if (expanded) "收起排队消息" else "展开排队消息",
                    modifier = Modifier.size(18.dp),
                    tint = palette.textSecondary
                )
            }
        }
        if (items.size == 1 || expanded) {
            Column(Modifier.heightIn(max = 192.dp).verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    key(item.id) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (items.size == 1) {
                                Icon(
                                    painterResource(R.drawable.ic_question_bubble),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                                    tint = palette.textSecondary
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.preview,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary
                                )
                                if (item.attachmentCount > 0) {
                                    Text(
                                        "${item.attachmentCount} 个附件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textTertiary
                                    )
                                }
                            }
                            if (pendingItemId == item.id) {
                                CircularProgressIndicator(
                                    Modifier.padding(10.dp).size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.primary
                                )
                            } else {
                                QueueAction(R.drawable.ic_pencil_line, "编辑排队消息", actionsEnabled && item.editable) { onAction(item.id, "edit") }
                                QueueAction(R.drawable.ic_trash, "删除排队消息", actionsEnabled) { onAction(item.id, "remove") }
                                QueueAction(R.drawable.ic_arrow_up, "立即插话", actionsEnabled && running) { onAction(item.id, "steer") }
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                thickness = 0.5.dp,
                                color = palette.divider
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).alpha(if (enabled) 1f else 0.38f).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = dshPalette().textSecondary)
    }
}

@Composable
private fun SlashCommandMenus(
    stateHolder: AndroidSharedStateHolder,
    modifier: Modifier = Modifier
) {
    val state = stateHolder.slashCommands
    if (!state.catalogVisible && state.optionsCommand == null) return
    val palette = dshPalette()
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 14.dp,
                    spread = 0.dp,
                    color = palette.floatingShadow,
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                )
            ),
        shape = shape,
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            palette.cardBorder
        )
    ) {
        val optionsCommand = state.optionsCommand
        // 测量实际内容高度，短列表随内容收缩，超过面板上限时滚动。
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 7.dp)
        ) {
            if (optionsCommand != null) {
                Text(
                    text = "/${optionsCommand.name}",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
            if (state.catalogLoading || state.optionsLoading || state.selectionLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.primary)
            }
            if (optionsCommand == null) {
                state.filteredGroups.forEach { group ->
                    Text(
                        text = group.title,
                        color = palette.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    group.items.forEach { command ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { stateHolder.selectSlashCatalogItem(command.stableId) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(command.name, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                command.description,
                                color = palette.textTertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp).weight(1f)
                            )
                            if (command.ui.kind == "select") {
                                Text("›", color = palette.textTertiary)
                            }
                        }
                    }
                }
            } else {
                state.options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !state.selectionLoading) {
                                stateHolder.selectSlashCommandOption(option.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            (option.description ?: option.detail)?.let {
                                Text(
                                    it,
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        if (option.selected == true) {
                            Text("✓", color = palette.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun slashCommandVisualTransformation(
    commandToken: String?,
    accentColor: Color
): VisualTransformation {
    if (commandToken == null) return VisualTransformation.None
    return VisualTransformation { source ->
        val highlightsCommand = source.text == commandToken || source.text.startsWith("$commandToken ")
        val transformed = buildAnnotatedString {
            append(source)
            if (highlightsCommand) {
                addStyle(
                    SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold),
                    start = 0,
                    end = commandToken.length
                )
            }
        }
        TransformedText(transformed, OffsetMapping.Identity)
    }
}

@Composable
private fun ComposerIconButton(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = dshPalette().textPrimary
        )
    }
}

@Composable
internal fun SessionAgentPresetControl(
    state: com.clarklevis.dsh.shared.facade.SharedSessionAgentPresetSnapshot,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (!state.visible) return
    var expanded by remember(state.sessionId) { mutableStateOf(false) }
    val current = state.presets.firstOrNull { it.id == state.agentPreset }
    val retry = !state.loading && !state.saving && (!state.known || !state.catalogLoaded)
    val shape = RoundedCornerShape(13.dp)
    val palette = dshPalette()
    val shadowColor = palette.floatingShadow
    Box {
        Surface(
            shape = shape,
            color = palette.surface,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, palette.cardBorder),
            modifier = Modifier.testTag("session-agent-preset-capsule")
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 10.dp,
                        spread = 0.dp,
                        color = shadowColor,
                        offset = DpOffset(0.dp, 4.dp)
                    )
                )
                .clip(shape)
                .clickable(enabled = state.canSelect || (retry && state.connected)) {
                    if (retry) onRetry() else expanded = true
                }
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_agent_mode), contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = palette.textPrimary
                )
                Text(
                    when {
                        state.saving -> "切换中…"
                        state.agentPreset != null -> current?.name?.takeIf(String::isNotBlank)
                            ?: agentPresetDisplayName(state.agentPreset)
                        state.loading -> "读取模式…"
                        else -> "重试模式"
                    },
                    modifier = Modifier.widthIn(max = 132.dp),
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painterResource(R.drawable.ic_question_chevron_down), contentDescription = "选择会话模式",
                    modifier = Modifier.size(10.dp), tint = palette.textTertiary
                )
            }
        }
        ComposerPopupMenu(
            expanded = expanded && state.canSelect,
            onDismissRequest = { expanded = false },
            width = 292.dp, alignment = Alignment.BottomStart, horizontalCompensation = (-20).dp
        ) {
            state.presets.forEach { preset ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("session-agent-preset-${preset.id}"),
                    enabled = preset.broken != true,
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                preset.name?.takeIf(String::isNotBlank) ?: agentPresetDisplayName(preset.id),
                                color = palette.textPrimary,
                                fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (preset.broken == true) preset.brokenReason ?: "模式不可用"
                                else agentPresetCompactDescription(preset.id, preset.description),
                                fontSize = 12.sp, color = palette.textSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    trailingIcon = {
                        if (preset.id == state.agentPreset) Icon(
                            painterResource(R.drawable.ic_menu_check), contentDescription = "已选择",
                            modifier = Modifier.size(18.dp),
                            tint = palette.primary
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    onClick = { expanded = false; onSelect(preset.id) }
                )
            }
        }
    }
}

@Composable
private fun PermissionControl(
    stateHolder: AndroidSharedStateHolder,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = dshPalette()
    val selected = stateHolder.snapshot.permissions?.currentValue
        ?: stateHolder.snapshot.permissionDefault
    val options = stateHolder.snapshot.permissions?.options.orEmpty()
    val enabled = stateHolder.snapshot.selectedSessionId != null && options.isNotEmpty()
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = painterResource(permissionIcon(selected)),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.textSecondary
            )
            Text(
                options.firstOrNull { it.value == selected }?.name
                    ?: stateHolder.snapshot.permissionDefaultOptions.firstOrNull { it.value == selected }?.name
                    ?: permissionTitle(selected),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ComposerPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = 224.dp,
            alignment = Alignment.BottomStart,
            horizontalCompensation = (-28).dp
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.name,
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(
                                if (option.value == selected) R.drawable.ic_menu_check
                                else permissionIcon(option.value)
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (option.value == selected) palette.primary else palette.textSecondary
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    onClick = {
                        stateHolder.setSessionPermission(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ComposerMenuSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 4.dp),
        color = dshPalette().textTertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ComposerMenuItem(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = dshPalette()
    DropdownMenuItem(
        text = {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(
                    if (selected) R.drawable.ic_menu_check else R.drawable.ic_menu_circle
                ),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) palette.primary else palette.textSecondary
            )
        },
        contentPadding = PaddingValues(horizontal = 18.dp),
        onClick = onClick
    )
}

@Composable
private fun ModelControl(
    stateHolder: AndroidSharedStateHolder,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val effort = currentModelEffortTitle(stateHolder)
    val selection = currentModelSelection(stateHolder)
    val groups = stateHolder.snapshot.modelCatalog?.groups.orEmpty()
    val enabled = stateHolder.snapshot.selectedSessionId != null &&
        groups.isNotEmpty() && stateHolder.snapshot.modelCatalog?.routable != false
    val palette = dshPalette()
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                modelTitle(stateHolder),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            effort?.let {
                ReasoningEffortTag(it)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevrons_vertical),
                contentDescription = null,
                modifier = Modifier.size(9.dp),
                tint = palette.textTertiary
            )
        }
        ComposerPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = 286.dp,
            alignment = Alignment.BottomEnd,
            horizontalCompensation = 28.dp
        ) {
            val efforts = currentModelEfforts(stateHolder)
            if (efforts.isNotEmpty()) {
                ComposerMenuSectionTitle("推理等级")
                efforts.forEach { option ->
                    ComposerMenuItem(
                        title = option.name,
                        selected = option.id == selection?.reasoningEffort
                    ) {
                        selection ?: return@ComposerMenuItem
                        stateHolder.selectModel(selection.provider, selection.model, option.id)
                        expanded = false
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    color = palette.divider
                )
            }
            groups.forEach { group ->
                ComposerMenuSectionTitle(group.name)
                group.models.forEach { model ->
                    ComposerMenuItem(
                        title = model.name,
                        selected = group.id == selection?.provider && model.id == selection.model
                    ) {
                        val retainedEffort = selection?.reasoningEffort?.takeIf { current ->
                            model.reasoning?.efforts.orEmpty().any { it.id == current }
                        }
                        stateHolder.selectModel(
                            group.id,
                            model.id,
                            retainedEffort ?: model.reasoning?.defaultEffort
                        )
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReasoningEffortTag(title: String) {
    Text(
        title,
        modifier = Modifier
            .testTag("reasoning-effort-tag")
            .background(
                DshColors.Purple.copy(alpha = 0.16f),
                RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = dshPalette().textPrimary,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun ComposerPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp,
    alignment: Alignment,
    horizontalCompensation: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    // 退出动画结束后才移除 Popup，避免关闭时直接消失。
    if (!visibility.currentState && !visibility.targetState) return
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxSurfaceHeight = (windowHeight - 180.dp).coerceIn(240.dp, 560.dp)
    val horizontalCompensationPx = with(density) { horizontalCompensation.roundToPx() }
    val screenMarginPx = with(density) { 8.dp.roundToPx() }
    val anchorGapPx = with(density) { 4.dp.roundToPx() }
    val positionProvider = remember(
        alignment,
        horizontalCompensationPx,
        screenMarginPx,
        anchorGapPx
    ) {
        ComposerPopupPositionProvider(
            alignToEnd = alignment == Alignment.BottomEnd,
            horizontalCompensationPx = horizontalCompensationPx,
            screenMarginPx = screenMarginPx,
            anchorGapPx = anchorGapPx
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, clippingEnabled = true)
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(180)) + scaleIn(
                animationSpec = tween(220),
                initialScale = 0.94f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (alignment == Alignment.BottomEnd) 1f else 0f, 1f
                )
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                animationSpec = tween(140),
                targetScale = 0.96f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (alignment == Alignment.BottomEnd) 1f else 0f, 1f
                )
            )
        ) {
            Box(Modifier.padding(20.dp)) {
                val popupShape = RoundedCornerShape(24.dp)
                Surface(
                    modifier = Modifier.width(width).heightIn(max = maxSurfaceHeight).dropShadow(
                        shape = popupShape,
                        shadow = Shadow(
                            radius = 18.dp,
                            spread = 0.dp,
                            color = dshPalette().floatingShadow,
                            offset = DpOffset(x = 0.dp, y = 8.dp)
                        )
                    ),
                    shape = popupShape,
                    color = dshPalette().surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        content = content
                    )
                }
            }
        }
    }
}

private class ComposerPopupPositionProvider(
    private val alignToEnd: Boolean,
    private val horizontalCompensationPx: Int,
    private val screenMarginPx: Int,
    private val anchorGapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maximumX = (windowSize.width - popupContentSize.width - screenMarginPx).coerceAtLeast(0)
        val minimumX = screenMarginPx.coerceAtMost(maximumX)
        val preferredX = if (alignToEnd) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        } + horizontalCompensationPx

        val maximumY = (windowSize.height - popupContentSize.height - screenMarginPx).coerceAtLeast(0)
        val minimumY = screenMarginPx.coerceAtMost(maximumY)
        val aboveAnchor = anchorBounds.top - popupContentSize.height - anchorGapPx
        val belowAnchor = anchorBounds.bottom + anchorGapPx
        val preferredY = when {
            aboveAnchor >= minimumY -> aboveAnchor
            belowAnchor <= maximumY -> belowAnchor
            anchorBounds.top >= windowSize.height - anchorBounds.bottom -> aboveAnchor
            else -> belowAnchor
        }

        return IntOffset(
            x = preferredX.coerceIn(minimumX, maximumX),
            y = preferredY.coerceIn(minimumY, maximumY)
        )
    }
}

@Composable
private fun ContextUsageRing(stateHolder: AndroidSharedStateHolder) {
    val palette = dshPalette()
    val snapshot = stateHolder.snapshot.contextSnapshot
    var expanded by remember(stateHolder.snapshot.selectedSessionId) { mutableStateOf(false) }
    val pressure = snapshot?.pressure
    val contextWindow = pressure?.contextWindow
    val progress = if (contextWindow != null && contextWindow > 0) {
        (pressure.pressureTokens ?: 0).toFloat() / contextWindow
    } else 0f
    Box(
        Modifier.size(32.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                expanded = true
                stateHolder.refreshContextUsage()
            }
            .semantics { contentDescription = "查看上下文用量" }
            .testTag("context-usage-ring"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(18.dp),
            strokeWidth = 3.dp,
            color = palette.primary,
            trackColor = palette.surfaceMuted
        )
        ComposerPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = 300.dp,
            alignment = Alignment.BottomEnd,
            horizontalCompensation = 0.dp
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp).testTag("context-usage-popover"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上下文已用", color = palette.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (contextWindow != null && contextWindow > 0 && pressure.pressureTokens != null)
                            "${kotlin.math.round(progress.coerceIn(0f, 1f) * 100).toInt()}%" else "—",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${contextTokenCount(pressure?.pressureTokens)} / ${contextTokenCount(contextWindow)}",
                        color = palette.textTertiary,
                        fontSize = 12.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.primary,
                    trackColor = palette.surfaceMuted
                )
                val breakdown = snapshot?.breakdown
                val usage = snapshot?.tokenUsage
                if (breakdown != null) {
                    ContextUsageRow("系统提示词", breakdown.systemTokens, Color.Gray)
                    ContextUsageRow("工具", breakdown.toolsTokens, Color(0xFF8055CF))
                    ContextUsageRow("对话消息", breakdown.messageTokens, palette.primary)
                } else if (usage != null) {
                    ContextUsageRow("未缓存输入", usage.uncachedInputTokens, palette.primary)
                    ContextUsageRow("缓存读取", usage.cacheReadTokens, Color(0xFF8055CF))
                    ContextUsageRow("模型输出", usage.outputTokens, Color(0xFFE18B38))
                } else {
                    Text("暂无上下文用量明细", color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ContextUsageRow(title: String, tokens: Int?, color: Color) {
    val palette = dshPalette()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(title, color = palette.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(contextTokenCount(tokens), color = palette.textPrimary, fontSize = 14.sp)
    }
}

private fun contextTokenCount(value: Int?): String = when {
    value == null -> "—"
    value >= 1_000_000 -> "~" + String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> "~" + String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0)
    else -> "~$value"
}

@Composable
private fun ConversationRow(
    item: ConversationItem,
    thumbnails: Map<String, ImageBitmap>,
    attachmentStates: Map<String, AttachmentLoadState>,
    onRetryAttachment: (String) -> Unit,
    onPreviewImages: (List<GatewayImageAttachment>, Int) -> Unit
) {
    when (item.kind) {
        ConversationItemKind.USER -> UserMessage(
            item,
            thumbnails,
            attachmentStates,
            onRetryAttachment,
            onPreviewImages
        )
        ConversationItemKind.ASSISTANT -> AssistantMessage(item, thumbnails, attachmentStates, onRetryAttachment)
        ConversationItemKind.STATUS -> StatusRow(item)
        ConversationItemKind.SYSTEM -> SystemRow(item)
        else -> Unit
    }
}

@Composable
private fun ConversationProcessRow(group: ConversationProcessGroup) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    val palette = dshPalette()
    val command = group.command
    val commandColor = if (command?.isError == true) {
        MaterialTheme.colorScheme.error
    } else {
        palette.textPrimary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = group.isExpandable) {
                expanded = !expanded
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (command != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_command_status),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (command.isError) MaterialTheme.colorScheme.error
                        else palette.textSecondary
                )
            }
            if (command != null) {
                Text(
                    command.title,
                    color = commandColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "·",
                    color = palette.textTertiary,
                    fontSize = 14.sp
                )
                Text(
                    command.text.singleLinePreview(),
                    modifier = Modifier.weight(1f),
                    color = if (command.isError) MaterialTheme.colorScheme.error
                        else palette.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    group.title,
                    modifier = Modifier.weight(1f),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (group.isExpandable) ProcessChevron(expanded)
        }
        if (expanded && group.isExpandable) {
            Column(
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (command != null && group.commandHasDetailedText) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                palette.surfaceMuted,
                                RoundedCornerShape(14.dp)
                            )
                            .border(
                                1.dp,
                                palette.cardBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            command.text,
                            color = commandColor,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                group.contexts.forEach { ProcessContextDisclosure(it) }
                if (group.reasoningText.isNotEmpty()) {
                    ProcessReasoningDisclosure(group.id, group.reasoningText)
                }
                if (group.tools.isNotEmpty()) {
                    ProcessToolBundle(group.id, group.tools)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 7.dp),
            thickness = 1.dp,
            color = palette.divider
        )
    }
}

@Composable
private fun ProcessContextDisclosure(item: ConversationItem) {
    ProcessDisclosure(
        id = "context-${item.id}",
        title = item.title.toContextDisplayTitle(),
        preview = item.text.singleLinePreview(),
        iconRes = R.drawable.ic_dsh_context_injection,
        tint = DshColors.Success
    ) {
        DshMarkdownText(item.text, Modifier.fillMaxWidth(), compact = true)
    }
}

@Composable
private fun ProcessReasoningDisclosure(groupId: String, text: String) {
    ProcessDisclosure(
        id = "reasoning-$groupId",
        title = "Think",
        preview = text.singleLinePreview(),
        iconRes = R.drawable.ic_dsh_think,
        tint = DshColors.Purple
    ) {
        DshMarkdownText(text, Modifier.fillMaxWidth(), compact = true)
    }
}

@Composable
private fun ProcessToolBundle(
    groupId: String,
    tools: List<ConversationProcessTool>
) {
    val names = tools.mapNotNull { it.call?.title }.take(2)
    val title = when {
        names.isEmpty() -> "查看 ${tools.size} 个工具结果"
        else -> "使用了 ${names.joinToString("、")}${if (tools.size > 2) " 等工具" else ""}"
    }
    ProcessDisclosure(
        id = "tools-$groupId",
        title = title,
        preview = "",
        iconRes = R.drawable.ic_process_tool,
        tint = DshColors.Orange,
        bodyStartPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tools.forEach { ProcessToolDisclosure(it) }
        }
    }
}

@Composable
private fun ProcessToolDisclosure(tool: ConversationProcessTool) {
    val failed = tool.result?.isError == true
    val summary = remember(tool.call?.title, tool.call?.text, tool.result?.title) {
        com.clarklevis.dsh.shared.projection.ToolActivitySummaryFormatter.summarize(
            tool.call?.title ?: tool.result?.title ?: "工具", tool.call?.text.orEmpty()
        )
    }
    ProcessDisclosure(
        id = "tool-${tool.id}",
        title = listOf(summary.label, summary.annotation).filter(String::isNotBlank).joinToString(" · "),
        preview = listOf(
            summary.detail,
            if (failed) "失败" else if (tool.result == null) "等待结果" else ""
        ).filter(String::isNotBlank).joinToString(" · "),
        stackedPreview = true,
        iconRes = processToolIcon(tool.call?.title),
        tint = if (failed) Color.Red else DshColors.Orange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            tool.call?.text?.takeIf(String::isNotEmpty)?.let { arguments ->
                Text(
                    "调用参数",
                    color = dshPalette().textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                DshMarkdownText(
                    "```json\n$arguments\n```",
                    Modifier.fillMaxWidth(),
                    compact = true
                )
            }
            tool.result?.text?.takeIf(String::isNotEmpty)?.let { result ->
                Text(
                    if (failed) "错误" else "结果",
                    color = if (failed) Color.Red else dshPalette().textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                DshMarkdownText(result, Modifier.fillMaxWidth(), compact = true)
            }
        }
    }
}

@Composable
private fun ProcessDisclosure(
    id: String,
    title: String,
    preview: String,
    iconRes: Int,
    tint: Color,
    bodyStartPadding: Dp = 26.dp,
    stackedPreview: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember(id) { mutableStateOf(false) }
    val palette = dshPalette()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = tint
                )
            }
            if (stackedPreview) {
                Column(Modifier.weight(1f).padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = palette.textPrimary,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (preview.isNotEmpty()) Text(preview,
                        color = palette.textSecondary,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(
                    title,
                    color = tint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview.isNotEmpty()) {
                    Text("·", color = palette.textTertiary)
                    Text(
                        preview,
                        modifier = Modifier.weight(1f),
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            ProcessChevron(expanded)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(start = bodyStartPadding, bottom = 3.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ProcessChevron(expanded: Boolean) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(15.dp).rotate(if (expanded) 90f else 0f),
        tint = dshPalette().textTertiary
    )
}

private fun String.singleLinePreview(): String =
    replace(Regex("\\s+"), " ").trim()

private fun String.toContextDisplayTitle(): String = when {
    startsWith("Context · ") -> "上下文注入 · ${removePrefix("Context · ")}"
    else -> this
}

private fun processToolIcon(toolName: String?): Int {
    val normalized = toolName.orEmpty()
        .lowercase()
        .filter(Char::isLetterOrDigit)
    return when (normalized) {
        "write", "edit", "writefile", "editfile", "applypatch" -> R.drawable.ic_session_rename
        "glob", "grep", "websearch" -> R.drawable.ic_dsh_search
        "read", "webfetch", "cordispackageinspect", "cordisruntimeinspect" ->
            R.drawable.ic_dsh_read
        "bash", "pwsh" -> R.drawable.ic_dsh_bash
        else -> R.drawable.ic_process_tool
    }
}

@Composable
private fun UserMessage(
    item: ConversationItem,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit,
    onPreviewImages: (List<GatewayImageAttachment>, Int) -> Unit
) {
    val palette = dshPalette()
    // 用户气泡在浅色体系里改用白色卡片 + 轻描边，靠右对齐与助手正文区分。
    val bubbleFill = palette.surface
    val bubbleEdge = if (palette.isDark) palette.primary.copy(alpha = 0.34f) else palette.cardBorder
    Column(
        Modifier.fillMaxWidth().padding(start = 34.dp, top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserAttachmentPreview(item.images, thumbnails, states, onRetry, onPreviewImages)
        if (item.text.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.text,
                    modifier = Modifier
                        .background(bubbleFill, RoundedCornerShape(15.dp))
                        .border(0.7.dp, bubbleEdge, RoundedCornerShape(15.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("user-text-bubble"),
                    color = palette.textPrimary,
                    fontSize = 16.sp
                )
                CopyButton(item.text)
            }
        }
    }
}

internal data class UserAttachmentPreviewSize(val width: Dp, val height: Dp)

internal fun userSingleAttachmentPreviewSize(
    sourceWidth: Int,
    sourceHeight: Int,
    availableWidth: Dp
): UserAttachmentPreviewSize {
    val safeWidth = sourceWidth.coerceAtLeast(1).toFloat()
    val safeHeight = sourceHeight.coerceAtLeast(1).toFloat()
    val maximumWidth = minOf(320.dp, availableWidth)
    val maximumHeight = 320.dp
    val scale = minOf(
        1f,
        maximumWidth.value / safeWidth,
        maximumHeight.value / safeHeight
    )
    return UserAttachmentPreviewSize(
        width = (safeWidth * scale).dp,
        height = (safeHeight * scale).dp
    )
}

@Composable
private fun UserAttachmentPreview(
    attachments: List<GatewayImageAttachment>,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit,
    onPreviewImages: (List<GatewayImageAttachment>, Int) -> Unit
) {
    if (attachments.isEmpty()) return
    BoxWithConstraints(Modifier.testTag("user-image-bubble")) {
        if (attachments.size == 1) {
            val attachment = attachments.first()
            val previewSize = userSingleAttachmentPreviewSize(
                sourceWidth = attachment.width,
                sourceHeight = attachment.height,
                availableWidth = maxWidth
            )
            UserAttachmentImage(
                attachment = attachment,
                image = thumbnails[attachment.attachmentId],
                state = states[attachment.attachmentId],
                onRetry = onRetry,
                onPreview = { onPreviewImages(attachments, 0) },
                modifier = Modifier.size(previewSize.width, previewSize.height),
                cornerRadius = 11.dp,
                borderWidth = 0.dp
            )
        } else {
            val visibleAttachments = attachments.take(3)
            val cardOffset = 12.dp
            val cardSide = minOf(
                164.dp,
                (maxWidth - cardOffset * (visibleAttachments.size - 1)).coerceAtLeast(96.dp)
            )
            val previewSide = cardSide + cardOffset * (visibleAttachments.size - 1)
            Box(Modifier.size(previewSide)) {
                visibleAttachments.indices.reversed().forEach { index ->
                    val attachment = visibleAttachments[index]
                    UserAttachmentImage(
                        attachment = attachment,
                        image = thumbnails[attachment.attachmentId],
                        state = states[attachment.attachmentId],
                        onRetry = onRetry,
                        onPreview = { onPreviewImages(attachments, index) },
                        modifier = Modifier
                            .offset(
                                x = cardOffset * index,
                                y = cardOffset * (visibleAttachments.lastIndex - index)
                            )
                            .size(cardSide),
                        cornerRadius = 18.dp,
                        borderWidth = 2.dp
                    )
                }
                Text(
                    text = "${attachments.size}张",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UserAttachmentImage(
    attachment: GatewayImageAttachment,
    image: ImageBitmap?,
    state: AttachmentLoadState?,
    onRetry: (String) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier,
    cornerRadius: Dp,
    borderWidth: Dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val retryable = state in setOf(AttachmentLoadState.FAILED, AttachmentLoadState.DEFERRED)
    Box(
        modifier = modifier
            .clip(shape)
            .background(dshPalette().surfaceMuted)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, dshPalette().surface, shape)
                else Modifier
            )
            .clickable {
                if (retryable) onRetry(attachment.attachmentId) else onPreview()
            },
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.name ?: "图片附件",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = when (state) {
                    AttachmentLoadState.FAILED -> "加载失败 · 点击重试"
                    AttachmentLoadState.DEFERRED -> "已释放 · 点击重载"
                    else -> "正在加载图片…"
                },
                modifier = Modifier.padding(10.dp),
                fontSize = 11.sp,
                color = dshPalette().textTertiary
            )
        }
    }
}

internal data class ConversationImagePreviewRequest(
    val attachments: List<GatewayImageAttachment>,
    val initialIndex: Int
)

internal fun imagePreviewInitialPage(imageCount: Int, requestedIndex: Int): Int =
    requestedIndex.coerceIn(0, (imageCount - 1).coerceAtLeast(0))

@Composable
internal fun ConversationImagePreviewDialog(
    request: ConversationImagePreviewRequest,
    thumbnails: Map<String, ImageBitmap>,
    onDismiss: () -> Unit
) {
    if (request.attachments.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = imagePreviewInitialPage(request.attachments.size, request.initialIndex),
        pageCount = request.attachments::size
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("conversation-image-preview")
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val attachment = request.attachments[page]
                ZoomablePreviewImage(
                    image = thumbnails[attachment.attachmentId],
                    contentDescription = attachment.name ?: "图片"
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .semantics { contentDescription = "关闭图片预览" },
                    shape = RoundedCornerShape(19.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${request.attachments.size}",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun ZoomablePreviewImage(
    image: ImageBitmap?,
    contentDescription: String
) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var translation by remember(image) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(image) {
                awaitEachGesture {
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2 || scale > 1f) {
                            val nextScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            if (nextScale == 1f) {
                                translation = Offset.Zero
                            } else {
                                val maximumX = size.width * (nextScale - 1f) / 2f
                                val maximumY = size.height * (nextScale - 1f) / 2f
                                val pan = event.calculatePan()
                                translation = Offset(
                                    x = (translation.x + pan.x).coerceIn(-maximumX, maximumX),
                                    y = (translation.y + pan.y).coerceIn(-maximumY, maximumY)
                                )
                            }
                            scale = nextScale
                            event.changes.forEach { it.consume() }
                        }
                        event = awaitPointerEvent()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_photo_stack),
                contentDescription = contentDescription,
                modifier = Modifier.size(54.dp),
                tint = Color.White.copy(alpha = 0.42f)
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    item: ConversationItem,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AssistantMessageHeaderContent(item, thumbnails, states, onRetry)
        if (item.text.isNotEmpty()) {
            DshStreamingAwareMarkdownText(
                markdown = item.text,
                isStreaming = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        CopyButton(item.text)
    }
}

@Composable
private fun AssistantMessageHeader(
    item: ConversationItem,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        AssistantMessageHeaderContent(item, thumbnails, states, onRetry)
    }
}

@Composable
private fun AssistantMessageHeaderContent(
    item: ConversationItem,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        WhaleIcon(Modifier.width(26.dp).height(20.dp))
        Text(
            item.title,
            color = dshPalette().textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    AttachmentGrid(item.images, thumbnails, states, onRetry)
}

@Composable
private fun StatusRow(item: ConversationItem) {
    val palette = dshPalette()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_command_status),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (item.isError) MaterialTheme.colorScheme.error
                else palette.textSecondary
        )
        Text(item.title, fontSize = 14.sp, color = if (item.isError) MaterialTheme.colorScheme.error else palette.textPrimary)
        Text("·", fontSize = 14.sp, color = palette.textTertiary)
        Text(item.text, fontSize = 14.sp, color = if (item.isError) MaterialTheme.colorScheme.error else palette.textSecondary)
    }
}

@Composable
private fun SystemRow(item: ConversationItem) {
    val palette = dshPalette()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background((if (item.isError) Color.Red else palette.primary).copy(alpha = 0.06f), RoundedCornerShape(11.dp)).padding(10.dp)
    ) {
        Text(if (item.isError) "!" else "⌁", color = if (item.isError) Color.Red else palette.primary)
        Spacer(Modifier.width(9.dp))
        Column {
            Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            if (item.text.isNotEmpty()) Text(item.text, fontSize = 12.sp, color = palette.textSecondary)
        }
    }
}

@Composable
private fun AttachmentGrid(
    attachments: List<GatewayImageAttachment>,
    thumbnails: Map<String, ImageBitmap>,
    states: Map<String, AttachmentLoadState>,
    onRetry: (String) -> Unit
) {
    if (attachments.isEmpty()) return
    val palette = dshPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { attachment ->
                    val image = thumbnails[attachment.attachmentId]
                    Box(
                        Modifier.weight(1f).heightIn(min = 80.dp, max = 240.dp)
                            .clip(RoundedCornerShape(10.dp)).background(palette.surfaceMuted)
                            .clickable(enabled = states[attachment.attachmentId] in setOf(AttachmentLoadState.FAILED, AttachmentLoadState.DEFERRED)) {
                                onRetry(attachment.attachmentId)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (image != null) {
                            Image(image, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                        } else {
                            Text(
                                when (states[attachment.attachmentId]) {
                                    AttachmentLoadState.FAILED -> "加载失败 · 点击重试"
                                    AttachmentLoadState.DEFERRED -> "已释放 · 点击重载"
                                    else -> "正在加载图片…"
                                },
                                fontSize = 11.sp,
                                color = palette.textTertiary
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CopyButton(text: String) {
    if (text.isEmpty()) return
    val palette = dshPalette()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_400)
            copied = false
        }
    }
    Box(
        modifier = Modifier.width(16.dp).height(26.dp).clickable {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("DeepSeek", text))
            copied = true
        }.semantics { contentDescription = if (copied) "已复制" else "复制正文" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(if (copied) R.drawable.ic_menu_check else R.drawable.ic_copy_message),
            contentDescription = null,
            modifier = Modifier.size(if (copied) 14.dp else 16.dp),
            tint = if (copied) palette.primary else palette.textSecondary
        )
    }
}

@Composable
private fun SmallConnectionDot(state: GatewayConnectionState) {
    // 与抽屉/顶栏的连接点共用同一套语义取色，避免同一状态在不同页面显示成不同颜色。
    val color = dshConnectionDotColor(state, dshPalette())
    StatusIndicatorDot(
        color = color,
        modifier = Modifier.size(8.dp),
        glowing = state == GatewayConnectionState.CONNECTED
    )
}

private fun permissionTitle(value: String?) = runtimePermissionTitle(value)

private fun modelTitle(stateHolder: AndroidSharedStateHolder): String {
    val selection = currentModelSelection(stateHolder)
    return when (selection?.model) {
        "deepseek-chat" -> "DeepSeek Chat"
        "deepseek-reasoner" -> "DeepSeek Reasoner"
        null -> "DeepSeek Agent"
        else -> currentModelItem(stateHolder)?.name ?: selection.model
    }
}

private fun currentModelSelection(stateHolder: AndroidSharedStateHolder) =
    stateHolder.snapshot.modelCatalog?.current ?: stateHolder.snapshot.defaultModel

private fun currentModelItem(stateHolder: AndroidSharedStateHolder): GatewayModelItem? {
    val selection = currentModelSelection(stateHolder) ?: return null
    return stateHolder.snapshot.modelCatalog?.groups
        ?.firstOrNull { it.id == selection.provider }
        ?.models
        ?.firstOrNull { it.id == selection.model }
}

private fun currentModelEfforts(stateHolder: AndroidSharedStateHolder): List<GatewayReasoningEffort> =
    currentModelItem(stateHolder)?.reasoning?.efforts.orEmpty()

private fun currentModelEffortId(stateHolder: AndroidSharedStateHolder): String? =
    currentModelSelection(stateHolder)?.reasoningEffort

private fun currentModelEffortTitle(stateHolder: AndroidSharedStateHolder): String? {
    val effortId = currentModelEffortId(stateHolder) ?: return null
    return currentModelEfforts(stateHolder).firstOrNull { it.id == effortId }?.name
        ?: effortId.lowercase().replaceFirstChar(Char::uppercase)
}

private fun permissionIcon(value: String?): Int = when (value) {
    "workspace-write" -> R.drawable.ic_permission_write
    "read-only" -> R.drawable.ic_permission_read
    "danger-full-access" -> R.drawable.ic_permission_warning
    else -> R.drawable.ic_permission_ask
}

/**
 * 页头副标题：会话任务进度，例如「3 个任务 · 1 进行中」。
 *
 * **用词必须诚实**：这里刻意不写「后台任务」。核对协议与投影后确认，
 * 「后台任务 / 子代理」这个概念在本 App 里**不存在**——数据源只有 `taskSnapshot`，
 * 它就是 `todo_write` 的待办清单投影（`SharedMobileStore.kt:137-140`；
 * 状态取值为 `in_progress` / `completed` / 其他=待处理）。
 * 写成「后台任务」会承诺一个并不存在的功能。
 *
 * 计数口径与输入框上方的任务面板（`TaskGoalUi` 的 `taskSummary`）保持一致，
 * 避免同一页面上两处对同一份数据给出不同数字。
 *
 * 无任务时返回 null（页头不显示副标题），而不是显示「0 个任务」占位。
 */
internal fun conversationTaskSubtitle(tasks: List<GatewayTask>?): String? {
    if (tasks.isNullOrEmpty()) return null
    val completed = tasks.count { it.status == "completed" }
    val active = tasks.count { it.status == "in_progress" }
    val pending = tasks.size - completed - active
    val detail = buildList {
        if (active > 0) add("$active 进行中")
        if (pending > 0) add("$pending 待处理")
        if (completed > 0) add("$completed 已完成")
    }.joinToString(" · ")
    return if (detail.isEmpty()) "${tasks.size} 个任务" else "${tasks.size} 个任务 · $detail"
}
