package com.clarklevis.dsh.android

/**
 * 「请求被拒」的原因分类与文案映射。
 *
 * 从 [AndroidSharedStateHolder] 里抽出来是为了可单测：这段逻辑决定**哪些失败会弹模态错误**，
 * 属于产品可见行为，不能只靠人工回归。
 *
 * 背景：`GatewayRuntime.rejectLocked` 会在连接未就绪时拒绝任何请求
 * （见 `GatewayRuntime.sendRequestLocked` 的 `not-connected` 分支）。而「切后台再回前台」
 * 这条正常路径必然经历「连接已关闭但刷新请求已发出」的窗口，于是必然产生一次
 * `not-connected` 拒绝。若把这类拒绝当成用户错误弹窗，用户就会看到
 * 「一回来就报错」——但重连本来就会自动完成，用户无事可做。
 */
internal object RequestRejectionPolicy {

    /**
     * 属于传输/连接重建类的拒绝原因：重连会自行兜住，**不弹模态错误**。
     *
     * 刻意排除三类：
     * - 用户动作类（`image-limits-exceeded`、`history-cursor-without-format`、
     *   `attachment-*`）——用户需要据此改变行为；
     * - `request-timeout`——可能意味着刚提交的消息没送出去，静默会让人误以为已发送；
     * - `gateway-request-failed`——宿主主动回了 `kind:"error"` 帧，是服务端明确拒绝，
     *   属于真实失败；socket 断开时宿主根本发不出该帧，故它不属于「切后台」噪声。
     */
    val TRANSIENT_REASONS: Set<String> = setOf(
        "not-connected",
        "send-failed",
        "request-busy",
        "request-coalesced",
        "stale-frame",
        "no-active-request",
        "session-mismatch",
        "transport-failed",
        "gateway-unavailable",
        "gateway-disabled",
        "connection-replaced",
        "connection-closed",
        "connection-recycled",
        "background-suspended",
        "network-lost",
        "recovery-timeout",
        "connection-timeout",
        "incoming-flow-failed",
        "network-flow-failed"
    )

    /** 该拒绝是否应该静默（不打扰用户）。 */
    fun isTransient(reason: String): Boolean = reason in TRANSIENT_REASONS

    /**
     * 把「请求被拒」翻译成用户可读文案。
     *
     * 此前实现直接把 `"${requestType}: ${reason}"` 当消息显示，用户会看到
     * `history: gateway-request-failed` 这类内部 token。未知组合保留原始 code，
     * 便于用户报错时定位。
     */
    fun message(requestType: String, reason: String): String = when (reason) {
        "request-timeout" -> "请求超时，请重试。"
        "image-limits-exceeded" -> "图片超出数量或大小限制，请减少后重试。"
        "authentication-required" -> "设备鉴权已失效，请重新扫码配对。"
        "history-cursor-without-format" -> "历史记录版本不兼容，已重新加载最新内容。"
        "attachment-invalid" -> "附件数据无效，请重新选择文件。"
        "attachment-cache-write-failed" -> "附件缓存写入失败，请检查存储空间。"
        "pair-token-missing" -> "配对凭据缺失，请重新扫码配对。"
        "credential-access-failed" -> "无法读取本机保存的配对凭据，请重新扫码配对。"
        else -> when (requestType) {
            "history" -> "读取历史记录失败，请重试。"
            "message" -> "消息发送失败，请重试。"
            "attachment" -> "附件处理失败，请重试。"
            "command-execute" -> "命令执行失败，请重试。"
            else -> "请求未完成（$reason），请重试。"
        }
    }
}
