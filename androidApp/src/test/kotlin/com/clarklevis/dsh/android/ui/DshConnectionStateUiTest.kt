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

    // ---- 配对进度（扫码后的空档）----

    /**
     * 配对进度只在**配对进行中**显示。
     *
     * 这是「扫码后到加载前没有过渡」的守卫：扫码成功会立刻关闭扫描页、退回任务列表，
     * 而配对最长 20s，此前这段时间没有任何反馈。
     */
    @Test
    fun `pairing progress is shown exactly while pairing`() {
        assertTrue(shouldShowPairingProgress(pairing = true))
        assertFalse(shouldShowPairingProgress(pairing = false))
    }

    /** 候选连接还没上报状态时（刚起步）也要有文案，不能是空白。 */
    @Test
    fun `pairing progress always has copy even before the candidate reports state`() {
        assertTrue(pairingProgressText(null).isNotBlank())
        assertEquals("正在连接设备…", pairingProgressText(null))
        assertEquals("正在连接设备…", pairingProgressText(GatewayConnectionState.DISCONNECTED))
        assertEquals("正在连接设备…", pairingProgressText(GatewayConnectionState.CONNECTING))
    }

    /** 连接与认证是两段可感知的不同等待，必须分得开。 */
    @Test
    fun `pairing progress distinguishes connecting from authenticating`() {
        val connecting = pairingProgressText(GatewayConnectionState.CONNECTING)
        val authenticating = pairingProgressText(GatewayConnectionState.AUTHENTICATING)

        assertEquals("正在认证设备…", authenticating)
        assertTrue("两阶段文案不能相同：$connecting", connecting != authenticating)
    }

    /** 已连接之后仍要说明「还在加载」，否则用户以为卡在连接上。 */
    @Test
    fun `connected phase still reports that loading continues`() {
        val text = pairingProgressText(GatewayConnectionState.CONNECTED)
        assertTrue("已连接后应说明仍在加载：$text", text.contains("加载"))
    }

    /** 每种状态都要有非空文案，避免对话框出现空白说明。 */
    @Test
    fun `every connection state has non blank pairing copy`() {
        (GatewayConnectionState.entries.toList() + listOf<GatewayConnectionState?>(null)).forEach { state ->
            assertTrue("$state 缺少配对进度文案", pairingProgressText(state).isNotBlank())
        }
    }
}
