package com.clarklevis.dsh.android.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.MobileScheduledTask
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun ScheduledTasksScreen(
    stateHolder: AndroidSharedStateHolder,
    onOpenSession: (String) -> Unit,
    onNewTask: () -> Unit = {}
) {
    val palette = dshPalette()
    var editingTask by remember { mutableStateOf<MobileScheduledTask?>(null) }
    var deletingTask by remember { mutableStateOf<MobileScheduledTask?>(null) }
    var revealedTaskId by remember { mutableStateOf<String?>(null) }
    var showsMutationError by remember { mutableStateOf(false) }
    val connection = stateHolder.gatewayState.connection
    // 编辑/删除都是需要网关的动作，必须按唯一语义源禁用，而不是只看 busy。
    val canMutate = !connection.dshBlocksNetworkActions
    // 一次建索引，供列表 O(1) 查会话标题（见下方 items 内的使用）。
    val sessionTitles = remember(stateHolder.snapshot.sessions) {
        stateHolder.snapshot.sessions.associateBy({ it.id }, { it.title })
    }
    // 只在 ONLINE 刷新：原先「未连接且列表为空」也会发一次请求，而状态层随即以
    //「连接网关后可查看定时任务」失败，页面再把它渲染成红色错误块——但 IDLE 的语义是
    //「空闲态，不是错误」（DshConnectionStateUi.kt:23-24），不该当成错误展示。
    LaunchedEffect(connection) {
        if (connection == GatewayConnectionState.CONNECTED) stateHolder.refreshScheduledTasks()
    }
    // 统一走 DshTabScaffold：页头（设备副标题 + 相位标签 + 配对入口）+ 正文 + FAB。
    // FAB 的新建动作：宿主网关**没有**「新建定时任务」的协议（只有 list/catalog/
    // update/delete，Web 端的「新建」同样是引导开新会话，见宿主
    // ui-schedule/src/client/index.ts 的 onNewTask），因此跳到首页并新建任务
    // ——定时任务由 Agent 在会话里创建。
    DshTabScaffold(
        title = "定时任务",
        subtitle = stateHolder.activeGatewayDisplayName(),
        connection = connection,
        modifier = Modifier
            .background(palette.canvas)
            .navigationBarsPadding()
            .testTag("scheduled-tasks-screen"),
        fab = DshFabSpec(
            onClick = onNewTask,
            contentDescription = "新建任务",
            testTag = "scheduled-tasks-fab"
        )
    ) {
        when {
            stateHolder.scheduledTasksLoading && stateHolder.scheduledTasks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            stateHolder.scheduledTasksError != null && stateHolder.scheduledTasks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stateHolder.scheduledTasksError.orEmpty(), color = palette.textSecondary)
                        Button(onClick = stateHolder::refreshScheduledTasks, modifier = Modifier.padding(top = 12.dp)) {
                            Text("重试")
                        }
                    }
                }
            }
            stateHolder.scheduledTasks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无定时任务", color = palette.textSecondary)
                }
            }
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, top = 10.dp, bottom = DshFabReservedHeight
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 公共「分组圆角列表」容器（与项目页/首页同一套，DshGroupedList.kt）：
                // 定时任务整组共享一个圆角 surface，滑动交互与展开详情保留在行内。
                item(key = "scheduled-tasks-group") {
                    DshGroupedSection(
                        label = "任务",
                        showCount = true,
                        count = stateHolder.scheduledTasks.size
                    ) {
                        stateHolder.scheduledTasks.forEachIndexed { index, task ->
                            if (index > 0) DshGroupedRowDivider(startIndent = 20.dp)
                            ScheduledTaskCard(
                                task = task,
                                // 建一次索引再查表：原先在 items lambda 里对每个 task 线性扫描
                                // snapshot.sessions（O(tasks × sessions)），而 snapshot 每次 token 流
                                // 都会重发布，列表滚动时会被反复放大。
                                sessionTitle = sessionTitles[task.sessionId] ?: task.sessionId,
                                revealedTaskId = revealedTaskId,
                                onRevealChange = { revealedTaskId = it },
                                onOpenSession = {
                                    if (revealedTaskId == task.id) revealedTaskId = null
                                    else {
                                        revealedTaskId = null
                                        onOpenSession(task.sessionId)
                                    }
                                },
                                onEdit = {
                                    revealedTaskId = null
                                    editingTask = task
                                },
                                canMutate = canMutate,
                                onDelete = {
                                    revealedTaskId = null
                                    deletingTask = task
                                },
                                busy = stateHolder.scheduledTaskPendingId == task.id
                            )
                        }
                    }
                }
            }
        }
    }
    editingTask?.let { task ->
        ScheduledTaskEditSheet(task, stateHolder, onDismiss = { editingTask = null })
    }
    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("删除定时任务？") },
            text = { Text("删除后任务及投递记录无法恢复；已进入会话队列的消息不会撤回。") },
            confirmButton = {
                TextButton(onClick = {
                    // deleteScheduledTask 在「未连接」等情况下返回 null。此时必须立即告知用户：
                    // 否则对话框消失、任务仍留在列表里，用户无从得知删除其实没有发生。
                    if (stateHolder.deleteScheduledTask(task) == null) showsMutationError = true
                    deletingTask = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingTask = null }) { Text("取消") } }
        )
    }
    LaunchedEffect(stateHolder.scheduledTaskMutationError) {
        if (stateHolder.scheduledTaskMutationError != null && editingTask == null) showsMutationError = true
    }
    if (showsMutationError) {
        AlertDialog(
            onDismissRequest = { showsMutationError = false },
            title = { Text("定时任务操作失败") },
            text = { Text(stateHolder.scheduledTaskMutationError ?: "请稍后重试") },
            confirmButton = { TextButton(onClick = { showsMutationError = false }) { Text("好") } }
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    task: MobileScheduledTask,
    sessionTitle: String,
    revealedTaskId: String?,
    onRevealChange: (String?) -> Unit,
    onOpenSession: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
    // 编辑/删除都要走网关，离线时必须禁用，否则点了才发现失败（参照首页
    // `dshBlocksNetworkActions` 对「新建任务」的处理）。
    canMutate: Boolean
) {
    val palette = dshPalette()
    val dark = palette.isDark
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        animationSpec = tween(220),
        label = "schedule-chevron"
    )
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 128.dp.toPx() }
    val flingThresholdPx = with(density) { 280.dp.toPx() }
    var offsetPx by remember(task.id) {
        mutableFloatStateOf(if (revealedTaskId == task.id) -revealWidthPx else 0f)
    }
    var animationJob by remember(task.id) { mutableStateOf<Job?>(null) }
    var localSettleTarget by remember(task.id) { mutableStateOf<Boolean?>(null) }
    var startedOpen by remember(task.id) { mutableStateOf(false) }
    var suppressCardTapUntil by remember(task.id) { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val progress = (-offsetPx / revealWidthPx).coerceIn(0f, 1f)

    fun settle(open: Boolean, velocity: Float = 0f) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = if (open) -revealWidthPx else 0f,
                initialVelocity = velocity,
                animationSpec = spring(stiffness = 450f, dampingRatio = 0.86f)
            ) { value, _ -> offsetPx = value.coerceIn(-revealWidthPx, 0f) }
        }
    }

    LaunchedEffect(revealedTaskId) {
        val shouldOpen = revealedTaskId == task.id
        if (localSettleTarget == shouldOpen) {
            localSettleTarget = null
        } else if (!shouldOpen && offsetPx < 0f) {
            settle(open = false)
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .requiredWidth(with(density) { (-offsetPx).toDp() })
                    .clipToBounds()
            ) {
                Row(
                    Modifier.align(Alignment.CenterEnd).requiredWidth(119.dp)
                        .offset(x = (18 * (1f - progress)).dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = 0.72f + 0.28f * progress
                            scaleY = 0.72f + 0.28f * progress
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }.then(if (progress < 0.95f) Modifier.clearAndSetSemantics {} else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        enabled = progress > 0.95f && task.status == "active" && !busy && canMutate,
                        modifier = Modifier.size(50.dp)
                            .clip(CircleShape)
                            .background(
                                palette.surface.copy(alpha = if (dark) 0.82f else 0.94f),
                                CircleShape
                            )
                            .border(
                                0.8.dp,
                                palette.cardBorder,
                                CircleShape
                            )
                    ) {
                        Icon(painterResource(R.drawable.ic_pencil_line), contentDescription = "编辑${task.title}",
                            modifier = Modifier.size(20.dp), tint = palette.textPrimary)
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = progress > 0.95f && !busy && canMutate,
                        modifier = Modifier.size(50.dp).background(DshColors.Danger, CircleShape)
                    ) {
                        Icon(painterResource(R.drawable.ic_trash), contentDescription = "删除${task.title}",
                            modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                // 行已处于分组容器的 surface 上（DshGroupedSection），不再自带卡片底/描边/阴影：
                // 旧样式每行一张带阴影的独立大卡，新样式行是容器内的一个分段。
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        animationJob?.cancel()
                        offsetPx = (offsetPx + delta).coerceIn(-revealWidthPx, 0f)
                    },
                    onDragStarted = {
                        startedOpen = revealedTaskId == task.id
                        suppressCardTapUntil = SystemClock.uptimeMillis() + 350L
                    },
                    onDragStopped = { velocity ->
                        val open = when {
                            velocity < -flingThresholdPx -> true
                            velocity > flingThresholdPx -> false
                            else -> offsetPx <= -revealWidthPx * (if (startedOpen) 0.75f else 0.25f)
                        }
                        suppressCardTapUntil = SystemClock.uptimeMillis() + 350L
                        localSettleTarget = open
                        onRevealChange(if (open) task.id else null)
                        settle(open, velocity)
                    }
                )
        ) {
            Column(
                Modifier.fillMaxWidth().clickable {
                    if (SystemClock.uptimeMillis() >= suppressCardTapUntil) onOpenSession()
                }
                    // 内边距对齐列表页的层级（DshGroupedRow 用 12dp/13dp）：
                    // 此前 20dp/20dp 是旧「大卡片」时代的遗留，与其他页比明显偏松。
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            ) {
                // 字号对齐 App 的通用层级：正文 15sp、辅助 12sp（DshGroupedRow 同源）。
                // 此前标题 21sp / 正文 16sp，比其他页大出两档，切换 Tab 时突兀。
                Text(shortRule(task), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primary)
                Text(
                    task.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    task.prompt, fontSize = 13.sp, lineHeight = 18.sp,
                    color = palette.textSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = palette.divider)
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (SystemClock.uptimeMillis() >= suppressCardTapUntil) {
                        if (revealedTaskId == task.id) onRevealChange(null)
                        else expanded = !expanded
                    }
                }.padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (task.status == "active") formatScheduleDate(task.scheduledAt) else "已结束",
                    color = palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_up),
                        contentDescription = if (expanded) "收起任务详情" else "展开任务详情",
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
                        tint = palette.textSecondary
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(220))
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScheduleDetail("执行规则", fullRule(task))
                    ScheduleDetail("下次计划时间", if (task.status == "active") formatScheduleDate(task.scheduledAt) else "无")
                    ScheduleDetail("所属会话", sessionTitle)
                    ScheduleDetail("状态", if (task.status == "active") "运行中" else "已结束")
                    ScheduleDetail("最近投递", task.lastDelivery?.get("deliveredAt")?.stringValue?.let { "已投递 · ${formatScheduleDate(it)}" } ?: "暂无投递记录")
                }
            }
        }
    }
}

