package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarDefaults
import com.clarklevis.dsh.shared.protocol.GatewayPermissionOption
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import com.clarklevis.dsh.shared.protocol.GatewayAgentPreset
import com.clarklevis.dsh.shared.protocol.GatewayModelGroup
import com.clarklevis.dsh.shared.protocol.GatewayModelItem
import com.clarklevis.dsh.shared.protocol.GatewayReasoningEffort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    stateHolder: AndroidSharedStateHolder,
    onBack: () -> Unit,
    onOpenAgentPresets: () -> Unit,
    onOpenDefaultModel: () -> Unit
) {
    var showPermissionPicker by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    val palette = dshPalette()
    val pageBackground = palette.canvas
    LaunchedEffect(stateHolder.gatewayState.connection) { stateHolder.refreshProductState(force = true) }
    Scaffold(
        // 统一走 DshPageHeader：此前这里用的是 Material 的 CenterAlignedTopAppBar，
        // 标题 17sp 居中，与其余页面不一致（Material 默认还带一层自己的高度与图标尺寸，
        // 与自定义顶栏混用会造成顶栏高度细微跳动）。
        topBar = { DshPageHeader(title = "设置", onBack = onBack) },
        containerColor = pageBackground
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 720.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    SettingsSection(
                        title = "新会话默认配置",
                        footer = "与 WebUI 使用同一份部署级设置。修改只影响之后新建的会话，运行中的会话保持启动时的配置。"
                    ) {
                        SettingsValueRow(
                            title = "Agent 预设",
                            value = selectedPreset(stateHolder),
                            enabled = online(stateHolder),
                            isLoading = stateHolder.defaultConfigurationLoadingKinds.any {
                                it == "defaults" || it == "agent-presets"
                            }
                        ) {
                            onOpenAgentPresets()
                        }
                        SettingsDivider()
                        SettingsValueRow(
                            title = "默认模型",
                            value = selectedModel(stateHolder),
                            enabled = online(stateHolder),
                            isLoading = stateHolder.defaultConfigurationLoadingKinds.any {
                                it == "default-model" || it == "save-default-model"
                            }
                        ) {
                            onOpenDefaultModel()
                        }
                        SettingsDivider()
                        PermissionSettingsRow(
                            value = stateHolder.permissionDefaultOptions
                                .firstOrNull { it.value == stateHolder.permissionDefault }?.name
                                ?: permissionName(stateHolder.permissionDefault),
                            selectedPermission = stateHolder.permissionDefault,
                            options = stateHolder.permissionDefaultOptions,
                            expanded = showPermissionPicker,
                            enabled = online(stateHolder) &&
                                "set-default" !in stateHolder.defaultConfigurationLoadingKinds,
                            onExpandedChange = { showPermissionPicker = it },
                            onPermissionSelected = { pendingPermission = it }
                        )
                    }
                    SettingsSection("Mobile Gateway") {
                        GatewayEndpointRow(stateHolder)
                        SettingsDivider()
                        GatewayStatusRow(stateHolder)
                        SettingsDivider()
                        // 过渡态必须禁用：IN_PROGRESS 的语义就是「不应让用户重复触发连接操作」
                        // （DshConnectionStateUi.kt:17-18），否则会与正在进行的握手竞争。
                        SettingsActionRow(
                            title = when (stateHolder.gatewayState.connection.dshPhase) {
                                DshConnectionPhase.ONLINE -> "断开连接"
                                DshConnectionPhase.IN_PROGRESS -> "连接中…"
                                DshConnectionPhase.ATTENTION -> "重新连接"
                                DshConnectionPhase.IDLE -> "连接"
                            },
                            enabled = !stateHolder.gatewayState.connection.dshIsTransitioning
                        ) {
                            if (stateHolder.gatewayState.connection.dshPhase == DshConnectionPhase.ONLINE) {
                                stateHolder.disconnect()
                            } else {
                                stateHolder.connect()
                            }
                        }
                        SettingsDivider()
                        SettingsActionRow("Ping 网关", enabled = online(stateHolder), onClick = stateHolder::pingGateway)
                    }
                    stateHolder.hostSnapshot?.let { host ->
                        SettingsSection("DSH Host") {
                            SettingsValueRow("版本", host.version ?: "—")
                            SettingsDivider()
                            SettingsValueRow("Provider", host.provider ?: "—")
                            SettingsDivider()
                            SettingsValueRow("Model", host.model ?: "—")
                            SettingsDivider()
                            SettingsValueRow("已连接会话", "${host.attachedSessions ?: 0}")
                            host.cwd?.let { cwd ->
                                SettingsDivider()
                                SettingsValueRow("cwd", cwd)
                            }
                        }
                    }
                    SettingsSection(
                        title = "外观",
                        footer = "语言设置将在重新启动应用后生效。当前仅支持简体中文，其他系统语言将显示中文。"
                    ) {
                        InterfaceStyleSettingsRow()
                        SettingsDivider()
                        LanguageSettingsRow()
                    }
                    SettingsSection(
                        title = stringResource(R.string.settings_notifications_section),
                        footer = stringResource(R.string.settings_notify_in_background_summary)
                    ) {
                        NotificationSettingsRow()
                    }
                    stateHolder.platformError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    Spacer(Modifier.padding(bottom = 20.dp))
                }
            }
        }
    }
    pendingPermission?.let { value ->
        DshAlertDialog(
            title = "修改全局默认权限？",
            message = "将新会话的默认权限改为“${stateHolder.permissionDefaultOptions.firstOrNull { it.value == value }?.name ?: permissionName(value)}”。这会更新部署级设置，并同步影响 WebUI。",
            confirmLabel = "确认修改",
            onDismissRequest = { pendingPermission = null },
            onConfirm = {
                stateHolder.setDefault("permission", value)
                pendingPermission = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentPresetSelectionScreen(
    stateHolder: AndroidSharedStateHolder,
    onBack: () -> Unit
) {
    var pendingPreset by remember { mutableStateOf<GatewayAgentPreset?>(null) }
    val presets = stateHolder.agentPresets
    val isLoading = "agent-presets" in stateHolder.defaultConfigurationLoadingKinds && presets.isEmpty()
    val isBusy = "set-default" in stateHolder.defaultConfigurationLoadingKinds
    LaunchedEffect(Unit) {
        if (presets.isEmpty()) stateHolder.refreshDefaultConfiguration()
    }

    SettingsSelectionScaffold(title = "Agent 预设", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSelectionHeader(
                    title = "Agent 预设",
                    description = "预设决定 Agent 使用的工具、提示词与能力。选择后只对新建会话生效。"
                )
            }
            when {
                isLoading -> item { SettingsLoadingState("正在读取 Agent 预设…") }
                presets.isEmpty() -> item {
                    SettingsEmptyState(
                        title = "没有可用的 Agent 预设",
                        description = "请确认 Mobile Gateway 已升级到 v0.1.11 并保持连接。"
                    )
                }
                else -> items(presets, key = GatewayAgentPreset::id) { preset ->
                    AgentPresetCard(
                        preset = preset,
                        selected = preset.id == stateHolder.agentPresetDefault,
                        busy = isBusy,
                        onClick = { pendingPreset = preset }
                    )
                }
            }
            if (stateHolder.agentPresetsAuthorable || stateHolder.agentPresetsHasDocument) {
                item {
                    Text(
                        text = "ⓘ  ${presetCapabilityText(stateHolder)}",
                        color = dshPalette().textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    pendingPreset?.let { preset ->
        DshAlertDialog(
            title = "设为全局默认预设？",
            message = "将“${agentPresetDisplayName(preset.id, preset.name)}”设为新会话的默认 Agent 预设。这会更新部署级设置，并同步影响 WebUI。",
            confirmLabel = "设为默认",
            onDismissRequest = { pendingPreset = null },
            onConfirm = {
                stateHolder.setDefault("agent-preset", preset.id)
                pendingPreset = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultModelSelectionScreen(
    stateHolder: AndroidSharedStateHolder,
    onBack: () -> Unit
) {
    var pendingChange by remember { mutableStateOf<PendingDefaultModelChange?>(null) }
    val groups = stateHolder.modelCatalog?.groups.orEmpty()
    val isLoading = "models" in stateHolder.defaultConfigurationLoadingKinds && groups.isEmpty()
    val isBusy = "save-default-model" in stateHolder.defaultConfigurationLoadingKinds
    LaunchedEffect(Unit) { stateHolder.ensureDefaultModelConfiguration() }

    SettingsSelectionScaffold(title = "默认模型", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsSelectionHeader(
                    title = "默认模型",
                    description = "为之后新建的会话设置默认模型与思考等级。这会更新部署级设置，同步影响 WebUI，运行中的会话保持启动时的配置。"
                )
            }
            when {
                isLoading -> item { SettingsLoadingState("正在读取模型列表…") }
                groups.isEmpty() -> item {
                    SettingsEmptyState(
                        title = "暂无可用模型",
                        description = "未能读取到模型列表，请检查网关连接后重试。"
                    )
                }
                else -> groups.forEach { group ->
                    item(key = "group-${group.id}") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                group.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dshPalette().textSecondary
                            )
                            group.models.forEach { model ->
                                val selected = stateHolder.defaultModel?.let {
                                    it.provider == group.id && it.model == model.id
                                } == true
                                DefaultModelCard(
                                    model = model,
                                    selected = selected,
                                    currentEffort = stateHolder.defaultModel?.reasoningEffort,
                                    busy = isBusy,
                                    onSelectModel = {
                                        pendingChange = pendingDefaultModelChange(
                                            group = group,
                                            model = model,
                                            currentEffort = stateHolder.defaultModel?.reasoningEffort
                                        )
                                    },
                                    onSelectEffort = { effort ->
                                        pendingChange = PendingDefaultModelChange(
                                            provider = group.id,
                                            providerName = group.name,
                                            model = model.id,
                                            modelName = model.name,
                                            reasoningEffort = effort.id,
                                            effortName = effort.name
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingChange?.let { change ->
        DshAlertDialog(
            title = "修改默认模型？",
            message = "将新会话的默认模型改为“${change.summary}”。这会更新部署级设置，并同步影响 WebUI。",
            confirmLabel = "确认修改",
            onDismissRequest = { pendingChange = null },
            onConfirm = {
                stateHolder.saveDefaultModel(change.provider, change.model, change.reasoningEffort)
                pendingChange = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSelectionScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val background = dshPalette().canvas
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TopBarCircleButton(
                        iconRes = R.drawable.ic_back_chevron,
                        description = "返回",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        },
        containerColor = background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun SettingsSelectionHeader(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            description,
            color = dshPalette().textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun SettingsLoadingState(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = dshPalette().textSecondary)
    }
}

@Composable
private fun SettingsEmptyState(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            color = dshPalette().textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AgentPresetCard(
    preset: GatewayAgentPreset,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    val shape = DshCardCornerRadius
    val palette = dshPalette()
    val enabled = !selected && !busy && preset.broken != true
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.textPrimary else palette.cardBorder,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                agentPresetDisplayName(preset.id, preset.name),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                preset.id,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    RoundedCornerShape(50)
                ).padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
            )
            Spacer(Modifier.weight(1f))
            if (selected) CurrentDefaultBadge()
        }
        Text(
            agentPresetDisplayDescription(preset.id, preset.description),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
        if (preset.broken == true) {
            Text(
                "⚠ 该预设存在配置错误，暂时不能设为默认值",
                color = DshColors.Orange,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DefaultModelCard(
    model: GatewayModelItem,
    selected: Boolean,
    currentEffort: String?,
    busy: Boolean,
    onSelectModel: () -> Unit,
    onSelectEffort: (GatewayReasoningEffort) -> Unit
) {
    val shape = DshCardCornerRadius
    val palette = dshPalette()
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.textPrimary else palette.cardBorder,
                shape = shape
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onSelectModel),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(model.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) CurrentDefaultBadge()
        }
        val efforts = model.reasoning?.efforts.orEmpty()
        if (efforts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "思考等级",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    fontSize = 12.sp
                )
                efforts.forEach { effort ->
                    val active = selected && effort.id == currentEffort
                    Text(
                        effort.name,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(
                                if (active) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            .clickable(enabled = !busy) { onSelectEffort(effort) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (active) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentDefaultBadge() {
    val palette = dshPalette()
    Text(
        "当前使用",
        modifier = Modifier.background(palette.textPrimary, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = palette.surface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

internal data class PendingDefaultModelChange(
    val provider: String,
    val providerName: String,
    val model: String,
    val modelName: String,
    val reasoningEffort: String?,
    val effortName: String?
) {
    val summary: String
        get() = listOfNotNull(providerName, modelName, effortName).joinToString(" · ")
}

internal fun pendingDefaultModelChange(
    group: GatewayModelGroup,
    model: GatewayModelItem,
    currentEffort: String?
): PendingDefaultModelChange {
    val efforts = model.reasoning?.efforts.orEmpty()
    val retainedEffort = currentEffort?.takeIf { current -> efforts.any { it.id == current } }
    val effortId = retainedEffort ?: model.reasoning?.defaultEffort
    return PendingDefaultModelChange(
        provider = group.id,
        providerName = group.name,
        model = model.id,
        modelName = model.name,
        reasoningEffort = effortId,
        effortName = effortId?.let { id -> efforts.firstOrNull { it.id == id }?.name }
    )
}

private fun presetCapabilityText(holder: AndroidSharedStateHolder): String = when {
    holder.agentPresetsAuthorable && holder.agentPresetsHasDocument ->
        "服务端支持编写自定义预设，并提供预设配置文档。"
    holder.agentPresetsAuthorable -> "服务端支持编写自定义 Agent 预设。"
    holder.agentPresetsHasDocument -> "服务端提供 Agent 预设配置文档。"
    else -> ""
}

@Composable
private fun SettingsSection(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = DshCardCornerRadius
    val palette = dshPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 20.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary
        )
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, cardShape)
                .border(1.dp, palette.cardBorder, cardShape)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) { content() }
        footer?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 20.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowAlpha = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha), fontSize = 16.sp)
        Spacer(Modifier.weight(1f).padding(horizontal = 6.dp))
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            )
        } else {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.52f else 0.3f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.30f else 0.15f),
                modifier = Modifier.padding(start = 8.dp).size(12.dp)
            )
        }
    }
}

@Composable
internal fun InterfaceStyleSettingsRow() {
    val appearance = LocalAppearanceSettings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsValueRow("界面", appearance.interfaceStyle.title) { expanded = true }
        DshSelectionPopup(
            expanded = expanded,
            options = InterfaceStyle.entries.map { DshSelectionOption(it.name, it.title) },
            selectedKey = appearance.interfaceStyle.name,
            onDismissRequest = { expanded = false },
            onSelect = { key ->
                appearance.selectInterfaceStyle(InterfaceStyle.valueOf(key))
                expanded = false
            }
        )
    }
}

@Composable
internal fun NotificationSettingsRow() {
    val notifications = LocalAgentNotificationSettings.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_notify_in_background),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
        Spacer(Modifier.weight(1f).padding(horizontal = 6.dp))
        Switch(
            checked = notifications.notifyInBackground,
            onCheckedChange = { notifications.updateNotifyInBackground(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
internal fun LanguageSettingsRow() {
    val appearance = LocalAppearanceSettings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsValueRow("语言", appearance.language.title) { expanded = true }
        DshSelectionPopup(
            expanded = expanded,
            options = AppLanguage.entries.map { DshSelectionOption(it.name, it.title) },
            selectedKey = appearance.language.name,
            onDismissRequest = { expanded = false },
            onSelect = { key ->
                appearance.selectLanguage(AppLanguage.valueOf(key))
                expanded = false
            }
        )
    }
}

@Composable
private fun PermissionSettingsRow(
    value: String,
    selectedPermission: String?,
    options: List<GatewayPermissionOption>,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPermissionSelected: (String) -> Unit
) {
    Box {
        SettingsValueRow(
            title = "权限",
            value = value,
            enabled = enabled,
            onClick = { onExpandedChange(true) }
        )
        DshSelectionPopup(
            expanded = expanded,
            options = options.map { permission ->
                DshSelectionOption(
                    permission.value,
                    permission.name,
                    when (permission.value) {
                        "read-only" -> R.drawable.ic_permission_read
                        "workspace-write" -> R.drawable.ic_menu_check
                        else -> R.drawable.ic_permission_warning
                    }
                )
            },
            selectedKey = selectedPermission,
            onDismissRequest = { onExpandedChange(false) },
            onSelect = { key ->
                onExpandedChange(false)
                onPermissionSelected(key)
            }
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
private fun GatewayEndpointRow(stateHolder: AndroidSharedStateHolder) {
    BasicTextField(
        value = stateHolder.endpoint,
        onValueChange = { stateHolder.endpoint = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (stateHolder.endpoint.isBlank()) {
                    Text(
                        "ws://host:3080/ws/mobile",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun GatewayStatusRow(stateHolder: AndroidSharedStateHolder) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(9.dp).background(statusColor(stateHolder), CircleShape))
        Text(connectionLabel(stateHolder), fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        stateHolder.gatewayState.serverPort?.let { port ->
            Text(
                "Port $port",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 16.sp
        )
    }
}


/**
 * 是否允许发起**需要网关**的动作。
 *
 * 用 [dshBlocksNetworkActions] 而不是 `== CONNECTED`：前者是唯一语义源规定的判据
 * （`DshConnectionStateUi.kt:53-56`），且不会把 SUSPENDED 等相位误判成「未连接」。
 */
private fun online(holder: AndroidSharedStateHolder) =
    !holder.gatewayState.connection.dshBlocksNetworkActions

private fun selectedPreset(holder: AndroidSharedStateHolder): String {
    val id = holder.agentPresetDefault ?: return "未读取"
    val gatewayName = holder.agentPresets.firstOrNull { it.id == id }?.name
    return agentPresetDisplayName(id, gatewayName)
}

private fun selectedModel(holder: AndroidSharedStateHolder): String {
    val selection = holder.defaultModel ?: return "未读取"
    val item = holder.modelCatalog?.groups?.flatMap { it.models }?.firstOrNull { it.id == selection.model }
    val base = item?.name ?: when (selection.model) {
        "deepseek-chat" -> "DeepSeek Chat"
        "deepseek-reasoner" -> "DeepSeek Reasoner"
        else -> selection.model
    }
    val effortName = selection.reasoningEffort?.let { effortId ->
        item?.reasoning?.efforts?.firstOrNull { it.id == effortId }?.name
            ?: when (effortId.lowercase()) {
                "low" -> "低"
                "medium" -> "中"
                "high" -> "高"
                else -> effortId.replaceFirstChar(Char::uppercase)
            }
    }
    return effortName?.let { "$base · $it" } ?: base
}

/**
 * 连接状态文案**必须**复用 [dshConnectionDetailText]，不要在这里再写一遍 when。
 *
 * `DshConnectionStateUi.kt:10-11` 明确要求集中映射：此前本页私有了一份，
 * 结果 SUSPENDED 在三个地方分别叫「已挂起 / 已暂停 / 未连接」，CONNECTING 叫
 * 「连接中」而别处是「正在连接…」。
 */
private fun connectionLabel(holder: AndroidSharedStateHolder): String =
    dshConnectionDetailText(holder.gatewayState.connection) ?: "已连接"

/** 权限文案统一走 [runtimePermissionTitle]，避免同一取值在不同页面显示成不同字符串。 */
private fun permissionName(value: String?) = runtimePermissionTitle(value)


/** 连接点颜色同样复用唯一语义源，避免把过渡态画成灰色、把等待网络画成非 attention 色。 */
@Composable
private fun statusColor(holder: AndroidSharedStateHolder): androidx.compose.ui.graphics.Color =
    dshConnectionDotColor(holder.gatewayState.connection, dshPalette())
