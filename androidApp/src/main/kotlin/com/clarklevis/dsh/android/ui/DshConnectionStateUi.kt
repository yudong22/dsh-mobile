package com.clarklevis.dsh.android.ui

import androidx.compose.ui.graphics.Color
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState

/**
 * 连接状态在 UI 上的呈现语义。
 *
 * 共享层有 7 个原始状态，但它们在界面上的「意思」只有四类：正常、过渡中、需要用户处理、未开始。
 * 集中在这里映射，避免每个页面各自 `when` 一遍导致文案与颜色分叉
 * （此前抽屉把 7 个状态压成一个 `deviceOnline: Boolean`，连接中与未连接长得一模一样）。
 */
internal enum class DshConnectionPhase {
    /** 已连接，可以正常收发。 */
    ONLINE,

    /** 正在建立连接或认证：应有进度反馈，且不应让用户重复触发连接操作。 */
    IN_PROGRESS,

    /** 连接失败或等待网络：需要用户介入（重试 / 检查网络）。 */
    ATTENTION,

    /** 未连接：空闲态，不是错误。 */
    IDLE
}

internal val GatewayConnectionState.dshPhase: DshConnectionPhase
    get() = when (this) {
        GatewayConnectionState.CONNECTED -> DshConnectionPhase.ONLINE
        GatewayConnectionState.CONNECTING,
        GatewayConnectionState.AUTHENTICATING -> DshConnectionPhase.IN_PROGRESS
        GatewayConnectionState.FAILED,
        GatewayConnectionState.WAITING_FOR_NETWORK -> DshConnectionPhase.ATTENTION
        GatewayConnectionState.DISCONNECTED,
        GatewayConnectionState.SUSPENDED -> DshConnectionPhase.IDLE
    }

/**
 * 逐状态文案。`null` 表示该状态无需在副标题里额外说明
 * （已连接是常态，不值得占用一行；未连接由绿点/灰点自行表达）。
 */
internal fun dshConnectionDetailText(state: GatewayConnectionState): String? = when (state) {
    GatewayConnectionState.CONNECTED -> null
    GatewayConnectionState.CONNECTING -> "正在连接…"
    GatewayConnectionState.AUTHENTICATING -> "正在认证…"
    GatewayConnectionState.WAITING_FOR_NETWORK -> "等待网络…"
    GatewayConnectionState.FAILED -> "连接失败"
    GatewayConnectionState.DISCONNECTED -> "未连接"
    GatewayConnectionState.SUSPENDED -> "已暂停"
}

/** 过渡态需要转圈动画；其余状态用静态点。 */
internal val GatewayConnectionState.dshIsTransitioning: Boolean
    get() = dshPhase == DshConnectionPhase.IN_PROGRESS

/**
 * 扫码/手动配对进行中的**阶段文案**。
 *
 * 为什么需要单独一条：扫码成功后扫描页立刻关闭、退回任务列表，而配对最长要
 * [PAIRING_TIMEOUT_MS]（20s）——这段时间此前**没有任何反馈**。手动配对面板恰好
 * 因为自己渲染了 `isConnecting` 才看得出在动，扫码路径完全没有，所以用户看到的
 * 是「扫完就没反应了，过一会儿才跳」。
 *
 * 分阶段而不是一个笼统的「正在连接」：连接与认证是两件可感知的不同等待，
 * 分开说得清「卡在哪一步」。候选连接状态可能还没上报（刚起步），此时用第一档。
 */
internal fun pairingProgressText(connection: GatewayConnectionState?): String = when (connection) {
    GatewayConnectionState.AUTHENTICATING -> "正在认证设备…"
    GatewayConnectionState.CONNECTED -> "已连接，正在加载任务…"
    GatewayConnectionState.FAILED -> "连接失败"
    GatewayConnectionState.WAITING_FOR_NETWORK -> "等待网络…"
    GatewayConnectionState.SUSPENDED -> "连接已暂停"
    // CONNECTING、DISCONNECTED、null：都还没开始握手，说第一档。
    else -> "正在连接设备…"
}

/** 配对进度提示是否该显示：仅在配对进行中显示。 */
internal fun shouldShowPairingProgress(pairing: Boolean): Boolean = pairing

/** 连接中是否应禁止发起需要联网的操作（新建会话等）。 */
internal val GatewayConnectionState.dshBlocksNetworkActions: Boolean
    get() = this != GatewayConnectionState.CONNECTED

internal fun dshConnectionDotColor(state: GatewayConnectionState, palette: DshPalette): Color =
    when (state.dshPhase) {
        DshConnectionPhase.ONLINE -> DshColors.Success
        DshConnectionPhase.IN_PROGRESS -> DshColors.Amber
        DshConnectionPhase.ATTENTION -> DshColors.Danger
        DshConnectionPhase.IDLE -> palette.textTertiary
    }
