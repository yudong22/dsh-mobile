package com.clarklevis.dsh.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「切后台回来不再弹窗」的回归测试。
 *
 * 这条修复的核心是**分类**：传输类拒绝静默、用户动作类失败保留。
 * 分类错一边就会退回原来的 bug（一回来就报错），错另一边则会把真实失败藏起来，
 * 所以两侧都要钉住。
 */
class RequestRejectionPolicyTest {

    /**
     * 切后台→回前台时必然产生的那次拒绝，必须静默。
     *
     * `GatewayRuntime.sendRequestLocked` 在 `connection != CONNECTED` 时对**任何**请求
     * 都回 `not-connected`；回前台的重连完成前，Compose 的刷新请求必然撞上它。
     * 这是用户报告的「弹窗」的主要来源。
     */
    @Test
    fun notConnectedRejectionIsSilentSoBackgroundReturnDoesNotAlert() {
        assertTrue(RequestRejectionPolicy.isTransient("not-connected"))
    }

    /** 连接生命周期自身变化引发的一类拒绝，同样属于噪声。 */
    @Test
    fun connectionLifecycleRejectionsAreSilent() {
        listOf(
            "connection-replaced",
            "connection-closed",
            "connection-recycled",
            "background-suspended",
            "network-lost",
            "recovery-timeout",
            "connection-timeout",
            "transport-failed",
            "gateway-unavailable",
            "gateway-disabled",
            "send-failed",
            "incoming-flow-failed",
            "network-flow-failed"
        ).forEach { reason ->
            assertTrue("$reason 应静默", RequestRejectionPolicy.isTransient(reason))
        }
    }

    /** 旧代次的帧被丢弃属于正常并发，不该打扰用户。 */
    @Test
    fun staleAndBusyRejectionsAreSilent() {
        listOf("stale-frame", "no-active-request", "session-mismatch", "request-busy", "request-coalesced")
            .forEach { reason ->
                assertTrue("$reason 应静默", RequestRejectionPolicy.isTransient(reason))
            }
    }

    /**
     * 宿主主动回的 `kind:"error"` 帧是**服务端明确拒绝**，必须让用户看见。
     *
     * 这条曾经被误判为传输噪声：socket 断开时宿主根本发不出该帧，
     * 所以它只能来自「连接确实通着、但服务端拒绝了这次请求」。
     */
    @Test
    fun gatewayReportedErrorStaysVisible() {
        assertFalse(RequestRejectionPolicy.isTransient("gateway-request-failed"))
    }

    /**
     * 超时必须可见：刚提交的消息可能没送出去，静默会让人以为已发送。
     */
    @Test
    fun requestTimeoutStaysVisible() {
        assertFalse(RequestRejectionPolicy.isTransient("request-timeout"))
    }

    /** 用户动作类失败必须可见，否则用户得不到任何可操作的反馈。 */
    @Test
    fun userActionFailuresStayVisible() {
        listOf(
            "image-limits-exceeded",
            "history-cursor-without-format",
            "attachment-invalid",
            "attachment-cache-write-failed",
            "authentication-required",
            "pair-token-missing",
            "credential-access-failed",
            "synthetic"
        ).forEach { reason ->
            assertFalse("$reason 必须可见", RequestRejectionPolicy.isTransient(reason))
        }
    }

    /** 文案不再泄漏内部 code：用户看到的是可读中文，而不是 `history: xxx`。 */
    @Test
    fun messagesAreHumanReadableAndNeverTheInternalFormat() {
        val cases = listOf(
            "history" to "gateway-request-failed",
            "message" to "gateway-request-failed",
            "unsubscribe" to "not-connected",
            "history" to "synthetic"
        )
        cases.forEach { (requestType, reason) ->
            val message = RequestRejectionPolicy.message(requestType, reason)
            assertFalse(
                "文案不得是内部 `${'$'}{requestType}: ${'$'}{reason}` 格式，实际：$message",
                message.startsWith("$requestType:")
            )
            assertTrue("文案应为中文说明，实际：$message", message.contains("。"))
        }
    }

    /** 已知 code 给专门文案，避免用户看到英文 token。 */
    @Test
    fun knownReasonsMapToSpecificGuidance() {
        assertEquals(
            "设备鉴权已失效，请重新扫码配对。",
            RequestRejectionPolicy.message("history", "authentication-required")
        )
        assertEquals(
            "图片超出数量或大小限制，请减少后重试。",
            RequestRejectionPolicy.message("message", "image-limits-exceeded")
        )
        assertEquals(
            "请求超时，请重试。",
            RequestRejectionPolicy.message("message", "request-timeout")
        )
    }

    /** 未知组合保留原始 code，便于用户报错时定位，但不暴露 requestType。 */
    @Test
    fun unknownReasonKeepsCodeForDiagnosis() {
        val message = RequestRejectionPolicy.message("gateway", "totally-new-code")
        assertTrue(message.contains("totally-new-code"))
        assertFalse(message.startsWith("gateway:"))
    }
}
