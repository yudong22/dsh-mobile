package com.clarklevis.dsh.android.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 卡片体系的标准圆角：首页各卡片（空态、会话列表卡）与主行动按钮共用。
 *
 * 单独抽出来是为了让「新建任务」这类不是卡片的控件也能对齐同一套圆角，而不是各自
 * 写死自己的值（按钮此前是 `height / 2` 的全圆角，看起来来自另一套体系）。
 */
internal val DshCardCornerRadius = RoundedCornerShape(20.dp)

/**
 * 卡片体系的标准**间距**：页面水平留白与区块间距。
 *
 * 此前这些值散落在每个页面的 `padding(horizontal = 18.dp)` 与各类 `Spacer(height(20.dp))`
 * 里，互相抄各自的数字，导致首页 18dp、设置页 20dp、定时任务 20dp 三种 gutter 并存。
 * 抽出来之后，调整整页留白只需改一处。
 */
internal val DshScreenHorizontalPadding = 18.dp
internal val DshSectionSpacing = 20.dp

/**
 * 卡片体系的标准**字号**。
 *
 * 页面标题 / 区块标题 / 正文 / 辅助文字四级。此前同为「区块标题」的文案在首页是
 * 17sp、项目页 15sp、设置页 17sp，视觉密度不一致。
 */
internal val DshSectionTitleFontSize = 17.sp

/**
 * 页面顶栏标题字号：**所有页面统一 18sp**。
 *
 * 此前四个页头各写一个值——项目 18sp、定时任务 20sp、设置 17sp，同一个层级
 * （一级页面标题）三种字号，切换 Tab 时标题会明显跳动。顶栏标题与页内区块标题
 * （[DshSectionTitleFontSize]）是两个层级，不要混用。
 */
internal val DshPageTitleFontSize = 18.sp

/**
 * 页头几何：**所有页面共用**，任何页面都不应再自己写高度或按钮尺寸。
 *
 * 此前三种页头各写各的：首页品牌头无固定高度、左侧 47dp 汉堡、标题 17–20sp 自适应；
 * 二级页 56dp + 46dp 返回钮 + 18sp。切页面时页头会整体跳动。
 * 这里把「页头内容高度」「左侧圆钮直径」「水平内边距」收敛成一组 token。
 *
 * 圆钮 40dp 而不是 46dp：页头内容高 56dp，40dp 的按钮上下各留 8dp 呼吸空间；
 * 46dp 只剩 5dp，视觉上「顶满」页头（真机截图对比确认过）。左侧返回钮、右侧
 * 「更多」/抽屉钮都用同一 token——此前返回 46dp、抽屉 40dp 并排出现时大小不一。
 */
internal val DshPageHeaderHeight = 56.dp
internal val DshPageHeaderCircleButtonSize = 40.dp
internal val DshPageHeaderHorizontalPadding = 12.dp

/**
 * 页头左侧相邻圆钮之间的水平间距。
 *
 * 任务详情页左侧并排放「返回 + 抽屉」两个 40dp 钮，需要 8dp 间隔才读得出是两个独立入口
 * （设计稿 `Docs/design/conversation-detail.html:36` 的 `.side-slot{gap:8px}`）。
 * 左右槽位按**同一公式**取宽，标题因此仍然整屏居中。
 */
internal val DshPageHeaderButtonGap = 8.dp

/**
 * 页头单侧槽位的宽度：容纳 [DshPageHeaderCircleButtonSize] 个圆钮所需的宽度。
 *
 * 抽成函数而不是各处手写，是因为**左右两侧必须用同一个值**——只要有一侧偏宽，
 * 标题就会被推离屏幕中线（真机实测过定时任务页标题偏左 28dp）。
 * 调用方只需告诉它「这一侧有几个钮」。
 */
internal fun dshPageHeaderSlotWidth(buttonCount: Int): androidx.compose.ui.unit.Dp {
    val count = buttonCount.coerceAtLeast(0)
    if (count <= 1) return DshPageHeaderCircleButtonSize
    return DshPageHeaderCircleButtonSize * count + DshPageHeaderButtonGap * (count - 1)
}

/**
 * 底部导航栏几何：**内容高度**（不含系统导航栏 inset）。
 *
 * 从 84dp / 单项 72dp 调矮到 68dp / 58dp：底栏是常驻 chrome，此前占掉的高度
 * 明显挤压内容区（任务详情页尤其明显）。图标同步 26→23dp、标签字号不变，
 * 仍满足 44dp 最小触控目标（单项 58dp × 五等分宽度远大于 44dp）。
 */
internal val DshBottomBarHeight = 68.dp
internal val DshBottomBarItemHeight = 58.dp

