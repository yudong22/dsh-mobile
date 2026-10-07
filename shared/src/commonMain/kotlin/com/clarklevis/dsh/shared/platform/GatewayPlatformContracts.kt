package com.clarklevis.dsh.shared.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 平台 WebSocket 的最小连接参数。凭据只允许传给 transport，禁止序列化、持久化或日志输出。
 */
data class GatewayConnectionSpec(
    val generation: Long,
    val endpoint: String,
    val deviceId: String,
    val bearerToken: String? = null,
    val pairingCode: String? = null,
    val channel: String? = null,
    val expectedGatewayId: String? = null
) {
    override fun toString(): String =
        "GatewayConnectionSpec(generation=$generation, endpoint=<redacted>, deviceId=<redacted>, " +
            "bearerToken=${bearerToken?.let { "<redacted>" }}, " +
            "pairingCode=${pairingCode?.let { "<redacted>" }})"
}

sealed interface GatewayTransportState {
    val generation: Long

    data class Closed(override val generation: Long = 0) : GatewayTransportState
    data class Opening(override val generation: Long) : GatewayTransportState
    data class Open(override val generation: Long) : GatewayTransportState

    data class Failed(
        override val generation: Long,
        val httpStatus: Int? = null,
        val closeCode: Int? = null,
        val reason: String? = null,
        val recoverable: Boolean = true
    ) : GatewayTransportState
}

/** 入站文本携带不可复用的连接代次；文本内容不得进入 toString 或日志。 */
data class GatewayTransportFrame(
    val generation: Long,
    val text: String,
    val byteCount: Int
) {
    override fun toString(): String =
        "GatewayTransportFrame(generation=$generation, byteCount=$byteCount, text=<redacted>)"
}

sealed interface GatewayTransportEvent {
    data class State(val value: GatewayTransportState) : GatewayTransportEvent
    data class Frame(val value: GatewayTransportFrame) : GatewayTransportEvent {
        override fun toString(): String = value.toString()
    }
}

/** 平台 transport 每次只拥有一个 socket，并保持单连接内帧的原始顺序。 */
interface GatewayTransport {
    val state: StateFlow<GatewayTransportState>
    /** frame 与 failure 必须共享同一有序事件流。 */
    val events: Flow<GatewayTransportEvent>

    suspend fun open(spec: GatewayConnectionSpec)
    suspend fun send(text: String)
    suspend fun close()
}

/** 两条物理连接各自提供有序事件流，不能在 transport 内合并成同一阻塞队列。 */
interface GatewaySplitTransport : GatewayTransport {
    suspend fun confirmControlHandshake() {}
    val conversationEvents: Flow<GatewayTransportEvent>
}

enum class GatewayNetworkState {
    AVAILABLE,
    UNAVAILABLE
}

interface GatewayNetworkMonitor {
    val state: StateFlow<GatewayNetworkState>
}

@kotlinx.serialization.Serializable
data class GatewayPreferencesSnapshot(
    val endpoint: String = "ws://127.0.0.1:3080/ws/mobile",
    val selectedWorkspaceId: String? = null,
    val sessionsJson: String? = null
)

/** 仅保存非敏感配置；token 与 device id 不得进入该存储。 */
interface GatewayPreferences {
    val snapshots: Flow<GatewayPreferencesSnapshot>

    suspend fun load(): GatewayPreferencesSnapshot
    suspend fun update(snapshot: GatewayPreferencesSnapshot)
}

/**
 * 凭据存储必须由平台安全设施保护。endpoint 可作为索引，token/device id 本身不得明文落盘。
 */
interface GatewayCredentialStore {
    suspend fun loadOrCreateDeviceId(): String
    suspend fun loadToken(endpoint: String): String?
    suspend fun saveToken(endpoint: String, token: String)
    suspend fun deleteToken(endpoint: String)
}

interface GatewayAttachmentCache {
    suspend fun read(attachmentId: String): ByteArray?
    /** 仅当内存和磁盘提交均成功时返回 true。 */
    suspend fun write(attachmentId: String, bytes: ByteArray): Boolean
    suspend fun removeExpired()
}

/**
 * 会话正文（规范化 SessionEvent 基线）的平台缓存。
 *
 * 与 [GatewayPreferences] / [GatewayAttachmentCache] 并列：**磁盘 I/O 由平台实现**，
 * 合并、水位与去重规则仍在 KMP。用于「连接建立前先展示本地对话内容」，
 * 连接成功后由宿主 history/session-snapshot 覆盖（全量替换语义）。
 *
 * 契约要点：
 * - 写入必须原子（避免半截 JSON 被当成有效缓存）；
 * - 失效按体积 + 条数限额，**不用短 TTL**（宁可看到略旧内容，也不要打开就是空会话）；
 * - 是派生缓存，schema 不匹配直接当作 miss 丢弃即可。
 */
interface GatewayConversationCache {
    /** 返回该会话的缓存 payload（KMP 定义的 schema）；无缓存或已淘汰时返回 null。 */
    suspend fun read(sessionId: String): String?

    /** 原子写入；任一环节失败返回 false，调用方不得据此认为已持久化。 */
    suspend fun write(sessionId: String, payload: String): Boolean

    suspend fun remove(sessionId: String)

    /** 按体积 + 条数限额淘汰旧条目。 */
    suspend fun removeExpired()
}

interface GatewayClock {
    fun nowEpochMilliseconds(): Long
    suspend fun delay(milliseconds: Long)
}
