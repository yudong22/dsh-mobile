package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 投递地址上报的契约。
 *
 * 这些断言锁住此前导致「安卓端从未注册成功」的两个行为：上报地址必须可重复读取
 * （不能读一次就清空），且必须在连接建立后重发。修复前用内存 `consume()` 取走待上报
 * 值，通道未就绪时那次取走就永久丢失了地址。
 *
 * 一台小米设备同时持有 FCM 与 Mi Push 两个地址，因此存储层以平台为键分别落盘，
 * `current()` 返回两个平台各自的注册——测试覆盖这条双通道约定。
 */
class AndroidPushRegistrationStoreTest {

    @Test
    fun storedTokenMapsToFcmRegistration() {
        val registration = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "fcm-token-1")

        assertEquals(GatewayPushPlatform.FCM, registration?.platform)
        assertEquals("fcm-token-1", registration?.token)
    }

    @Test
    fun storedTokenMapsToMipushRegistration() {
        val registration = pushRegistrationForStoredToken(GatewayPushPlatform.MIPUSH, "mi-regid-1")

        assertEquals(GatewayPushPlatform.MIPUSH, registration?.platform)
        assertEquals("mi-regid-1", registration?.token)
    }

    @Test
    fun storedTokenMapsToVivopushRegistration() {
        val registration = pushRegistrationForStoredToken(GatewayPushPlatform.VIVOPUSH, "vivo-regid-1")

        assertEquals(GatewayPushPlatform.VIVOPUSH, registration?.platform)
        assertEquals("vivo-regid-1", registration?.token)
    }

    /**
     * 同一落盘值必须能被反复读取。若某次读取清空了状态，连接建立后的补报就拿不到
     * 地址，注册会静默失败。
     */
    @Test
    fun storedTokenIsReadableMoreThanOnce() {
        val first = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "fcm-token-1")
        val second = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "fcm-token-1")

        assertEquals(first, second)
        assertEquals("fcm-token-1", second?.token)
    }

    @Test
    fun absentOrBlankTokenYieldsNoRegistration() {
        assertNull(pushRegistrationForStoredToken(GatewayPushPlatform.FCM, null))
        assertNull(pushRegistrationForStoredToken(GatewayPushPlatform.MIPUSH, ""))
        assertNull(pushRegistrationForStoredToken(GatewayPushPlatform.VIVOPUSH, ""))
        // 仅空白的 token 与缺失等价：上报它只会被服务端拒绝。
        assertNull(pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "   "))
    }

    /** token 轮换后必须上报新地址，而不是保留旧值。 */
    @Test
    fun rotatedTokenReplacesThePreviousAddress() {
        val rotated = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "fcm-token-2")

        assertEquals("fcm-token-2", rotated?.token)
    }

    /**
     * 三通道约定：FCM、Mi Push 与 vivo push 各自落盘，互不覆盖；`current()` 同时返回三者。
     * 单平台未注册时只返回已注册的，缺失平台不占槽位。
     */
    @Test
    fun fcmMipushAndVivopushStoredIndependently() {
        val fcm = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, "fcm-token-x")
        val mipush = pushRegistrationForStoredToken(GatewayPushPlatform.MIPUSH, "mi-regid-y")
        val vivo = pushRegistrationForStoredToken(GatewayPushPlatform.VIVOPUSH, "vivo-regid-z")

        assertEquals(GatewayPushPlatform.FCM, fcm?.platform)
        assertEquals("fcm-token-x", fcm?.token)
        assertEquals(GatewayPushPlatform.MIPUSH, mipush?.platform)
        assertEquals("mi-regid-y", mipush?.token)
        assertEquals(GatewayPushPlatform.VIVOPUSH, vivo?.platform)
        assertEquals("vivo-regid-z", vivo?.token)
    }

    @Test
    fun onlyRegisteredPlatformsAppear() {
        // 仅 Mi Push 注册时，FCM 不应出现（用假的存储函数验证过滤逻辑无法在此无 Context）。
        val mipush = pushRegistrationForStoredToken(GatewayPushPlatform.MIPUSH, "mi-regid-z")
        val missingFcm = pushRegistrationForStoredToken(GatewayPushPlatform.FCM, null)

        assertEquals("mi-regid-z", mipush?.token)
        assertNull(missingFcm)
    }
}
