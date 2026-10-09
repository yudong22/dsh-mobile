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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R

/**
 * 「分组圆角列表」公共样式——项目页（v1.9.4 改版）建立、供各列表页复用的一套行组件：
 *
 * - [DshGroupedSection]：组标题（12sp 次级灰字 + 可选计数）+ 一个共享圆角 surface 容器。
 *   行与行之间用 1dp 分隔线（[DshGroupedRow] 自带），视觉上是「列表」而不是「卡片堆」。
 * - [DshGroupedRow]：66dp 级两行行组件——34dp 圆角色块（缩写锚点）+ 标题 + 副行
 *   （副标题或状态点 + 说明文字）+ 右上角时间 / 行尾勾或箭头。
 * - [DshTileInitials]：色块里的缩写推导（ASCII 取词首、CJK 取前两字），项目页/任务页共用。
 *
 * 任何页面要新的分组列表，先从这里取组件；不要回退到「每行一张独立大卡」的旧样式。
 */

/** 组标题行 + 共享圆角容器。容器描边/分隔线颜色都取 palette，深浅色自适应。 */
@Composable
internal fun DshGroupedSection(
    label: String,
    modifier: Modifier = Modifier,
    showCount: Boolean = false,
    count: Int = 0,
    content: @Composable () -> Unit
) {
    val palette = dshPalette()
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = palette.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            if (showCount) {
                Text(
                    "  $count",
                    color = palette.textTertiary,
                    fontSize = 12.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // clip 必不可少：行是方角矩形（选中底色、行首强调条），容器是 20dp 圆角。
                // 不裁剪时选中行会从圆角处**溢出**——真机截图里左上角那条蓝色竖线
                // 戳在圆角外面，就是这个原因。
                .clip(DshCardCornerRadius)
                .background(palette.surface, DshCardCornerRadius)
                .border(1.dp, palette.cardBorder, DshCardCornerRadius)
        ) {
            content()
        }
    }
}

/**
 * 分组列表内的标准行：选中态 = 淡主色底 + 行首 3dp 强调条 + 行尾小勾；
 * 未选中 = 透明底 + 行尾灰色右箭头。
 *
 * [tileLabel] 非空时渲染 34dp 主色 tint 缩写色块；[secondLine] 为 null 且
 * [fallbackSubtitle] 非空时，副行退化为纯副标题文本（无状态点）。
 * [timeLabel] 渲染在右上角（活跃时可用 [timeHighlighted] 换主色加粗）。
 */