/** 页头副标题字号（首页的「设备 | 项目」）。固定值，不做自适应降档。 */
internal val DshHeaderSubtitleFontSize = 12.sp

/**
 * 页头副标题槽位高度：**所有页面共用**。
 *
 * 首页有副标题（设备 | 项目）、其余页面没有。如果按内容条件渲染，标题在两类页面里
 * 的垂直位置会差一行；固定槽位（无副标题时留空）让标题基线在四个页面完全一致。
 */
internal val DshHeaderSubtitleSlotHeight = 16.dp
internal val DshBodyFontSize = 15.sp
internal val DshCaptionFontSize = 13.sp

/**
 * 「我的」页与子设置页的行几何。
 *
 * 字号**对齐 App 的通用层级**（[DshBodyFontSize] 15sp 正文 / [DshCaptionFontSize] 13sp 辅助），
 * 不再单独放大：此前照参考图取 17sp/15sp，比列表页（15sp/12sp）大两档，
 * 四个 Tab 之间切换时文字明显跳一档（真机截图对比确认）。
 * 行高保留 56dp 的宽松感——那是设置项该有的疏朗，字号则与全局一致。
 */
internal val DshSettingsRowMinHeight = 56.dp
internal val DshSettingsTitleFontSize = DshBodyFontSize
internal val DshSettingsValueFontSize = DshCaptionFontSize
internal val DshSettingsChevronSize = 16.dp

/**
 * 悬浮新建按钮（参考图 3 右下角 FAB）：56dp 圆 + 24dp 图标。
 * 颜色取 palette.primary（随主题），不写死绿色。
 *
 * 右侧与底部**分开**取值：底栏常驻时 FAB 悬在底栏之上，两者共用 20dp 会让
 * 视觉下边距偏大（底栏 68dp + 20dp）。底部单独收到 12dp，贴近底栏又不压住它。
 */
internal val DshFabSize = 56.dp
internal val DshFabIconSize = 24.dp
internal val DshFabEdgePadding = 20.dp

/** FAB 距底部的间距：比右侧更小，避免与底栏叠加后视觉留白过大。 */
internal val DshFabBottomPadding = 12.dp

/**
 * 品牌色与固定语义色。
 *
 * 本轮 UI 改版把界面从「深色玻璃」切换为截图所定义的浅色卡片体系，
 * 但我方产品语义（会话/专家/资料库/定时任务/项目）保持不变；
 * 深色模式保留为同一套语义 token 的深色推导，不删功能。
 */
internal object DshColors {
    // 品牌/状态色：与 iOS 基准保持一致，不随明暗主题改变。
    val Ocean = Color(0xFF2E6BE6)
    val Purple = Color(0xFF7A54C7)
    val Orange = Color(0xFFF07D14)
    val Amber = Color(0xFFFFAD1F)
    val Success = Color(0xFF2EB85C)
    val Danger = Color(0xFFFF3B30)
    /** 浅色下当作**正文颜色**使用的错误红：`Danger` 在白底只有 3.55:1，不满足正文对比度。 */
    val DangerLight = Color(0xFFB3261E)
    /** 深色画布上的错误红：`Danger` 在 `#1C1C1E` 上为 4.80:1。 */
    val DangerDark = Color(0xFFFF6B63)
    /** 深色画布上的次级强调色；`Purple` 在深色底上只有 3.18:1。 */
    val PurpleDark = Color(0xFFB69BFF)

    // 旧深色命名保留给仍在引用的实现，取值改为新体系下的等价色。
    val Navy = Color(0xFF06172B)
    val NavyRaised = Color(0xFF131315)
    val Mist = Color(0xFF8B8B8B)
    val Ink = Color(0xFF1F1F1F)
    val Paper = Color(0xFFF8F8F8)

