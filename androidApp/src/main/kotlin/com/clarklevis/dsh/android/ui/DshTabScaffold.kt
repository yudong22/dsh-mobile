package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * **Tab 根页面**的统一骨架：页头 + 内容 + 右下角悬浮主行动按钮。
 *
 * 把此前散在首页/项目/定时任务三页的同一套结构收敛到一处：
 *  1. `Scaffold(topBar = DshPageHeader(...))` 的重复声明；
 *  2. 「副标题显示当前连接设备」的重复推导（[DshDeviceSubtitle]）；
 *  3. 「右上角配对设备菜单」的重复接线（读 [LocalScanActions]）；
 *  4. FAB 的 `align(BottomEnd) + padding` 定位与**列表避让**（[DshFabReservedHeight]）——
 *     此前三页各写一遍，边距调整要动三处，且极易漏掉避让导致最后一条被 FAB 压住。
 *
 * 页面只提供**语义**（标题、内容、FAB 的动作与可见性），不再关心几何。
 *
 * 说明：只有 Tab 根页面用它。下钻页（任务详情、Agent 预设、默认模型、插件）有返回钮、
 * 无底栏，不走本骨架——强行统一会把「无返回钮」这个层级语义也一起带过去。
 *
 * @param title 页头标题
 * @param subtitle 副标题；默认取当前连接设备（[stateHolder] 版本见重载）
 * @param connection 连接状态：为副标题渲染状态点，也为阶段标签提供相位
 * @param headerStatus 页头右侧的相位短标签（如「离线」）；null 表示不显示
 * @param showPairingEntry 是否在页头右侧渲染「配对设备」菜单
 * @param onTitleClick 页头标题点击（首页进「任务运行设置」）
 * @param fab 右下角主行动按钮；null 表示该页无 FAB（例如定时任务加载中）
 * @param content 页面正文。为列表预留 FAB 避让请用 [DshFabReservedHeight]
 *   追加到 `contentPadding.bottom`——骨架只保证 FAB 定位，不干预页面的滚动结构。
 */
@Composable
internal fun DshTabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    connection: GatewayConnectionState? = null,
    onTitleClick: (() -> Unit)? = null,
    headerStatus: DshHeaderStatus? = null,
    showPairingEntry: Boolean = true,
    fab: DshFabSpec? = null,
    content: @Composable () -> Unit
) {
    val palette = dshPalette()
    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            DshPageHeader(
                title = title,
                subtitle = subtitle,
                connection = connection,
                onTitleClick = onTitleClick,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        headerStatus?.let { DshHeaderStatusLabel(it) }
                        if (showPairingEntry) {
                            LocalScanActions.current?.let { actions ->
                                DshScanMenuButton(
                                    onScanRequested = actions.onScanRequested,
                                    onManualEntryRequested = actions.onManualEntryRequested
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).then(modifier)) {
            content()
            fab?.let {
                DshFab(
                    onClick = it.onClick,
                    enabled = it.enabled,
                    iconRes = it.iconRes,
                    contentDescription = it.contentDescription,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = DshFabEdgePadding, bottom = DshFabBottomPadding)
                        .testTag(it.testTag)
                )
            }
        }
    }
}

/** [DshTabScaffold] 的 FAB 描述：动作 + 可见性 + 可选图标/测试标签。 */
internal data class DshFabSpec(
    val onClick: () -> Unit,
    val contentDescription: String,
    val testTag: String,
    val enabled: Boolean = true,
    val iconRes: Int = com.clarklevis.dsh.android.R.drawable.ic_add
)

/** [DshTabScaffold] 页头右侧的相位短标签（「已连接」「离线」等）。 */
internal data class DshHeaderStatus(
    val text: String,
    val highlighted: Boolean
)

/**
 * 相位标签：与副标题的状态点同源（[homeConnectionBadge]），
 * 已连接用成功色、其余用三级文字色。断网时列表是缓存，必须有标识。
 */
@Composable
private fun DshHeaderStatusLabel(status: DshHeaderStatus) {
    val palette = dshPalette()
    androidx.compose.material3.Text(
        text = status.text,
        color = if (status.highlighted) DshColors.Success else palette.textTertiary,
        fontSize = 13.sp,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = Modifier.padding(end = 2.dp)
    )
}
