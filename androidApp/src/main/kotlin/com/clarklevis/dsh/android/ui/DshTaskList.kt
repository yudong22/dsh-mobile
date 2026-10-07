package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R

/**
 * 「任务」区块标题行：左侧标题 + 折叠箭头，右侧列表视图按钮。
 *
 * 首页的会话列表与搜索框已移除（会话列表改由侧边抽屉承载），本文件原本的
 * `dshTaskList` / `DshTaskEmptyState` 随之删除；标题行仍由抽屉使用。
 */
@Composable
internal fun DshSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = dshPalette()
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = palette.textSecondary,
                fontSize = 17.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            ChevronDownIcon(
                tint = palette.textSecondary,
                expanded = expanded,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.ic_checklist),
            contentDescription = "切换列表显示",
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(palette.textPrimary)
        )
    }
}

/** 展开/收起用的小箭头：复用现有 chevron 资源并按展开态旋转。 */
@Composable
private fun ChevronDownIcon(
    tint: Color,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(R.drawable.ic_question_chevron_down),
        contentDescription = null,
        modifier = modifier.rotate(if (expanded) 0f else -90f),
        colorFilter = ColorFilter.tint(tint)
    )
}