@Composable
internal fun DshGroupedRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondLine: DshRowSecondLine? = null,
    fallbackSubtitle: String? = null,
    timeLabel: String? = null,
    timeHighlighted: Boolean = false,
    selected: Boolean = false,
    tileLabel: String? = null,
    testTag: String? = null
) {
    val palette = dshPalette()
    // 读屏文案：单条 contentDescription 比逐个 Text 朗读更连贯。
    val rowDescription = buildString {
        append(title)
        secondLine?.let {
            append("，")
            append(it.accessibleText)
        }
        fallbackSubtitle?.let { append("，").append(it) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 选中底色用 palette 里的**实色** token（而非 primary 透明叠加）：
            // 叠加在 #F8F8F8 画布上会得到发灰紫的 #EFF3FD，在白卡片旁显脏。
            .background(if (selected) palette.selectedRow else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = rowDescription
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .drawBehind {
                if (selected) {
                    // 行首 3dp 强调条：垂直居中、占行高约 60%。
                    // 容器已 clip，条不会溢出圆角；x 起点同时留出 1dp 描边宽度，
                    // 避免压在描边上出现双色边。
                    val barWidth = 3.dp.toPx()
                    val barHeight = size.height * 0.6f
                    drawRoundRect(
                        color = palette.primary,
                        topLeft = Offset(1.dp.toPx(), (size.height - barHeight) / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tileLabel != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(palette.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tileLabel,
                    color = palette.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            when {
                secondLine != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(6.dp)
                            .background(secondLine.dotColor(palette), CircleShape)
                    )
                    Text(
                        secondLine.text,
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
                fallbackSubtitle != null -> Text(
                    fallbackSubtitle,
                    color = palette.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (timeLabel != null) {
                Text(
                    timeLabel,
                    color = if (timeHighlighted) palette.primary else palette.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (timeHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
            if (selected) {
                Image(
                    painter = painterResource(R.drawable.ic_menu_check),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(palette.primary)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(palette.textTertiary)
                )
            }
        }
    }
}

/**
 * [DshGroupedRow] 副行模型：状态点颜色 + 文本 + 读屏整句。
 * [dotColor] 是 lambda 而不是 Color，让调用方按当前 palette 推导（深浅色自适应）。
 */
internal data class DshRowSecondLine(
    val text: String,
    val accessibleText: String = text,
    val dotColor: (DshPalette) -> Color = { it.textTertiary }
)

/** 组内行间 1dp 分隔线：左缩进 [startIndent] 对齐文本起点，最后一段不画。 */
@Composable
internal fun DshGroupedRowDivider(startIndent: androidx.compose.ui.unit.Dp = 58.dp) {
    val palette = dshPalette()
    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = startIndent))
}

/** 项目/任务通用的图标缩写：ASCII 取前两个词首字符，CJK/其他取前两个字符。 */
internal fun DshTileInitials(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "·"
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val asciiWords = words.filter { it.first().code < 0x2E80 }
    return when {
        asciiWords.size >= 2 ->
            (asciiWords[0].first().toString() + asciiWords[1].first()).uppercase()
        asciiWords.size == 1 && asciiWords[0].length >= 2 -> asciiWords[0].take(2).uppercase()
        else -> trimmed.take(2)
    }
}

/**
 * 页面右下角的悬浮主行动按钮（参考图 3 定时任务页的「＋」）。
 *
 * **颜色语义固定为两种**（用户明确要求统一）：
 *  - 可点：`palette.primary` 实色蓝 + 白图标；
 *  - 不可点：`palette.disabledPrimary` 浅蓝实色 + 白图标。
 *
 * 禁用态**不再用 `alpha` 整体降透明度**——那会把底色与图标一起冲淡成灰
 * （实测 `#2E6BE6 × 0.38` 叠画布得 `#ACC3F1`，发灰且不像同一套按钮）。
 * 改用预先调好的浅蓝实色：仍是「同一个按钮」，只是现在点不了。
 *
 * 定位与列表避让**不在这里**：由 [DshTabScaffold] 统一处理，
 * 避免每个页面各写一遍 `align(BottomEnd) + padding(...)` 与底部 Spacer。
 */
@Composable
internal fun DshFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_add,
    contentDescription: String,
    enabled: Boolean = true
) {
    val palette = dshPalette()
    Box(
        modifier = modifier
            .size(DshFabSize)
            .background(if (enabled) palette.primary else palette.disabledPrimary, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(DshFabIconSize),
            // 图标色**按状态分档**，不是统一取 onPrimary：
            //  - 可点：白图标 on 主色蓝 = 4.81:1；
            //  - 不可点：浅蓝底上白图标只有 1.54:1（实测，等于看不见），
            //    故浅色模式改用主色描边式图标（3.12:1，且读作「同一个按钮」）；
            //    深色模式禁用底是暗蓝，白图标 9.86:1 最优，保持不变。
            colorFilter = ColorFilter.tint(
                when {
                    enabled -> palette.onPrimary
                    palette.isDark -> palette.onPrimary
                    else -> palette.primary
                }
            )
        )
    }
}

/**
 * 列表底部为 FAB 预留的避让高度：FAB 自身 + 右侧边距 + 底部边距。
 *
 * 一个页面的「列表 contentPadding.bottom」与「FAB 定位」必须用同一份推导，
 * 否则滚到底部时最后一条会被 FAB 压住。抽成常量而不是各页展开算式。
 */
internal val DshFabReservedHeight: androidx.compose.ui.unit.Dp
    get() = DshFabSize + DshFabEdgePadding + DshFabBottomPadding

/**
 * 页内提示文案（空态引导、离线原因等）的统一排版：三级文字色、14sp、行高 20sp。
 *
 * 此前项目页两处、首页空态一处各自写同样的字号/行高/颜色三元组，
 * 改一次排版要同步多处。这里只负责「文字」，不负责定位。
 */
@Composable
internal fun DshHintText(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Text(
        text = text,
        color = palette.textTertiary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = modifier,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}
