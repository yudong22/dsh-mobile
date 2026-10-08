package com.clarklevis.dsh.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
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
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.DshAndroidApplication
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.protocol.GatewayPermissionOption

/** 「任务运行设置」面板当前显示的层级。 */
private enum class RuntimeSettingsPage { MAIN, DEVICE, WORKSPACE, PERMISSION }

/**
 * 顶部标题拼接：`设备 | 工作空间`，与参考截图的副标题一致；空值自动省略。
 * 抽成纯函数便于单测。
 */
internal fun runtimeHeaderSubtitle(deviceLabel: String?, workspaceLabel: String?): String =
    listOfNotNull(deviceLabel, workspaceLabel)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("  |  ")

/**
 * 权限模式的兜底中文名（网关未返回 name 时使用）。
 *
 * 这是**唯一**的权限文案来源：设置页与对话页此前各自抄了一份，`else` 分支还互相漂移
 * （"未读取" / "默认权限" / "默认"）。网关实际会返回 `ask`，因此它也需要本地化，
 * 而不是把英文原样显示出来。调用方若已拿到 `GatewayPermissionOption.name`，应优先用它。
 */
internal fun runtimePermissionTitle(value: String?): String = when (value) {
    "read-only" -> "只读"
    "workspace-write" -> "工作区写入"
    "danger-full-access" -> "完全访问"
    "ask" -> "每次询问"
    null, "" -> "默认"
    else -> value
}

private fun permissionIconRes(value: String?): Int = when (value) {
    "read-only" -> R.drawable.ic_permission_read
    "workspace-write" -> R.drawable.ic_permission_write
    "danger-full-access" -> R.drawable.ic_permission_warning
    else -> R.drawable.ic_permission_ask
}