    // 截图取色：浅色画布 / 抽屉画布 / 卡片。
    val CanvasLight = Color(0xFFF8F8F8)
    val DrawerLight = Color(0xFFFAFAFA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val MutedLight = Color(0xFFF0F0F0)
    val BorderLight = Color(0xFFEAEAEA)
    val DividerLight = Color(0xFFEBEBEB)
    val TextPrimaryLight = Color(0xFF1F1F1F)
    val TextSecondaryLight = Color(0xFF8B8B8B)
    val TextTertiaryLight = Color(0xFF9A9A9A)
    val AccentLight = Color(0xFF1FA07E)

    /**
     * 选中行的底色（浅色）。
     *
     * **中性冷灰、不带蓝**（用户明确要求）：选中态的主信号是行首 3dp 强调条与
     * 行尾勾，底色只做「这一行被选中」的轻微区分。此前用主色系浅蓝 `#E8EFFE`，
     * 在卡片上像一个色块跳出来，喧宾夺主。
     *
     * 取**实色**而非 `primary.copy(alpha)`：透明叠加结果随底层背景漂移
     * （叠在 `#F8F8F8` 上会发灰紫）。与白卡片 1.12:1、正文 14.7:1——可辨但不抢眼。
     */
    val SelectedRowLight = Color(0xFFF0F2F5)
    val SelectedRowDark = Color(0xFF262B33)

    /**
     * 悬浮主行动按钮的**禁用**底色（浅色）。
     *
     * 不用「主色降透明度」：`alpha 0.38` 叠在画布上得到 `#ACC3F1`，
     * 明度被画布抬高的同时色相也发灰——「变淡」与「变脏」分不开。
     * 这里给一个**预先调好的浅蓝**：明确表达「这是可点按钮，但现在点不了」，
     * 而不是看起来像坏掉的控件。深色同理，给暗蓝而不是降透明度。
     */
    val DisabledPrimaryLight = Color(0xFFBBD1FB)
    val DisabledPrimaryDark = Color(0xFF2E4270)

    val CanvasDark = Color(0xFF0E0E10)
    val DrawerDark = Color(0xFF151517)
    val SurfaceDark = Color(0xFF1C1C1E)
    val MutedDark = Color(0xFF26262A)
    val BorderDark = Color(0xFF303034)
    val DividerDark = Color(0xFF2A2A2E)
    val TextPrimaryDark = Color(0xFFF2F2F2)
    val TextSecondaryDark = Color(0xFF9A9A9E)
    val TextTertiaryDark = Color(0xFF8A8A8E)
    val AccentDark = Color(0xFF3FD0A4)
}

/**
 * 一套语义配色 token。所有页面（首页、抽屉、对话、设置、弹层）都从这里取色，
 * 避免再出现「深色底配黑字」这类只在单一页面修好的可读性问题。
 */
internal data class DshPalette(
    val isDark: Boolean,
    /** 页面画布底色。 */
    val canvas: Color,
    /** 侧边抽屉底色，比画布略亮/略暗一档以形成层次。 */
    val drawerCanvas: Color,
    /** 卡片、气泡、按钮等前景表面。 */
    val surface: Color,
    /** 次级表面：标签、次级按钮、输入框占位底。 */
    val surfaceMuted: Color,
    /** 描边。 */
    val border: Color,
    /** 列表分隔线。 */
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** 品牌强调色：账户头像、成功态点睛。 */
    val accent: Color,
    /** 主操作色：发送键、可点的 FAB。 */
    val primary: Color,
    /**
     * 分组列表**选中行**的底色（中性冷灰，不带蓝）。
     * 取实色而不是 `primary.copy(alpha)`——透明叠加随底层背景漂移。
     */
    val selectedRow: Color,
    /**
     * 主行动按钮的**禁用**底色，见 [DshColors.DisabledPrimaryLight]。
     * 与 [primary] 成对使用：可点取 primary、不可点取本值。
     */
    val disabledPrimary: Color
) {
    /** 覆盖在 canvas 上的浮层阴影，浅色下更淡。 */
    val floatingShadow: Color get() = Color.Black.copy(alpha = if (isDark) 0.42f else 0.08f)

    /** 卡片描边：浅色下几乎不可见，深色下用更亮的边。 */
    val cardBorder: Color get() = border

    /**
     * 叠在 [primary] 之上的前景色，与 [primary] **成对**取值。
     *
     * 与 [paletteColorScheme] 的 onPrimary 同一推导，抽出来是为了让「主色容器 + 主色上的
     * 文字」这类配对必须同时取自 palette：此前按钮用固定 `DshColors.Ocean` 当容器、
     * 却拿主题 `onPrimary` 当文字，暗色下对比度只有约 3.53:1。
     */
    val onPrimary: Color get() = if (isDark) surface else Color.White
}

internal object DshPalettes {
    val Light = DshPalette(
        isDark = false,
        canvas = DshColors.CanvasLight,
        drawerCanvas = DshColors.DrawerLight,
        surface = DshColors.SurfaceLight,
        surfaceMuted = DshColors.MutedLight,
        border = DshColors.BorderLight,
        divider = DshColors.DividerLight,
        textPrimary = DshColors.TextPrimaryLight,
        textSecondary = DshColors.TextSecondaryLight,
        textTertiary = DshColors.TextTertiaryLight,
        accent = DshColors.AccentLight,
        primary = DshColors.Ocean,
        selectedRow = DshColors.SelectedRowLight,
        disabledPrimary = DshColors.DisabledPrimaryLight
    )