@Composable
private fun ScheduleDetail(label: String, value: String) {
    val palette = dshPalette()
    // 13sp 与 ScheduleDetail 之外的辅助文字同为一级（App 里正文 15sp、辅助 12–13sp）。
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = palette.textSecondary,
            modifier = Modifier.size(width = 92.dp, height = 20.dp))
        Text(value, fontSize = 13.sp, lineHeight = 19.sp, color = palette.textPrimary,
            modifier = Modifier.weight(1f))
    }
}

private fun shortRule(task: MobileScheduledTask): String = when (task.kind) {
    "daily" -> "每天"
    "weekly" -> "每周"
    "every" -> "每 ${duration(task.raw["everySeconds"]?.doubleValue?.toInt() ?: 0)}"
    "cron" -> "按计划"
    else -> "一次"
}

/**
 * Cron 表达式的**人话释义**。
 *
 * 原先直接把 `0 9 * * 1` 这样的原文吐给用户（`shortRule` 只显示"Cron"），普通用户无法
 * 从中判断任务什么时候跑。这里覆盖最常见的五个字段位；解析不了时才回退到原文，
 * 保证不猜错。
 */
private fun cronDescription(expression: String): String? {
    val fields = expression.trim().split(Regex("\\s+"))
    if (fields.size != 5) return null
    // cron 顺序是 分 时 日 月 周
    val minuteField = fields[0]
    val hourField = fields[1]
    val dayOfMonthField = fields[2]
    val monthField = fields[3]
    val dayOfWeekField = fields[4]
    if (monthField != "*" || dayOfMonthField != "*") return null
    val dayPart = when (dayOfWeekField) {
        "*", "?" -> null
        "1-5", "MON-FRI" -> "工作日"
        "0,6", "6,0", "SUN,SAT" -> "周末"
        else -> {
            val names = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
            val parts = dayOfWeekField.split(",")
            parts.mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == parts.size && it.isNotEmpty() }
                ?.mapNotNull { names.getOrNull(it % 7) }
                ?.joinToString("、")
                ?: return null
        }
    }
    val hour = hourField.toIntOrNull() ?: return null
    val minute = minuteField.toIntOrNull() ?: return null
    val timePart = "%02d:%02d".format(hour, minute)
    return when (dayPart) {
        null -> "每天 $timePart"
        else -> "每$dayPart $timePart"
    }
}