/**
 * 点击顶部标题弹出的「任务运行设置」面板。
 *
 * 主层三行：设备（当前 Gateway 主机）、工作空间、权限模式；
 * 每行都有 `›` 且可下钻到二级选择页，二级页顶部有返回、底部选中项带勾选。
 * 交互与参考截图一致：选完即生效并回到主层（设备切换会重建运行时，直接关闭面板）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuntimeSettingsSheet(
    stateHolder: AndroidSharedStateHolder,
    deviceLabel: String,
    workspaceLabel: String,
    onDismiss: () -> Unit
) {
    val palette = dshPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // saveable：面板停留在二级页时旋转屏幕，子页不应回到主层（面板本身由 M3 的 SheetState 保留）。
    var page by rememberSaveable { mutableStateOf(RuntimeSettingsPage.MAIN) }
    val context = LocalContext.current
    val hosts = (context.applicationContext as? DshAndroidApplication)?.hosts

    val permissionOptions = remember(
        stateHolder.snapshot.permissions,
        stateHolder.snapshot.permissionDefaultOptions
    ) {
        stateHolder.snapshot.permissions?.options?.takeIf { it.isNotEmpty() }
            ?: stateHolder.snapshot.permissionDefaultOptions
    }
    val currentPermission = stateHolder.snapshot.permissions?.currentValue
        ?: stateHolder.snapshot.permissionDefault
    val workspaceTitle = stateHolder.availableWorkspaces
        .firstOrNull { it.workspaceId == stateHolder.selectedWorkspaceId }?.title
        ?: if (stateHolder.isUngroupedWorkspaceSelected) "未分组" else workspaceLabel

    // `ModalBottomSheet` 渲染在它自己的 `ComponentDialog` 窗口里，并通过
    // `ModalBottomSheetDialogWrapper` 安装自己的返回回调。因此两件事缺一不可：
    //  1. `shouldDismissOnBackPress = false` —— 否则 M3 的默认回调会抢在返回键之前
    //     直接把整个面板关掉，二级页永远回不到主层；
    //  2. `BackHandler` 必须写在 `ModalBottomSheet` 的 content **内部** —— 只有 content
    //     才处于该 dialog 窗口的 composition 中，写在外部注册到的是 Activity 的
    //     dispatcher，面板打开时根本不会被调用。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        // 二级页：返回键先回到主层；主层：返回键关闭面板。因为上面关掉了 M3 的
        // shouldDismissOnBackPress，主层的关闭行为必须由这里接管，否则返回键会失效。
        BackHandler {
            if (page == RuntimeSettingsPage.MAIN) onDismiss() else page = RuntimeSettingsPage.MAIN
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 20.dp).testTag("runtime-settings-sheet")
        ) {
            RuntimeSettingsTitleBar(
                title = when (page) {
                    RuntimeSettingsPage.MAIN -> "任务运行设置"
                    RuntimeSettingsPage.DEVICE -> "设备"
                    RuntimeSettingsPage.WORKSPACE -> "工作空间"
                    RuntimeSettingsPage.PERMISSION -> "权限模式"
                },
                showsBack = page != RuntimeSettingsPage.MAIN,
                onBack = { page = RuntimeSettingsPage.MAIN },
                onClose = onDismiss,
                palette = palette
            )
            when (page) {
                RuntimeSettingsPage.MAIN -> {
                    RuntimeSettingsCard(palette) {
                        RuntimeSettingsRow(
                            iconRes = if (hosts?.activeProfile?.server == true) {
                                R.drawable.ic_gateway_server
                            } else {
                                R.drawable.ic_gateway_pc
                            },
                            label = "设备",
                            value = deviceLabel,
                            testTag = "runtime-settings-device",
                            palette = palette
                        ) { page = RuntimeSettingsPage.DEVICE }
                    }
                    Spacer(Modifier.height(12.dp))
                    RuntimeSettingsCard(palette) {
                        RuntimeSettingsRow(
                            iconRes = R.drawable.ic_folder_outline,
                            label = "工作空间",
                            value = workspaceTitle,
                            testTag = "runtime-settings-workspace",
                            palette = palette
                        ) { page = RuntimeSettingsPage.WORKSPACE }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 18.dp),
                            color = palette.divider
                        )
                        RuntimeSettingsRow(
                            iconRes = permissionIconRes(currentPermission),
                            label = "权限模式",
                            value = runtimePermissionTitleOrName(permissionOptions, currentPermission),
                            testTag = "runtime-settings-permission",
                            palette = palette
                        ) { page = RuntimeSettingsPage.PERMISSION }
                    }
                }

                RuntimeSettingsPage.DEVICE -> RuntimeOptionList(
                    palette = palette,
                    testTag = "runtime-settings-device-list",
                    emptyNotice = "尚未配对任何设备。可在「设置 → Mobile Gateway」中扫码配对。",
                    options = hosts?.profiles.orEmpty().map { profile ->
                        RuntimeOption(
                            id = profile.localId,
                            title = profile.displayName,
                            detail = buildString {
                                append(if (profile.server) "服务器" else "电脑")
                                if (profile.localId in hosts?.onlineIds.orEmpty()) append(" · 在线")
                            },
                            selected = profile.localId == hosts?.activeId
                        )
                    }
                ) { id ->
                    // 切换主机重建运行时与状态持有者，关闭面板避免停留在旧实例上。
                    hosts?.profiles?.firstOrNull { it.localId == id }?.let { hosts.select(it) }
                    onDismiss()
                }

                RuntimeSettingsPage.WORKSPACE -> RuntimeOptionList(
                    palette = palette,
                    testTag = "runtime-settings-workspace-list",
                    emptyNotice = "尚未从网关读取到工作空间。请先连接并刷新。",
                    options = buildList {
                        add(
                            RuntimeOption(
                                id = AndroidSharedStateHolder.UNGROUPED_WORKSPACE_ID,
                                title = "未分组",
                                detail = "不归属任何工作空间的会话",
                                selected = stateHolder.isUngroupedWorkspaceSelected
                            )
                        )
                        stateHolder.availableWorkspaces.forEach { workspace ->
                            add(
                                RuntimeOption(
                                    id = workspace.workspaceId,
                                    title = workspace.title,
                                    detail = workspace.path,
                                    selected = workspace.workspaceId == stateHolder.selectedWorkspaceId
                                )
                            )
                        }
                    }
                ) { id ->
                    stateHolder.selectWorkspace(id)
                    page = RuntimeSettingsPage.MAIN
                }

                RuntimeSettingsPage.PERMISSION -> RuntimeOptionList(
                    palette = palette,
                    testTag = "runtime-settings-permission-list",
                    emptyNotice = if (stateHolder.snapshot.selectedSessionId == null) {
                        "请先打开一个会话，再调整权限模式。"
                    } else {
                        "网关尚未返回可选权限。"
                    },
                    options = permissionOptions.map { option ->
                        RuntimeOption(
                            id = option.value,
                            title = option.name,
                            detail = option.description,
                            selected = option.value == currentPermission
                        )
                    }
                ) { value ->
                    stateHolder.setSessionPermission(value)
                    page = RuntimeSettingsPage.MAIN
                }
            }
        }
    }
}

private fun runtimePermissionTitleOrName(
    options: List<GatewayPermissionOption>,
    value: String?
): String = options.firstOrNull { it.value == value }?.name ?: runtimePermissionTitle(value)

@Composable
private fun RuntimeSettingsTitleBar(
    title: String,
    showsBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    palette: DshPalette
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showsBack) {
            Box(
                Modifier.size(30.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "返回上一级" },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_back_chevron),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    colorFilter = ColorFilter.tint(palette.textPrimary)
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Box(
            Modifier.size(34.dp)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "关闭任务运行设置" }
                .testTag("runtime-settings-close"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(palette.textSecondary)
            )
        }
    }
}

@Composable
private fun RuntimeSettingsCard(
    palette: DshPalette,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(palette.surface, RoundedCornerShape(22.dp))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(22.dp))
    ) {
        content()
    }
}

@Composable
private fun RuntimeSettingsRow(
    iconRes: Int,
    label: String,
    value: String,
    testTag: String,
    palette: DshPalette,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(palette.textSecondary)
        )
        Text(
            text = label,
            color = palette.textPrimary,
            fontSize = 17.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = palette.textSecondary,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Image(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(palette.textTertiary)
        )
    }
}

/** 二级选择页共用的一行数据。 */
private data class RuntimeOption(
    val id: String,
    val title: String,
    val detail: String?,
    val selected: Boolean
)

@Composable
private fun RuntimeOptionList(
    palette: DshPalette,
    testTag: String,
    emptyNotice: String,
    options: List<RuntimeOption>,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) {
        Text(
            text = emptyNotice,
            color = palette.textTertiary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag(testTag)
    ) {
        items(options, key = { it.id }) { option ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(option.id) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
                    .testTag("runtime-option-${option.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = option.title,
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    option.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            color = palette.textTertiary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
                if (option.selected) {
                    Box(
                        Modifier.size(22.dp).background(palette.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_menu_check),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            colorFilter = ColorFilter.tint(palette.surface)
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 22.dp), color = palette.divider)
        }
    }
}
