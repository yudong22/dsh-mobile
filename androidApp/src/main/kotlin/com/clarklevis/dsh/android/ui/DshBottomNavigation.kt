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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 底部主导航的条目。
 *
 * 左三项是页面目的地（任务列表 / 项目 / 定时任务），右两项是全局动作（设置 / 扫码）——
 * 设置与扫码原先在首页顶栏，为腾出顶部空间并统一到拇指可达区域而下移。
 * 「专家」与「资料库」已移除：前者改由设置页的 Agent 预设入口承担，后者不再提供。
 *
 * 「任务列表」而不是「任务」：该 Tab 的落点是任务列表页，用「任务列表」能区别于抽屉里
 * 同名的「任务 (n)」区块标题。`label` 同时是读屏用的 contentDescription。
 *
 * 视觉比例对齐改版截图：整条 84dp 高、图标 26dp、标签 11sp、选中态用主文字色、未选中用三级文字色。
 */
internal enum class DshTab(
    val label: String,
    val iconRes: Int,
    val testTag: String
) {
    TASKS("任务列表", R.drawable.ic_tab_tasks, "tab-tasks"),
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
    // 顺序很关键：`navigationBarsPadding()` 必须在 `height()` 之前、且在**外层**应用，
    // 让 [DshBottomBarHeight] 是「内容高度」，系统导航栏 inset 额外加在下方。
    //
    // 反例（曾导致真机上底栏只剩一个标签）：`.navigationBarsPadding().height(...)`
    // 让内容高度成为「含 inset 的总高」。真机导航栏 inset 为 20dp（60px @3.0x）时
    // 内容区只剩 64dp，小于 DshTabItem 需要的高度，于是图标与文字被裁到屏幕外。
    Box(modifier.fillMaxWidth().background(palette.canvas).navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DshBottomBarHeight)
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
            .height(DshBottomBarItemHeight)
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
            // 26 -> 23dp：底栏整体调矮后��标需同步收小，否则与标签挤在一起。
            modifier = Modifier.size(23.dp),
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
 * 打开侧边抽屉的圆钮：三条横线。
 *
 * 首页与会话页（任务详情）共用同一个组件——两处的动作完全相同（打开同一个抽屉快速切换任务），
 * 外观不一致会让人以为是两个不同入口。
 *
 * 此前首页按钮只画了两条横线（`repeat(2)`），与「抽屉/汉堡」的通用识别形状不符；
 * 这里统一为三条，两处一起生效。
 */
@Composable
internal fun DshDrawerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DshPageHeaderCircleButtonSize,
    testTag: String = "drawer-button"
) {
    val palette = dshPalette()
    Box(
        modifier = modifier
            .size(size)
            .background(palette.surface, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开侧边栏" }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    Modifier.size(width = 19.dp, height = 2.dp)
                        .background(palette.textPrimary, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * 底栏 + 「扫码」认证菜单的组合外壳。
 *
 * 「扫码」不是页面目的地而是全局动作（在原位展开菜单），所以它与底栏强绑定：
 * 哪个页面挂了底栏，哪里就必须能开这个菜单，否则表现为「点了没反应」。
 * 把它封装在这里，各页面只需把本组件放进 `Scaffold(bottomBar = ...)`，
 * 不必各自重复一遍菜单与锚点逻辑。
 */
@Composable
internal fun DshBottomBarHost(
    selected: DshTab,
    onSelectTab: (DshTab) -> Unit,
    onScanRequested: () -> Unit,
    onManualEntryRequested: () -> Unit
) {
    // 菜单展开态是纯 UI 状态，随屏幕重建保留即可。
    var showAuthMenu by rememberSaveable { mutableStateOf(false) }
    Box {
        DshBottomTabBar(
            selected = selected,
            onSelect = { tab ->
                if (tab == DshTab.SCAN) showAuthMenu = true else onSelectTab(tab)
            }
        )
        // 菜单锚在底栏左上角区域；向上弹以免超出屏幕底部。
        GatewayAuthenticationMenuContent(
            expanded = showAuthMenu,
            onDismissRequest = { showAuthMenu = false },
            onScan = {
                showAuthMenu = false
                onScanRequested()
            },
            onManualEntry = {
                showAuthMenu = false
                onManualEntryRequested()
            }
        )
    }
}

/**
 * 截图首页的「新建任务」大胶囊：白色底、极细描边、居中图标 + 文字。
 *
 * 圆角用 [DshCardCornerRadius]（20dp）而不是自身高度的一半（26.5dp）：首页的卡片与
 * 会话列表卡都是 20dp，按钮独自是全圆角会让它看起来来自另一套体系。
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
            .background(palette.surface, DshCardCornerRadius)
            .border(1.dp, palette.cardBorder, DshCardCornerRadius)
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
