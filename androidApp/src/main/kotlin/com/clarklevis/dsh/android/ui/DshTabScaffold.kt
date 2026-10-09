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
 * @param connection 连接状态：为副标题渲染状态点，并在过渡/故障态补一句相位说明
 * @param bodyIsWhite 正文底色是否用白色。任务列表（通栏列表）用白底，与行同色、
 *   滚动时整屏一体（微信通讯录的观感）；项目/定时任务用画布灰，让白色卡片浮起来。
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
    bodyIsWhite: Boolean = false,
    showPairingEntry: Boolean = true,
    fab: DshFabSpec? = null,
    content: @Composable () -> Unit
) {
    val palette = dshPalette()
    // 相位文案并入副标题行（状态点已由 DshPageHeader 渲染），不再单独占右上角：
    //  - 语义上它和副标题同属「当前连接」信息，放一起更好读；
    //  - 布局上右侧动作区只留配对按钮一个，宽度可控、与左侧占位对称，
    //    标题保持整屏居中（此前塞两个元素把标题推偏 28dp）。
    // 文案遵循既有约定（DshConnectionStateUi.dshConnectionDetailText）：
    // **已连接是常态、不额外占字**，只有过渡态/故障态才说明当前状态。
    val subtitleText = listOfNotNull(
        subtitle?.takeIf { it.isNotBlank() },
        DshTabScaffoldHeaderStatus(connection)
    ).joinToString("  ·  ")

    Scaffold(
        containerColor = if (bodyIsWhite) palette.surface else palette.canvas,
        topBar = {
            DshPageHeader(
                title = title,
                subtitle = subtitleText.takeIf { it.isNotBlank() },
                connection = connection,
                onTitleClick = onTitleClick,
                actions = {
                    if (showPairingEntry) {
                        LocalScanActions.current?.let { actions ->
                            DshScanMenuButton(
                                onScanRequested = actions.onScanRequested,
                                onManualEntryRequested = actions.onManualEntryRequested
                            )
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

/**
 * 副标题行要追加的相位说明。**已连接返回 null**——它是常态，不值得占字；
 * 只有过渡态与故障态才说明（沿用 [dshConnectionDetailText] 的既有约定，
 * 与之同源，避免同一个连接状态在两处各写一套文案）。
 */
private val DshTabScaffoldHeaderStatus: (GatewayConnectionState?) -> String? = { connection ->
    connection?.let { dshConnectionDetailText(it) }
}