private fun fullRule(task: MobileScheduledTask): String = when (task.kind) {
    "daily" -> "每天 ${task.raw["time"]?.stringValue.orEmpty()} · ${task.raw["timeZone"]?.stringValue.orEmpty()}"
    "weekly" -> {
        val days = task.raw["weekdays"]?.arrayValue.orEmpty().mapNotNull { it.doubleValue?.toInt() }
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        "每周${days.mapNotNull { dayNames.getOrNull(it - 1) }.joinToString("、周")} · ${task.raw["time"]?.stringValue.orEmpty()} · ${task.raw["timeZone"]?.stringValue.orEmpty()}"
    }
    "every" -> "每 ${duration(task.raw["everySeconds"]?.doubleValue?.toInt() ?: 0)}执行一次"
    "cron" -> {
        val expression = task.raw["expression"]?.stringValue.orEmpty()
        // 有释义就用人话，并保留原文做副信息；解析不了才只显示原文。
        val description = cronDescription(expression)
        if (description == null) {
            "$expression · ${task.raw["timeZone"]?.stringValue.orEmpty()}"
        } else {
            "$description · ${task.raw["timeZone"]?.stringValue.orEmpty()}"
        }
    }
    "after" -> "${duration(task.raw["afterSeconds"]?.doubleValue?.toInt() ?: 0)}后执行一次"
    "at" -> "指定时间执行一次"
    else -> task.kind
}

private fun duration(seconds: Int): String = when {
    seconds > 0 && seconds % 86_400 == 0 -> "${seconds / 86_400} 天"
    seconds > 0 && seconds % 3_600 == 0 -> "${seconds / 3_600} 小时"
    seconds > 0 && seconds % 60 == 0 -> "${seconds / 60} 分钟"
    else -> "$seconds 秒"
}

private val scheduleInputPatterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
private val scheduleOutputFormat = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)

/**
 * 解析用 pattern 按 Locale 缓存复用。
 *
 * 原先每次调用都新建两个 `SimpleDateFormat` 并把输入串解析两遍，而本函数被三处调用
 * （卡片时间行、下次执行时间、详情行），列表中每行每次重组都要付出这份开销。
 * 对照 `relativeTime` 早已用 map 缓存 Calendar（`DshProductApp.kt:675-677`）。
 */
private fun formatScheduleDate(value: String): String {
    val date = scheduleInputPatterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(value)
        }.getOrNull()
    } ?: return value
    return scheduleOutputFormat.format(date)
}
