package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连接状态的 UI 映射：7 个原始状态 → 4 类展示语义。
 *
 * 这层映射此前散落在各页面里（且被压缩成 `deviceOnline: Boolean`），
 * 现在集中到一个纯函数，用测试锁住「哪些状态算连接中」这类判断。
 */
class DshConnectionStateUiTest {

    @Test
    fun `transitioning states are exactly connecting and authenticating`() {
        val transitioning = GatewayConnectionState.entries.filter { it.dshIsTransitioning }
        assertEquals(
            listOf(GatewayConnectionState.CONNECTING, GatewayConnectionState.AUTHENTICATING),
            transitioning
        )
    }

    @Test
    fun `waiting for network and failed need attention rather than counting as in progress`() {
        assertEquals(DshConnectionPhase.ATTENTION, GatewayConnectionState.WAITING_FOR_NETWORK.dshPhase)
        assertEquals(DshConnectionPhase.ATTENTION, GatewayConnectionState.FAILED.dshPhase)
        assertFalse(GatewayConnectionState.WAITING_FOR_NETWORK.dshIsTransitioning)
        assertFalse(GatewayConnectionState.FAILED.dshIsTransitioning)
    }

    @Test
    fun `every state maps to a phase and only connected is online`() {
        GatewayConnectionState.entries.forEach { state ->
            // 穷尽 when 已由编译器保证；这里确保 ONLINE 不会被误分配给非 CONNECTED 状态。
            assertEquals(
                state == GatewayConnectionState.CONNECTED,
                state.dshPhase == DshConnectionPhase.ONLINE
            )
        }
    }

    @Test
    fun `only connected allows network actions so the button disables while connecting`() {
        assertFalse(GatewayConnectionState.CONNECTED.dshBlocksNetworkActions)
        assertTrue(GatewayConnectionState.CONNECTING.dshBlocksNetworkActions)
        assertTrue(GatewayConnectionState.AUTHENTICATING.dshBlocksNetworkActions)
        assertTrue(GatewayConnectionState.DISCONNECTED.dshBlocksNetworkActions)
    }

    /** 已连接是常态，不占副标题；其余状态都必须有可读文案。 */
    @Test
    fun `connected has no detail text while every other state does`() {
        assertNull(dshConnectionDetailText(GatewayConnectionState.CONNECTED))
        GatewayConnectionState.entries
            .filter { it != GatewayConnectionState.CONNECTED }
            .forEach { state ->
                val text = dshConnectionDetailText(state)
                assertTrue("$state 缺少副标题文案", !text.isNullOrBlank())
            }
    }

    @Test
    fun `connecting and authenticating have distinct copy`() {
        assertEquals("正在连接…", dshConnectionDetailText(GatewayConnectionState.CONNECTING))
        assertEquals("正在认证…", dshConnectionDetailText(GatewayConnectionState.AUTHENTICATING))
    }
}
