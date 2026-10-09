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
internal val DshBodyFontSize = 15.sp
internal val DshCaptionFontSize = 13.sp

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
    /** 主操作色：发送键、选中态。 */
    val primary: Color
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
        primary = DshColors.Ocean
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
        primary = Color(0xFF7EA8FF)
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
