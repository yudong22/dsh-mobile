package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.gateway.GatewayProfile

/**
 * 通知点击的跳转目标解析。
 *
 * App 有两条通知路径，它们的**键名**与 **ID 语义**都不同，必须两条都认：
 *
 * | 来源 | gatewayId 的含义 | Intent 里的键 |
 * |---|---|---|
 * | 本地通知（socket 帧驱动） | `GatewayProfile.localId`（本机 ID） | `com.clarklevis.dsh.android.notification.*` |
 * | 远程推送（FCM） | 服务端 `gatewayId` | FCM `data` 的裸键 `gatewayId` / `sessionId` |
 *
 * 只认其中一条会让另一条静默失效。远程推送尤其隐蔽：FCM 载荷同时带 `notification`
 * 与 `data`，App 在后台时 `notification` 由**系统托盘**直接展示，`onMessageReceived`
 * 根本不被调用；点击时系统把 `data` 的裸键放进启动 Intent，而 App 按命名空间键去读，
 * 于是只打开 App、不进会话。这类 bug 不会报错，只会「点了没反应」。
 *
 * FCM 侧行为以 `firebase-messaging` 的 `CommonNotificationBuilder.createContentIntent`
 * 为准：它调用 `paramsWithReservedKeysRemoved()` 后 `putExtras()`，即 `data` 的自定义键
 * 会原样进入点击 Intent，键名不做任何改写。
 *
 * 这些映射做成纯函数，便于在没有 Android 运行时的情况下测试（本工程无 Robolectric）。
 */

/** FCM `data` 里的键。网关按裸键发送（见 mobile-gateway `lib/push.mjs`）。 */
internal const val FCM_DATA_GATEWAY_ID = "gatewayId"
internal const val FCM_DATA_SESSION_ID = "sessionId"

/**
 * 从通知启动 Intent 的两组 extras 中解析跳转目标。
 *
 * 命名空间键优先：它由本 App 自己写入，语义明确；FCM 裸键是外部输入，仅作回退。
 * 任一 ID 缺失或为空白都返回 null —— 半条路由比不跳更糟，会切到一个不存在的会话。
 */
internal fun notificationRouteFromExtras(
    namespacedGatewayId: String?,
    namespacedSessionId: String?,
    fcmGatewayId: String?,
    fcmSessionId: String?,
): AndroidNotificationSessionRoute? {
    val gatewayId = namespacedGatewayId?.takeIf(String::isNotBlank)
        ?: fcmGatewayId?.takeIf(String::isNotBlank)
    val sessionId = namespacedSessionId?.takeIf(String::isNotBlank)
        ?: fcmSessionId?.takeIf(String::isNotBlank)
    if (gatewayId == null || sessionId == null) return null
    return AndroidNotificationSessionRoute(gatewayId, sessionId)
}

/**
 * 找到通知指向的主机。
 *
 * 先按 `localId` 匹配（本地通知的语义），再按服务端 `gatewayId` 匹配（远程推送的语义）。
 * 少了后半段，远程推送会原地清掉路由：`firstOrNull { localId == 服务端ID }` 永远为 null。
 */
internal fun profileForNotification(
    profiles: List<GatewayProfile>,
    routeGatewayId: String,
): GatewayProfile? = profiles.firstOrNull { it.localId == routeGatewayId }
    ?: profiles.firstOrNull { it.gatewayId == routeGatewayId }

/** 通知指向的主机是否已经是当前主机——是则无需切换，两种 ID 都算命中。 */
internal fun notificationTargetsActiveProfile(
    active: GatewayProfile?,
    routeGatewayId: String,
): Boolean {
    if (active == null) return false
    return active.localId == routeGatewayId || active.gatewayId == routeGatewayId
}
