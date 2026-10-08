package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.facade.SharedApprovalEffect
import com.clarklevis.dsh.shared.facade.SharedMobileQuestionSubmission
import com.clarklevis.dsh.shared.facade.SharedMobileSnapshot
import com.clarklevis.dsh.shared.projection.TrajectoryNode
import com.clarklevis.dsh.shared.protocol.GatewayFrame
import com.clarklevis.dsh.shared.protocol.GatewayQuestionAnswer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Projection mutation、结果发布和对应 UI 清理共用一个有序提交边界。 */
internal class AndroidProjectionActor(
    private val projection: AndroidGatewayProjection,
    private val uiDispatcher: CoroutineDispatcher,
    private val publish: (snapshot: SharedMobileSnapshot, coalesceWithDisplayFrame: Boolean) -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val snapshotTimeoutMillis: Long = 20_000
) {
    private val mutationLock = Mutex()
    private val workScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private var snapshotTimeoutJob: Job? = null
    private var activeSnapshotWait: Pair<String, Long>? = null
    private var pendingStreamingFrame: PendingStreamingFrame? = null
    private var lastStreamingFlushNanos = 0L
    /**
     * 快通道 `assistant-stream` 的待提交帧。与 legacy 通道不同，这里**不能**合并帧内容：
     * `AssistantStreamState` 要求 revision 逐帧连续，丢帧会触发重订阅。因此仅按时间/条数
     * 聚合一批帧，交给投影逐帧 accept、统一下游提交。
     */
    private var pendingAssistantStreamFrames: MutableList<PendingAssistantStreamFrame>? = null

    val initialSnapshot: SharedMobileSnapshot = projection.snapshot()

    suspend fun acceptFrame(
        rawJson: String,
        frame: GatewayFrame,
        correlatedSessionId: String?,
        afterPublish: () -> Unit = {}
    ): Boolean = mutationLock.withLock {
        if (!frame.isBatchableAssistantStreamFrame()) {
            flushPendingAssistantStreamFramesLocked()
            publishMutationLocked(
                projection.acceptFrame(rawJson, frame, correlatedSessionId),
                coalesceWithDisplayFrame = frame.kind == "assistant-stream" ||
                    (frame.kind == "event" && frame.event?.type == "assistant/chunk"),
                afterPublish = afterPublish
            )
            return@withLock true
        }
        val pending = pendingAssistantStreamFrames
            ?: mutableListOf<PendingAssistantStreamFrame>().also { pendingAssistantStreamFrames = it }
        // 同一批必须属于同一 session，避免把不同会话的帧混在一次提交里。
        if (pending.isNotEmpty() && pending.last().frame.sessionId != frame.sessionId) {
            flushPendingAssistantStreamFramesLocked()
        }
        requireNotNull(pendingAssistantStreamFrames)
            .add(PendingAssistantStreamFrame(rawJson, frame, correlatedSessionId, afterPublish))
        val now = nowNanos()
        val shouldFlush = requireNotNull(pendingAssistantStreamFrames).size >= MAXIMUM_STREAMING_BATCH_SIZE ||
            now - lastStreamingFlushNanos >= STREAMING_PROJECTION_INTERVAL_NANOS
        if (shouldFlush) {
            flushPendingAssistantStreamFramesLocked()
            lastStreamingFlushNanos = now
            return@withLock true
        }
        // 本帧已缓冲但尚未提交：调用方不应在此刻读取会话内容（与 legacy 合批语义一致）。
        return@withLock false
    }

    private suspend fun flushPendingAssistantStreamFramesLocked() {
        val frames = pendingAssistantStreamFrames ?: return
        pendingAssistantStreamFrames = null
        if (frames.isEmpty()) return
        publishMutationLocked(
            projection.acceptAssistantStreamBatch(
                frames.map { it.rawJson to it.frame },
                frames.last().correlatedSessionId
            ),
            coalesceWithDisplayFrame = true
        )
        // 批内每帧的 afterPublish 都必须执行，否则会丢掉附件清理等副作用。
        frames.forEach { it.afterPublish() }
    }

    private data class PendingAssistantStreamFrame(
        val rawJson: String,
        val frame: GatewayFrame,
        val correlatedSessionId: String?,
        val afterPublish: () -> Unit
    )

    /**
     * 将同一步骤的微小文本 token 合成一次增量投影。Runtime 仍无损处理每个协议帧；这里只
     * 降低 history/conversation MVI、JSON patch 和 Main dispatcher 的提交频率。
     */
    suspend fun acceptStreamingFrame(
        rawJson: String,
        frame: GatewayFrame,
        correlatedSessionId: String?
    ): Boolean = mutationLock.withLock {
        if (!frame.isBatchableStreamingChunk()) {
            flushPendingStreamingFrameLocked()
            publishMutationLocked(
                projection.acceptFrame(rawJson, frame, correlatedSessionId),
                coalesceWithDisplayFrame = false
            )
            return@withLock true
        }

        var flushed = false
        val current = pendingStreamingFrame
        pendingStreamingFrame = if (current == null) {
            PendingStreamingFrame(rawJson, frame, correlatedSessionId, count = 1)
        } else if (current.canMerge(frame, correlatedSessionId)) {
            current.merge(rawJson, frame)
        } else {
            flushPendingStreamingFrameLocked()
            flushed = true
            PendingStreamingFrame(rawJson, frame, correlatedSessionId, count = 1)
        }

        val now = nowNanos()
        if (
            lastStreamingFlushNanos == 0L ||
            requireNotNull(pendingStreamingFrame).count >= MAXIMUM_STREAMING_BATCH_SIZE ||
            now - lastStreamingFlushNanos >= STREAMING_PROJECTION_INTERVAL_NANOS
        ) {
            flushPendingStreamingFrameLocked()
            lastStreamingFlushNanos = now
            flushed = true
        }
        flushed
    }

    suspend fun selectSession(sessionId: String?, afterPublish: () -> Unit = {}) =
        mutate(afterPublish) { projection.selectSession(sessionId) }

    suspend fun loadHistory(
        sessionId: String,
        older: Boolean,
        afterPublish: () -> Unit = {}
    ) = mutate(afterPublish) { projection.loadHistory(sessionId, older) }

    suspend fun disconnected() = mutate { projection.disconnected() }

    suspend fun catchUpSelectedHistoryAfterReconnect() = mutate {
        projection.catchUpSelectedHistoryAfterReconnect()
    }

    suspend fun loadFixture(afterPublish: () -> Unit = {}) =
        mutate(afterPublish = afterPublish, mutation = projection::loadFixture)

    suspend fun reset(afterPublish: () -> Unit = {}) =
        mutate(afterPublish = afterPublish, mutation = projection::reset)

    suspend fun submitApprovalDecision(
        rpcId: String,
        outcome: String,
        isConnected: Boolean
    ): SharedApprovalEffect? = mutationLock.withLock {
        flushPendingStreamingFrameLocked()
        val result = projection.submitApprovalDecision(rpcId, outcome, isConnected)
        publishMutationLocked(result.snapshot, coalesceWithDisplayFrame = false)
        result.effect
    }

    suspend fun approvalRequestFailed(rpcId: String, message: String?) = mutate {
        projection.approvalRequestFailed(rpcId, message)
    }

    suspend fun approvalSessionRequestsFailed(sessionId: String, message: String?) = mutate {
        projection.approvalSessionRequestsFailed(sessionId, message)
    }

    suspend fun historyTimedOut(sessionId: String) = mutate {
        projection.historyTimedOut(sessionId)
        projection.snapshot()
    }

    suspend fun historyCancelled(sessionId: String) = mutate {
        projection.historyCancelled(sessionId)
        projection.snapshot()
    }

    suspend fun trajectory(sessionId: String?): List<TrajectoryNode> = mutationLock.withLock {
        flushPendingStreamingFrameLocked()
        flushPendingAssistantStreamFramesLocked()
        projection.trajectory(sessionId)
    }

    fun acceptFrameImmediate(rawJson: String, frame: GatewayFrame, correlatedSessionId: String?) {
        publish(projection.acceptFrame(rawJson, frame, correlatedSessionId), false)
    }

    fun selectSessionImmediate(sessionId: String?, afterPublish: () -> Unit = {}) {
        publish(projection.selectSession(sessionId), false)
        afterPublish()
    }

    fun loadFixtureImmediate(afterPublish: () -> Unit = {}) {
        publish(projection.loadFixture(), false)
        afterPublish()
    }

    /** 经 KMP 校验后提交提问答案；网络 I/O 由持有者负责。 */
    suspend fun submitQuestionAnswer(
        rpcId: String,
        answers: List<GatewayQuestionAnswer>,
        isConnected: Boolean
    ): SharedMobileQuestionSubmission = mutationLock.withLock {
        projection.submitQuestionAnswer(rpcId, answers, isConnected)
    }

    suspend fun submitQuestionCancel(
        rpcId: String,
        isConnected: Boolean
    ): SharedMobileQuestionSubmission = mutationLock.withLock {
        projection.submitQuestionCancel(rpcId, isConnected)
    }

    /**
     * 会话列表缓存的导出/恢复入口。与正文缓存同理必须在 `mutationLock` 内执行：
     * `mobileStore` 由后台 gateway dispatcher 写入，而这两个入口从 Main 调用。
     */
    suspend fun exportSessionCache(): String = mutationLock.withLock {
        projection.exportSessionCache()
    }

    suspend fun restoreSessionCache(sessionsJson: String): SharedMobileSnapshot =
        mutationLock.withLock {
            projection.restoreSessionCache(sessionsJson)
        }

    /**
     * 该会话是否已有不得被磁盘缓存覆盖的权威内容。必须在 `mutationLock` 内读取：
     * 投影状态由后台 gateway dispatcher 写入，绕过锁会读到撕裂状态。
     */
    suspend fun hasAuthoritativeContent(sessionId: String): Boolean = mutationLock.withLock {
        projection.hasAuthoritativeContent(sessionId)
    }

    /**
     * 会话正文缓存的导出/恢复入口。两者都必须在 `mutationLock` 内执行：投影状态
     * （historyEvents / conversationItems / 水位）由后台 gateway dispatcher 持有，
     * 绕过锁会让缓存恢复与并发投影互相踩踏。
     */
    suspend fun exportConversationCache(sessionId: String): String? = mutationLock.withLock {
        flushPendingStreamingFrameLocked()
        flushPendingAssistantStreamFramesLocked()
        projection.exportConversationCache(sessionId)
    }

    /**
     * 恢复后必须经 `mutate` 发布：`publishMutationLocked` 会切到 uiDispatcher，
     * 而 `snapshot` 是 Compose state，只能在 Main 上提交（否则可能漏掉重组）。
     */
    suspend fun restoreConversationCache(sessionId: String, payload: String) =
        mutate { projection.restoreConversationCache(sessionId, payload) }

    fun resetImmediate(afterPublish: () -> Unit = {}) {
        publish(projection.reset(), false)
        afterPublish()
    }

    fun close() {
        workScope.cancel()
        pendingStreamingFrame = null
        projection.close()
    }

    private suspend fun mutate(
        afterPublish: () -> Unit = {},
        coalesceWithDisplayFrame: Boolean = false,
        mutation: () -> SharedMobileSnapshot
    ) {
        mutationLock.withLock {
            flushPendingStreamingFrameLocked()
            flushPendingAssistantStreamFramesLocked()
            val next = mutation()
            publishMutationLocked(next, coalesceWithDisplayFrame, afterPublish)
        }
    }

    private suspend fun flushPendingStreamingFrameLocked() {
        val pending = pendingStreamingFrame ?: return
        pendingStreamingFrame = null
        publishMutationLocked(
            projection.acceptFrame(pending.rawJson, pending.frame, pending.correlatedSessionId),
            coalesceWithDisplayFrame = true
        )
    }

    private fun GatewayFrame.isBatchableAssistantStreamFrame(): Boolean {
        if (kind != "assistant-stream") return false
        return frame?.objectValue?.get("type")?.stringValue == "chunk"
    }

    private suspend fun publishMutationLocked(
        next: SharedMobileSnapshot,
        coalesceWithDisplayFrame: Boolean,
        afterPublish: () -> Unit = {}
    ) {
        withContext(uiDispatcher) {
            publish(next, coalesceWithDisplayFrame)
            afterPublish()
        }
        reconcileSnapshotTimeout()
    }

    private fun reconcileSnapshotTimeout() {
        val wait = projection.snapshotWait
        if (wait == activeSnapshotWait) return
        snapshotTimeoutJob?.cancel()
        activeSnapshotWait = wait
        snapshotTimeoutJob = wait?.let { expected ->
            workScope.launch {
                delay(snapshotTimeoutMillis)
                mutationLock.withLock {
                    if (projection.snapshotWait == expected) {
                        snapshotTimeoutJob = null
                        activeSnapshotWait = null
                        projection.historyTimedOut(expected.first)
                        publishMutationLocked(projection.snapshot(), false)
                    }
                }
            }
        }
    }

    private fun GatewayFrame.isBatchableStreamingChunk(): Boolean {
        val value = event ?: return false
        return kind == "event" &&
            value.type == "assistant/chunk" &&
            value.chunkType in BATCHABLE_CHUNK_TYPES &&
            !value.text.isNullOrEmpty()
    }

    private data class PendingStreamingFrame(
        val rawJson: String,
        val frame: GatewayFrame,
        val correlatedSessionId: String?,
        val count: Int
    ) {
        fun canMerge(next: GatewayFrame, nextCorrelatedSessionId: String?): Boolean {
            val currentEvent = requireNotNull(frame.event)
            val nextEvent = next.event ?: return false
            return correlatedSessionId == nextCorrelatedSessionId &&
                frame.sessionId == next.sessionId &&
                currentEvent.turn == nextEvent.turn &&
                currentEvent.step == nextEvent.step &&
                currentEvent.chunkType == nextEvent.chunkType
        }

        fun merge(nextRawJson: String, next: GatewayFrame): PendingStreamingFrame {
            val currentEvent = requireNotNull(frame.event)
            val nextEvent = requireNotNull(next.event)
            return copy(
                rawJson = nextRawJson,
                frame = next.copy(
                    event = nextEvent.copy(text = currentEvent.text.orEmpty() + nextEvent.text.orEmpty())
                ),
                count = count + 1
            )
        }
    }

    companion object {
        private val BATCHABLE_CHUNK_TYPES = setOf("text-delta", "reasoning-delta")
        private const val MAXIMUM_STREAMING_BATCH_SIZE = 32
        private const val STREAMING_PROJECTION_INTERVAL_NANOS = 50_000_000L
    }
}
