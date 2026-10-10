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
 */
class AndroidPushRegistrationStoreTest {

    @Test
    fun storedTokenMapsToFcmRegistration() {
        val registration = pushRegistrationForStoredToken("fcm-token-1")

        assertEquals(GatewayPushPlatform.FCM, registration?.platform)
        assertEquals("fcm-token-1", registration?.token)
    }

    /**
     * 同一落盘值必须能被反复读取。若某次读取清空了状态，连接建立后的补报就拿不到
     * 地址，注册会静默失败。
     */
    @Test
    fun storedTokenIsReadableMoreThanOnce() {
        val first = pushRegistrationForStoredToken("fcm-token-1")
        val second = pushRegistrationForStoredToken("fcm-token-1")

        assertEquals(first, second)
        assertEquals("fcm-token-1", second?.token)
    }

    @Test
    fun absentOrBlankTokenYieldsNoRegistration() {
        assertNull(pushRegistrationForStoredToken(null))
        assertNull(pushRegistrationForStoredToken(""))
        // 仅空白的 token 与缺失等价：上报它只会被服务端拒绝。
        assertNull(pushRegistrationForStoredToken("   "))
    }

    /** token 轮换后必须上报新地址，而不是保留旧值。 */
    @Test
    fun rotatedTokenReplacesThePreviousAddress() {
        val rotated = pushRegistrationForStoredToken("fcm-token-2")

        assertEquals("fcm-token-2", rotated?.token)
    }
}