    val Dark = DshPalette(
        isDark = true,
        canvas = DshColors.CanvasDark,
        drawerCanvas = DshColors.DrawerDark,
        surface = DshColors.SurfaceDark,
        surfaceMuted = DshColors.MutedDark,
        border = DshColors.BorderDark,
        divider = DshColors.DividerDark,
        textPrimary = DshColors.TextPrimaryDark,
        textSecondary = DshColors.TextSecondaryDark,
        textTertiary = DshColors.TextTertiaryDark,
        accent = DshColors.AccentDark,
        primary = Color(0xFF7EA8FF),
        selectedRow = DshColors.SelectedRowDark,
        disabledPrimary = DshColors.DisabledPrimaryDark
    )
}

/**
 * 与 `LocalAppearanceSettings` 一样默认报错：静默退回浅色会让「深色模式下渲染成浅色」
 * 这类问题毫无征兆地出现（例如新增的 Dialog/Popup/测试忘了包 `DshTheme`）。
 * 生产路径上唯一提供者是 `DshTheme` 自身。
 */
internal val LocalDshPalette = staticCompositionLocalOf<DshPalette> {
    error("DshPalette must be provided by DshTheme")
}

/** 当前有效配色。ViewModel/纯函数请显式传参，Composable 内直接调用。 */
@Composable
internal fun dshPalette(): DshPalette = LocalDshPalette.current

@Composable
internal fun StatusIndicatorDot(
    color: Color,
    modifier: Modifier = Modifier,
    glowing: Boolean = false
) {
    val decoratedModifier = if (glowing) {
        modifier.dropShadow(
            shape = CircleShape,
            shadow = Shadow(
                radius = 5.dp,
                spread = 1.dp,
                color = color.copy(alpha = 0.28f),
                offset = DpOffset(0.dp, 0.dp)
            )
        )
    } else {
        modifier
    }
    Box(decoratedModifier.background(color, CircleShape))
}

/**
 * 把语义 token 映射到 Material3 的角色。
 *
 * 注意 `onPrimary`/`error`/`secondary` 都**按明暗分档**，不能两个分支写同一个字面量：
 * 深色下 `primary` 是浅蓝 `#7EA8FF`，配白字只有 2.35:1（远低于 4.5:1 正文下限），
 * 必须用深色前景（`palette.surface` → 7.24:1）；`error` 用 `#FF3B30` 在白底/浅底上
 * 只有 3.3~3.6:1，而它被当作**正文颜色**用在大量错误文案上，因此浅色下改用 M3 的
 * `#B3261E`（6.54:1），深色下保留亮红（4.80:1）。
 */
private fun paletteColorScheme(palette: DshPalette): ColorScheme {
    val onPrimary = if (palette.isDark) palette.surface else Color.White
    // 深色下沿用 HEAD 的亮紫（3.18:1 → 7.39:1），避免深色模式可读性倒退。
    val secondary = if (palette.isDark) DshColors.PurpleDark else DshColors.Purple
    val error = if (palette.isDark) DshColors.DangerDark else DshColors.DangerLight
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onPrimary,
        background = palette.canvas,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceMuted,
        onSurfaceVariant = palette.textSecondary,
        // 未显式设置的容器角色会退回 M3 基线（浅紫灰），与浅色卡片体系冲突——
        // 例如未用 DshAlertDialog 的原生 AlertDialog 会拿到 surfaceContainerHigh。
        surfaceContainerHigh = palette.surfaceMuted,
        surfaceContainerHighest = palette.surfaceMuted,
        surfaceContainer = palette.surface,
        surfaceContainerLow = palette.surface,
        surfaceContainerLowest = palette.surface,
        outline = palette.border,
        outlineVariant = palette.divider,
        error = error,
        onError = onPrimary
    )
}

@Composable
internal fun DshTheme(
    appearance: AppearanceSettings = rememberAppearanceSettings(),
    notifications: AgentNotificationSettings = rememberAgentNotificationSettings(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val dark = appearance.interfaceStyle.isDark(isSystemInDarkTheme())
    val configuration = Configuration(LocalConfiguration.current).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    // 所有页面的深浅色判断使用同一份有效配置，避免只更新 MaterialTheme 而留下浅色背景。
    val palette = if (dark) DshPalettes.Dark else DshPalettes.Light
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearance,
        LocalAgentNotificationSettings provides notifications,
        LocalDshPalette provides palette,
        LocalConfiguration provides configuration
    ) {
        MaterialTheme(colorScheme = paletteColorScheme(palette)) {
            CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
        }
    }
}
