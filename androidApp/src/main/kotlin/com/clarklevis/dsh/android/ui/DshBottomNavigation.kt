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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 底部主导航的条目。
 *
 * 三个页面目的地（任务列表 / 项目 / 定时任务）+ 一个账户页（我的）。
 * 「我的」而不是「设置」：该页是「账户 + 个人偏好」，页面标题与应用内各处文案
 * 早已统一为「我的」，底栏沿用旧名会让同一目的地出现两种叫法。
 * 「扫码」原先占第五格，本轮 UI 统一后移到任务/项目页右上角的认证菜单按钮
 * （[DshScanMenuButton]）——配对是设备级操作，放在设备上下文所在的页面更顺。
 * 「专家」与「资料库」已移除：前者改由「我的」页的 Agent 预设入口承担，后者不再提供。
 *
 * 「任务列表」而不是「任务」：该 Tab 的落点是任务列表页，用「任务列表」能区别于抽屉里
 * 同名的「任务 (n)」区块标题。`label` 同时是读屏用的 contentDescription。
 */
internal enum class DshTab(
    val label: String,
    val iconRes: Int,
    val testTag: String
) {
    TASKS("任务列表", R.drawable.ic_tab_tasks, "tab-tasks"),
    PROJECTS("项目", R.drawable.ic_tab_projects, "tab-projects"),
    SCHEDULES("定时任务", R.drawable.ic_drawer_schedule, "tab-schedules"),
    SETTINGS("我的", R.drawable.ic_settings, "tab-settings")
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
 * 页头圆钮的**共用表面**：投影 + 圆形裁切 + surface 底 + 描边。
 *
 * 抽出来是因为页头左侧现在并排渲染「返回 + 抽屉」两个圆钮（v1.9.7）。
 * 此前返回钮走 [TopBarCircleButton]（带投影和 0.7dp 描边），抽屉钮只有一个
 * 纯色圆底——两者从不同框出现，所以看不出差异；一旦并排，深浅不一的观感就暴露了。
 * 让两处引用同一个修饰符，以后调一处即可（不需要分别改两个文件）。
 */
internal fun Modifier.dshHeaderCircleButtonSurface(palette: DshPalette): Modifier = this
    .dropShadow(
        shape = CircleShape,
        shadow = Shadow(
            radius = 12.dp,
            spread = 0.dp,
            color = Color.Black.copy(alpha = if (palette.isDark) 0.24f else 0.07f),
            offset = DpOffset(x = 0.dp, y = 4.dp)
        )
    )
    .clip(CircleShape)
    .background(palette.surface)
    .border(0.7.dp, palette.cardBorder, CircleShape)

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
            // 与返回钮共用同一表面，避免同一槽位里两个圆钮深浅不一。
            .dshHeaderCircleButtonSurface(palette)
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
 * 底栏 + 页面级切换的组合外壳（扫码移除后仅剩导航职责）。
 *
 * 底栏是 NavHost 的兄弟节点：页面切换时它保持不动（符合「常驻导航」的预期）。
 */
@Composable
internal fun DshBottomBarHost(
    selected: DshTab,
    onSelectTab: (DshTab) -> Unit
) {
    DshBottomTabBar(
        selected = selected,
        onSelect = onSelectTab
    )
}

/**
 * 任务/项目页右上角的「配对设备」入口（扫码 Tab 移除后的落点）。
 *
 * 40dp 圆钮（[DshPageHeaderCircleButtonSize] token）+ 点击弹出认证菜单
 * （扫描二维码 / 手动输入配对信息）。菜单锚点在按钮下方右侧。
 * 放在设备上下文所在的页面（任务/项目）而不是底栏：配对是设备级操作。
 */
internal data class ScanActions(
    val onScanRequested: () -> Unit,
    val onManualEntryRequested: () -> Unit
)

/** 外壳提供的配对动作；未提供的页面（我的/定时任务等）读取为 null，不渲染入口。 */
internal val LocalScanActions = staticCompositionLocalOf<ScanActions?> { null }

@Composable
internal fun DshScanMenuButton(
    onScanRequested: () -> Unit,
    onManualEntryRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 菜单展开态是纯 UI 状态，随屏幕重建保留即可。
    var showAuthMenu by rememberSaveable { mutableStateOf(false) }
    val palette = dshPalette()
    Box(modifier) {
        TopBarCircleButton(
            iconRes = R.drawable.ic_gateway_auth,
            description = "配对设备",
            onClick = { showAuthMenu = true }
        )
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
            },
            offset = DpOffset(x = (-12).dp, y = 8.dp)
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
