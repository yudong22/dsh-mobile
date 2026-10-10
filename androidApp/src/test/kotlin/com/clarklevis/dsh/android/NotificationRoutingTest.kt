package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.gateway.GatewayProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知点击的跳转解析。
 *
 * 这里的断言锁住两个曾经同时成立的静默缺陷：远程推送的键名与本地通知不同，且它的
 * `gatewayId` 是服务端 ID 而非本机 `localId`。任一未覆盖，点击推送都只打开 App、
 * 不进会话，且不产生任何错误。
 */
class NotificationRoutingTest {

    private fun profile(localId: String, gatewayId: String?) = GatewayProfile(
        localId = localId,
        gatewayId = gatewayId,
        gatewayName = "denisMacBook-M5.local",
        endpoints = listOf("ws://192.168.3.66:3081/ws/mobile"),
    )

    // --- 键名：本地通知（命名空间键）与远程推送（FCM 裸键）都要认 -------------

    @Test
    fun readsTheNamespacedKeysWrittenByLocalNotifications() {
        val route = notificationRouteFromExtras(
            namespacedGatewayId = "local-1",
            namespacedSessionId = "session-1",
            fcmGatewayId = null,
            fcmSessionId = null,
        )
        assertEquals("local-1", route?.gatewayId)
        assertEquals("session-1", route?.sessionId)
    }

    @Test
    fun readsTheBareKeysFromFcmData() {
        // FCM 的 createContentIntent 把 data 的自定义键原样放进点击 Intent，键名不改写。
        val route = notificationRouteFromExtras(
            namespacedGatewayId = null,
            namespacedSessionId = null,
            fcmGatewayId = "1fa25386-f71e-47a3-9f5b-c86d6f4b5ae7",
            fcmSessionId = "session-9f7c8414",
        )
        assertEquals("1fa25386-f71e-47a3-9f5b-c86d6f4b5ae7", route?.gatewayId)
        assertEquals("session-9f7c8414", route?.sessionId)
    }

    @Test
    fun namespacedKeysWinWhenBothArePresent() {
        // 两组同时出现（例如 App 自己又补写了 Intent）时，以本 App 写入的为准。
        val route = notificationRouteFromExtras(
            namespacedGatewayId = "local-1",
            namespacedSessionId = "session-1",
            fcmGatewayId = "server-9",
            fcmSessionId = "session-9",
        )
        assertEquals("local-1", route?.gatewayId)
        assertEquals("session-1", route?.sessionId)
    }

    @Test
    fun aHalfRouteIsNotARoute() {
        // 只有一个 ID 时必须放弃：切到不存在的会话比不跳更糟。
        assertNull(
            notificationRouteFromExtras(
                namespacedGatewayId = "local-1",
                namespacedSessionId = null,
                fcmGatewayId = null,
                fcmSessionId = null,
            )
        )
        assertNull(
            notificationRouteFromExtras(
                namespacedGatewayId = null,
                namespacedSessionId = null,
                fcmGatewayId = null,
                fcmSessionId = "session-1",
            )
        )
    }

    @Test
    fun blankValuesAreTreatedAsAbsent() {
        assertNull(
            notificationRouteFromExtras(
                namespacedGatewayId = "  ",
                namespacedSessionId = "\t",
                fcmGatewayId = "",
                fcmSessionId = "",
            )
        )
        // 空白优先键必须让位给有效回退键，否则一条推送会被空白值吃掉。
        val fallback = notificationRouteFromExtras(
            namespacedGatewayId = "",
            namespacedSessionId = "   ",
            fcmGatewayId = "server-1",
            fcmSessionId = "session-1",
        )
        assertEquals("server-1", fallback?.gatewayId)
        assertEquals("session-1", fallback?.sessionId)
    }

    // --- ID 语义：本地通知给 localId，远程推送给服务端 gatewayId ---------------

    @Test
    fun resolvesTheProfileByLocalIdForLocalNotifications() {
        val profiles = listOf(
            profile("ea8ecbf5", "1fa25386"),
            profile("other-local", "other-server"),
        )
        assertEquals("ea8ecbf5", profileForNotification(profiles, "ea8ecbf5")?.localId)
    }

    @Test
    fun resolvesTheProfileByServerGatewayIdForRemotePushes() {
        // 网关推送里的 gatewayId 是服务端 ID；只按 localId 找会返回 null 并清掉路由。
        val profiles = listOf(
            profile("ea8ecbf5", "1fa25386"),
            profile("other-local", "other-server"),
        )
        assertEquals("ea8ecbf5", profileForNotification(profiles, "1fa25386")?.localId)
    }

    @Test
    fun anUnknownGatewayIdResolvesToNothing() {
        val profiles = listOf(profile("ea8ecbf5", "1fa25386"))
        assertNull(profileForNotification(profiles, "not-paired-here"))
    }

    @Test
    fun theActiveProfileMatchesByEitherId() {
        val active = profile("ea8ecbf5", "1fa25386")
        assertTrue(notificationTargetsActiveProfile(active, "ea8ecbf5"))
        assertTrue(notificationTargetsActiveProfile(active, "1fa25386"))
        assertFalse(notificationTargetsActiveProfile(active, "another-gateway"))
        assertFalse(notificationTargetsActiveProfile(null, "ea8ecbf5"))
    }

    @Test
    fun aProfileWithoutAServerIdStillMatchesByLocalId() {
        // 配对后服务端 ID 才写入；此时本地通知仍必须能路由。
        val unpaired = profile("ea8ecbf5", null)
        assertEquals("ea8ecbf5", profileForNotification(listOf(unpaired), "ea8ecbf5")?.localId)
        assertTrue(notificationTargetsActiveProfile(unpaired, "ea8ecbf5"))
    }

    /**
     * 传给页面的路由闸门。
     *
     * 这里曾写成 `route.gatewayId == hosts.activeId`（activeId 是 localId）。本地通知
     * 恰好带 localId 所以能用，远程推送带服务端 ID，于是路由被静默丢弃——点推送只在
     * 首页停留。契约与 [notificationTargetsActiveProfile] 保持一致，因此这里逐条钉住。
     */
    @Test
    fun theRouteGateAcceptsRemotePushesAddressedByServerId() {
        val active = profile("ea8ecbf5", "1fa25386")
        // 本地通知：gatewayId 是 localId。
        assertTrue(notificationTargetsActiveProfile(active, "ea8ecbf5"))
        // 远程推送：gatewayId 是服务端 ID，必须同样放行。
        assertTrue(notificationTargetsActiveProfile(active, "1fa25386"))
    }
}
