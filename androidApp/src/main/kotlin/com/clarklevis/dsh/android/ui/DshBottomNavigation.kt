package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 底部主导航的条目。
 *
 * 左三项是页面目的地（任务 / 项目 / 定时任务），右两项是全局动作（设置 / 扫码）——
 * 设置与扫码原先在首页顶栏，为腾出顶部空间并统一到拇指可达区域而下移。
 * 「专家」与「资料库」已移除：前者改由设置页的 Agent 预设入口承担，后者不再提供。
 *
 * 视觉比例对齐改版截图：整条 84dp 高、图标 26dp、标签 11sp、选中态用主文字色、未选中用三级文字色。
 */
internal enum class DshTab(
    val label: String,
    val iconRes: Int,
    val testTag: String
) {
    TASKS("任务", R.drawable.ic_tab_tasks, "tab-tasks"),
    PROJECTS("项目", R.drawable.ic_tab_projects, "tab-projects"),
    SCHEDULES("定时任务", R.drawable.ic_drawer_schedule, "tab-schedules"),
    SETTINGS("设置", R.drawable.ic_settings, "tab-settings"),
    SCAN("扫码", R.drawable.ic_gateway_auth, "tab-scan")
}

/**
 * 截图中的底部标签栏：与页面同底色，无分隔线，五等分。
 *
 * [selected] 为 null 时不强调任何一项（例如已经进入二级页面）。
 */
@Composable
internal fun DshBottomTabBar(
    selected: DshTab?,
    onSelect: (DshTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.canvas)
            .navigationBarsPadding()
            .height(84.dp)
            .padding(horizontal = 4.dp)
            .testTag("bottom-tab-bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DshTab.entries.forEach { tab ->
            DshTabItem(
                tab = tab,
                selected = tab == selected,
                palette = palette,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun DshTabItem(
    tab: DshTab,
    selected: Boolean,
    palette: DshPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 选中态使用主文字色（截图里的纯黑图标），未选中态使用三级灰，保证 4.5:1 以上可读。
    val tint = if (selected) palette.textPrimary else palette.textTertiary
    Column(
        modifier = modifier
            .height(72.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = tab.label
            }
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(tint)
        )
        Text(
            text = tab.label,
            color = tint,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/**
 * 截图首页顶部的品牌胶囊：左侧圆形菜单按钮 + 两行标题（主标题 + 设备/工作空间副标题）。
 *
 * 标题区域整体可点击，点击弹出「任务运行设置」面板。
 */
@Composable
internal fun DshBrandHeader(
    title: String,
    subtitle: String,
    connection: GatewayConnectionState,
    onOpenDrawer: () -> Unit,
    onOpenRuntimeSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val palette = dshPalette()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .background(palette.surface, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenDrawer)
                .semantics { contentDescription = "打开侧边栏" }
                .testTag("brand-drawer-button"),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(2) {
                    Box(
                        Modifier.size(width = 19.dp, height = 2.dp)
                            .background(palette.textPrimary, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 14.dp).weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onOpenRuntimeSettings)
                .semantics { contentDescription = "任务运行设置" }
                .testTag("brand-runtime-settings-button")
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    // 品牌名比参考截图的标题长得多（"DeepSeek Harness" vs "WorkBuddy"），
                    // 因此按宽度自动降档字号而不是省略成 "DeepSeek …"；
                    // 仍未放下时才退回尾部省略，避免任何情况下溢出。
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 17.sp,
                        maxFontSize = 24.sp,
                        stepSize = 1.sp
                    ),
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 连接状态点：已连接绿、连接中琥珀（并转圈）、失败红、未连接灰。
                    // 原先这里是静态的云图标，看不出连接状态。
                    StatusIndicatorDot(
                        color = dshConnectionDotColor(connection, palette),
                        modifier = Modifier.size(7.dp).testTag("header-connection-dot"),
                        glowing = connection == GatewayConnectionState.CONNECTED
                    )
                    Text(
                        text = subtitle,
                        modifier = Modifier.weight(1f, fill = false),
                        color = palette.textTertiary,
                        // 网关主机名可能很长（如 denisMacBook-M5.local），同样按宽度降档，
                        // 尽量把 `设备 | 工作空间` 两段都展示出来。
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 11.sp,
                            maxFontSize = 14.sp,
                            stepSize = 0.5.sp
                        ),
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    // 过渡/失败态追加状态词，已连接是常态就不再赘述。
                    dshConnectionDetailText(connection)?.let { detail ->
                        Text(
                            text = detail,
                            color = dshConnectionDotColor(connection, palette),
                            fontSize = 12.sp,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            modifier = Modifier.testTag("header-connection-status")
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(palette.textTertiary)
                    )
                }
            }
        }
        trailing()
    }
}

/**
 * 截图首页的「新建任务」大胶囊：白色底、极细描边、居中图标 + 文字。
 */
@Composable
internal fun DshNewTaskButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "new-task-button"
) {
    val palette = dshPalette()
    // 禁用态降低图标/文字与背景的对比度，明确表达「此刻点不了」。
    val contentColor = if (enabled) palette.textPrimary else palette.textTertiary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(palette.surface, RoundedCornerShape(27.dp))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(27.dp))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(contentColor)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 9.dp),
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

/**
 * 截图首页搜索框：次级面胶囊底、无描边、三级灰占位。
 */
@Composable
internal fun DshSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(palette.surfaceMuted, RoundedCornerShape(22.dp))
            .padding(horizontal = 15.dp)
            .testTag("workspace-session-search"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            colorFilter = ColorFilter.tint(palette.textTertiary)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = palette.textPrimary,
                fontSize = 16.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            cursorBrush = SolidColor(palette.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            color = palette.textTertiary,
                            fontSize = 16.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 抽屉底部账户卡：56dp 圆形头像 + 名称 + `套餐 | 配额`，对位参考截图。
 * 套餐用金褐色（截图取样 #AD9245），配额前带一枚用量菱形标记。
 */
@Composable
internal fun DshAccountCard(
    name: String,
    planLabel: String,
    quotaLabel: String?,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(top = 14.dp, bottom = 20.dp)
            .testTag("drawer-account-card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(palette.surfaceMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).ifEmpty { "D" }.uppercase(),
                color = palette.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                color = palette.textPrimary,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = planLabel,
                    color = ACCOUNT_PLAN_GOLD,
                    fontSize = 16.sp,
                    maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                if (quotaLabel != null) {
                    Box(
                        Modifier.size(width = 1.dp, height = 14.dp)
                            .background(palette.divider)
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_tab_projects),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        colorFilter = ColorFilter.tint(palette.textSecondary)
                    )
                    Text(
                        text = quotaLabel,
                        color = palette.textTertiary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
    }
}

/** 截图账户卡套餐文字的金褐色（取样 #AD9245 附近）。 */
private val ACCOUNT_PLAN_GOLD = Color(0xFFAD9245)
