package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.facade.SharedConversationBootstrap
import com.clarklevis.dsh.shared.facade.SharedConversationCachePayload
import com.clarklevis.dsh.shared.facade.SharedConversationPatch
import com.clarklevis.dsh.shared.facade.SharedConversationStore
import com.clarklevis.dsh.shared.facade.SharedHistoryBootstrap
import com.clarklevis.dsh.shared.facade.SharedHistoryEffect
import com.clarklevis.dsh.shared.facade.SharedHistoryPatch
import com.clarklevis.dsh.shared.facade.SharedHistoryStore
import com.clarklevis.dsh.shared.facade.SharedMobileApprovalSubmission
import com.clarklevis.dsh.shared.facade.SharedMobileQuestionSubmission
import com.clarklevis.dsh.shared.facade.SharedMobileSnapshot
import com.clarklevis.dsh.shared.facade.SharedMobileStore
import com.clarklevis.dsh.shared.facade.SharedMviEvent
import com.clarklevis.dsh.shared.projection.ConversationItem
import com.clarklevis.dsh.shared.projection.ConversationProjectionLabels
import com.clarklevis.dsh.shared.projection.TrajectoryNode
import com.clarklevis.dsh.shared.projection.TrajectoryProjection
import com.clarklevis.dsh.shared.protocol.GatewayFrame
import com.clarklevis.dsh.shared.protocol.GatewayPendingApprovalRequest
import com.clarklevis.dsh.shared.protocol.GatewayQuestionAnswer
import com.clarklevis.dsh.shared.protocol.JsonValue
import com.clarklevis.dsh.shared.protocol.SessionEvent
import com.clarklevis.dsh.shared.sync.AssistantChunk
import com.clarklevis.dsh.shared.sync.AssistantStreamState
import com.clarklevis.dsh.shared.sync.HistorySessionState
import com.clarklevis.dsh.shared.sync.HistorySyncConfiguration
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val adapterJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    encodeDefaults = true
}

/** Android 的 KMP MVI 适配器：history/live 水位与流式 patch 均由现有共享 Store 决定。 */
internal class AndroidGatewayProjection(
    private val mobileStore: SharedMobileStore = SharedMobileStore(),
    private val historyStore: SharedHistoryStore = SharedHistoryStore(
        HistorySyncConfiguration(pagesPerBatch = 1)
    ),
    private val conversationStore: SharedConversationStore = SharedConversationStore(mobileConversationLabels()),
    /**
     * 历史基线变化后的落盘回调。只在基线/终态触发，绝不每 token 触发；
     * 磁盘 I/O 由平台层完成，KMP/投影不做 I/O。
     */
    private val onConversationBaselineChanged: (String) -> Unit = {},
    private val onResubscribe: (String) -> Unit = {},
    private val onHistoryPageRequested: (sessionId: String, beforeSequence: Int?, historyFormatVersion: Int?) -> Unit = { _, _, _ -> }
) {
    private var assistantStream = AssistantStreamState()
    private var usesAssistantStream = false
    private var waitGeneration = 0L
    var snapshotWait: Pair<String, Long>? = null
        private set
    /**
     * 快通道 `assistant-stream` 的 `revision` 必须逐帧 +1（`AssistantStreamState.acceptStream`），
     * 否则会触发重订阅。因此合批不改逐帧调用，只折叠下游昂贵链路。
     */
    private var isBatchingAssistantChunks = false
    private val pendingAssistantChunks = mutableListOf<AssistantChunk>()
    private var pendingAssistantSessionId: String? = null
    private var pendingAssistantAttemptId: String? = null
    private val historyEvents = mutableMapOf<String, List<SessionEvent>>()
    private var trajectoryHistorySessionId: String? = null
    private var trajectoryHistoryEvents: List<SessionEvent>? = null
    private var trajectoryHistoryNodes: List<TrajectoryNode> = emptyList()
    private val historyLastSequences = mutableMapOf<String, Int>()
    private val historyErrors = mutableMapOf<String, String>()
    private val historyHasMore = mutableMapOf<String, Boolean>()
    private val historySessionStates = mutableMapOf<String, HistorySessionState>()
    private var historyPendingSessionId: String? = null
    /**
     * 本进程已收到过实时帧的会话。缓存恢复只允许作为**非网络种子**：一旦该会话已有实时
     * 内容，磁盘上的旧基线就可能倒退覆盖它，必须跳过。
     */
    private val liveSessionIds = mutableSetOf<String>()
    private val conversationItems = mutableMapOf<String, List<ConversationItem>>()
    private val conversationLastSequences = mutableMapOf<String, Int>()
    private var controlSnapshot = mobileStore.snapshot()
    private var lastFrameKind: String? = null
    private var queueSessionIds = emptySet<String>()
    private var lastError: String? = null
    private val historyEnvelope = MviEnvelopeValidator("history")
    private val conversationEnvelope = MviEnvelopeValidator("conversation")
    private val historySubscription = historyStore.subscribe(::acceptHistoryMviEvent)
    private val conversationSubscription = conversationStore.subscribe(::acceptConversationMviEvent)

    /**
     * 经 KMP 校验后提交提问答案。校验失败时 effect 为 null，平台因此不会发出必然被拒的请求。
     */
    fun submitQuestionAnswer(
        rpcId: String,
        answers: List<GatewayQuestionAnswer>,
        isConnected: Boolean
    ): SharedMobileQuestionSubmission {
        val submission = mobileStore.submitQuestionAnswer(rpcId, answers, isConnected)
        controlSnapshot = submission.snapshot
        return submission.copy(snapshot = snapshot())
    }

    fun submitQuestionCancel(rpcId: String, isConnected: Boolean): SharedMobileQuestionSubmission {
        val submission = mobileStore.submitQuestionCancel(rpcId, isConnected)
        controlSnapshot = submission.snapshot
        return submission.copy(snapshot = snapshot())
    }

    /** 导出/恢复会话列表缓存；平台层负责实际磁盘 I/O。 */
    fun exportSessionCache(): String = mobileStore.exportSessionCache()

    /**
     * 该会话在本**连接**上是否收到过实时（或宿主基线）帧。
     *
     * 注意这是「连接代」语义而非「内存里有没有内容」：`hello` 会把它清空。判定能否用磁盘缓存
     * 播种请用 [hasAuthoritativeContent]，否则重连后会拿旧基线覆盖仍然有效的内存内容。
     */
    fun hasLiveContent(sessionId: String): Boolean = sessionId in liveSessionIds

    /**
     * 该会话是否存在**不得被磁盘缓存覆盖**的权威内容。
     *
     * 缓存是「非网络基线」，只应在内存里还没有该会话内容时用于播种。三个来源任一成立即算有：
     *  - 本连接收到过实时/基线帧（[liveSessionIds]）；
     *  - 内存里已经留有该会话的事件基线（`historyEvents`）；
     *  - **已渲染**的会话内容（`conversationItems`）。
     *
     * 第三条必不可少，且不能由第二条替代：`historyEvents` 只是「可落盘的规范化事件」缓冲，
     * 而 steering（排队）消息经 `conversationStore.replaceSteeringMessages` 只进
     * `conversationItems`、不进 `historyEvents`（实测此时 `exportConversationCache()` 为 null）。
     * 这类行不会随 `hello` 的 `clearTransient` 消失，因此在重连（`hello` 清空 [liveSessionIds]）
     * 之后仍然存在于屏幕上。只看前两条时本函数会返回 false，随后的重选就会用旧磁盘基线把
     * 已经渲染出来的内容覆盖掉。
     *
     * 注意：快通道的在途临时 chunk 会被 `hello` 的 `clearTransient` 合理清掉，那种情况下
     * 返回 false 是正确的——此时屏幕上确实没有内容需要保护。
     */
    fun hasAuthoritativeContent(sessionId: String): Boolean =
        hasLiveContent(sessionId) ||
            historyEvents[sessionId].orEmpty().isNotEmpty() ||
            conversationItems[sessionId].orEmpty().isNotEmpty()

    fun restoreSessionCache(sessionsJson: String): SharedMobileSnapshot {
        controlSnapshot = mobileStore.restoreSessions(sessionsJson)
        return snapshot()
    }

    /** 导出工作区映射缓存；平台层负责实际磁盘 I/O。 */
    fun exportWorkspaceCache(): String = mobileStore.exportWorkspaceCache()

    /**
     * 用缓存的工作区播种（冷启动/离线）。
     *
     * **只填列表、不发 effect**，且调用方必须先用 `hasReceivedWorkspaces` 之类的守卫
     * 确认宿主还没推过 `workspaces` 帧——否则会用旧缓存覆盖权威数据。
     */
    fun restoreWorkspaceCache(workspacesJson: String): SharedMobileSnapshot {
        controlSnapshot = mobileStore.restoreWorkspaces(workspacesJson)
        return snapshot()
    }

    /**
     * 导出该会话的规范化事件基线供平台落盘。只在已有基线时导出；空列表返回 null，
     * 避免用空缓存覆盖掉磁盘上仍可用的历史。
     */
    fun exportConversationCache(sessionId: String): String? {
        val records = historyEvents[sessionId].orEmpty()
        if (records.isEmpty()) return null
        return adapterJson.encodeToString(SharedConversationCachePayload(
            schema = SharedConversationCachePayload.CONVERSATION_CACHE_SCHEMA,
            sessionId = sessionId,
            lastSequence = historyLastSequences[sessionId] ?: records.last().seq,
            hasMore = historyHasMore[sessionId] == true,
            events = records
        ))
    }

    /**
     * 用本地缓存作为**非网络基线**：走与 `session-snapshot` 相同的原子安装路径
     * （`installSnapshot` + `replaceSession`），因此投影与 conversation 不会分叉。
     *
     * 关键约束：水位**不得高于**缓存事件尾 seq，否则 `history` 的 `replace` 单调性校验
     * 会失败并使该 domain 永久 fail-closed。
     */
    fun restoreConversationCache(sessionId: String, payload: String): SharedMobileSnapshot {
        // 二次校验（调用方已在锁外查过一次）：检查与写入必须原子，否则「磁盘读取」这段挂起
        // 期间到达的实时帧会被旧基线覆盖。所有调用方都经 mutationLock 进入，因此这里在锁内。
        if (hasAuthoritativeContent(sessionId)) return snapshot()
        val cached = runCatching {
            adapterJson.decodeFromString<SharedConversationCachePayload>(payload)
        }.getOrNull() ?: return snapshot()
        if (cached.schema != SharedConversationCachePayload.CONVERSATION_CACHE_SCHEMA || cached.sessionId != sessionId) return snapshot()
        if (cached.events.isEmpty()) return snapshot()
        require(cached.events.all { it.sessionId == sessionId }) { "缓存包含其他 session 的事件" }
        val tail = cached.events.last().seq
        historyStore.clearSession(sessionId)
        conversationStore.clearSession(sessionId)
        historyEvents[sessionId] = cached.events
        // 水位取「缓存事件尾 seq」与「缓存记录值」的较小者，杜绝倒退。
        historyLastSequences[sessionId] = minOf(tail, cached.lastSequence)
        historyHasMore[sessionId] = cached.hasMore
        historyStore.installSnapshot(
            sessionId,
            adapterJson.encodeToString(cached.events),
            cached.hasMore,
            null
        )
        conversationStore.replaceSession(sessionId, adapterJson.encodeToString(cached.events))
        return snapshot()
    }

    fun snapshot(): SharedMobileSnapshot {
        val approvalDetails = controlSnapshot.pendingApprovals.mapNotNull { request ->
            approvalArguments(request)?.let { request.rpcId to it }
        }.toMap()
        val commandPreviews = controlSnapshot.pendingApprovals.mapNotNull { request ->
            val arguments = approvalDetails[request.rpcId]
            val preview = arguments?.get("cmd")?.stringValue
                ?: arguments?.get("command")?.stringValue
                ?: controlSnapshot.approvalCommandPreviews[request.rpcId]
            preview?.let { request.rpcId to it }
        }.toMap()
        return controlSnapshot.copy(
            selectedHistoryError = controlSnapshot.selectedSessionId?.let(historyErrors::get),
            conversation = controlSnapshot.selectedSessionId?.let(conversationItems::get).orEmpty(),
            approvalCommandPreviews = commandPreviews,
            approvalDetails = approvalDetails,
            selectedHistoryHasMore = controlSnapshot.selectedSessionId?.let(historyHasMore::get) == true,
            selectedHistoryEarliestSequence = controlSnapshot.selectedSessionId
                ?.let(historyEvents::get)?.firstOrNull()?.seq,
            selectedHistoryIsLoading = controlSnapshot.selectedSessionId
                ?.let(historySessionStates::get)?.isLoading == true ||
                (snapshotWait != null && snapshotWait?.first == controlSnapshot.selectedSessionId),
            selectedHistoryIsLoadingOlder = controlSnapshot.selectedSessionId
                ?.let(historySessionStates::get)?.isLoadingOlder == true,
            selectedHistoryLoadedEventCount = controlSnapshot.selectedSessionId
                ?.let(historySessionStates::get)?.progress?.loaded ?: 0,
            selectedHistoryTotalEventCount = controlSnapshot.selectedSessionId
                ?.let(historySessionStates::get)?.progress?.total,
            lastFrameKind = lastFrameKind ?: controlSnapshot.lastFrameKind,
            lastError = lastError ?: controlSnapshot.lastError
        )
    }

    private fun approvalArguments(request: GatewayPendingApprovalRequest): JsonValue? {
        val targetCallId = request.callId
        val historyArguments = targetCallId?.let { callId ->
            historyEvents[request.sessionId]
                ?.lastOrNull { it.event.callId == callId }
                ?.event
                ?.arguments
                ?.normalizedJsonValue()
        }
        return historyArguments ?: controlSnapshot.approvalDetails[request.rpcId]
    }

    fun selectSession(sessionId: String?): SharedMobileSnapshot {
        cancelSnapshotWait()
        controlSnapshot.selectedSessionId?.let { conversationStore.clearAssistantChunks(it) }
        assistantStream.selectSession(sessionId)
        controlSnapshot = mobileStore.selectSession(sessionId)
        if (sessionId == null) return snapshot()
        if (usesAssistantStream) awaitSnapshot(sessionId) else startHistory(sessionId, older = false)
        return snapshot()
    }

    fun loadHistory(sessionId: String, older: Boolean): SharedMobileSnapshot {
        if (usesAssistantStream && !older && sessionId == controlSnapshot.selectedSessionId) {
            assistantStream.selectSession(sessionId)
            conversationStore.clearAssistantChunks(sessionId)
            awaitSnapshot(sessionId)
            onResubscribe(sessionId)
        } else startHistory(sessionId, older)
        return snapshot()
    }

    fun disconnected(): SharedMobileSnapshot {
        cancelSnapshotWait()
        assistantStream.disconnect()
        controlSnapshot.selectedSessionId?.let { conversationStore.clearAssistantChunks(it) }
        return snapshot()
    }

    fun catchUpSelectedHistoryAfterReconnect(): SharedMobileSnapshot {
        controlSnapshot.selectedSessionId?.let {
            if (usesAssistantStream) {
                if (!assistantStream.hasBaseline(it)) awaitSnapshot(it)
            } else {
                startHistory(it, older = false)
            }
        }
        return snapshot()
    }

    fun submitApprovalDecision(
        rpcId: String,
        outcome: String,
        isConnected: Boolean
    ): SharedMobileApprovalSubmission {
        val result = mobileStore.submitApprovalDecision(rpcId, outcome, isConnected)
        controlSnapshot = result.snapshot
        return result.copy(snapshot = snapshot())
    }

    fun approvalRequestFailed(rpcId: String, message: String?): SharedMobileSnapshot {
        controlSnapshot = mobileStore.approvalRequestFailed(rpcId, message)
        return snapshot()
    }

    fun approvalSessionRequestsFailed(sessionId: String, message: String?): SharedMobileSnapshot {
        controlSnapshot = mobileStore.approvalSessionRequestsFailed(sessionId, message)
        return snapshot()
    }

    private fun startHistory(sessionId: String, older: Boolean) {
        historyErrors.remove(sessionId)
        val local = historyEvents[sessionId].orEmpty()
        historyStore.start(
            sessionId = sessionId,
            older = older,
            hasLocalEvents = local.isNotEmpty(),
            earliestLocalSequence = local.firstOrNull()?.seq
        )
    }

    fun acceptFrame(rawJson: String, frame: GatewayFrame, correlatedSessionId: String?): SharedMobileSnapshot {
        lastFrameKind = frame.kind
        // 非快通道帧（含 assistant/message 终帧、history、session-snapshot、错误等）必须先落地
        // 已缓冲的 chunk，否则终帧会先于最后一段文本进入投影，出现丢字或顺序颠倒。
        if (!frame.isAssistantStreamChunk()) flushAssistantChunks()
        if (frame.kind == "session-queues" && frame.queues != null) {
            val queues = requireNotNull(frame.queues)
            (queueSessionIds + queues.keys).forEach { sessionId ->
                val result = conversationStore.replaceSteeringMessages(sessionId, adapterJson.encodeToString(queues[sessionId].orEmpty()))
                if (!result.accepted) lastError = result.errorMessage
            }
            queueSessionIds = queues.keys
        } else if (frame.kind == "session-queue" && frame.sessionId != null && frame.items != null) {
            val sessionId = requireNotNull(frame.sessionId)
            queueSessionIds = queueSessionIds + sessionId
            val result = conversationStore.replaceSteeringMessages(sessionId, adapterJson.encodeToString(requireNotNull(frame.items)))
            if (!result.accepted) lastError = result.errorMessage
        }
        if (frame.kind == "hello") {
            cancelSnapshotWait()
            usesAssistantStream = "assistant-stream-v1" in frame.capabilities.orEmpty()
            // 新握手意味着后续帧来自一次全新连接：旧连接留下的实时标记不再代表
            // 「本次会话仍有权威内容」，必须清空，否则重连后的缓存播种会被永久跳过。
            liveSessionIds.clear()
        }
        val id = frame.sessionId ?: correlatedSessionId
        if (frame.kind == "history" && id != null && assistantStream.hasBaseline(id) &&
            historySessionStates[id]?.isLoadingOlder != true) return snapshot()
        val update = assistantStream.accept(frame)
        if (update.accepted) lastError = null
        update.invalidatedSessionIds.forEach {
            historyStore.clearSession(it)
            conversationStore.clearSession(it)
            historyHasMore.remove(it)
        }
        if (update.clearTransient) update.sessionId?.let { conversationStore.clearAssistantChunks(it) }
        update.error?.let { lastError = it }
        if (update.resubscribe && id != null) {
            awaitSnapshot(id)
            onResubscribe(id)
        } else if (
            (update.error != null || (frame.kind == "error" && frame.requestType in setOf(null, "subscribe"))) &&
            (id == null || id == snapshotWait?.first)
        ) {
            cancelSnapshotWait()
        }
        val streamError = update.error
        if (streamError != null && !update.resubscribe && id != null) {
            historyErrors[id] = streamError
            historyCancelled(id)
        }
        if (frame.kind == "error" && frame.requestType in setOf("history", "subscribe") && id != null) {
            historyErrors[id] = frame.message ?: "历史记录加载失败"
            historyCancelled(id)
        }
        if (!update.accepted) return snapshot()
        when (frame.kind) {
            "session-snapshot" -> if (id != null) {
                liveSessionIds += id
                historyErrors.remove(id)
                if (snapshotWait?.first == id) snapshotWait = null
                historyStore.clearSession(id)
                conversationStore.clearSession(id)
                val records = frame.events.orEmpty().map { it.normalized(id) }
                historyStore.installSnapshot(id, adapterJson.encodeToString(records), frame.hasMore == true, frame.nextBeforeSeq)
                historyHasMore[id] = frame.hasMore == true
                conversationStore.replaceSession(id, adapterJson.encodeToString(records))
                controlSnapshot = mobileStore.acceptFrame(rawJson)
            }
            "assistant-stream", "session-stream-reset", "subscribed" -> Unit
            "history" -> acceptHistory(frame, correlatedSessionId)
            "event" -> acceptLive(rawJson, frame)
            "hello" -> if (usesAssistantStream) controlSnapshot.selectedSessionId?.let(::awaitSnapshot)
            "paired", "attachment" -> Unit
            else -> {
                controlSnapshot = mobileStore.acceptFrame(rawJson)
                if (frame.kind == "sent") assistantStream.selectSession(controlSnapshot.selectedSessionId)
            }
        }
        if (id != null && update.attemptId != null && update.chunksJson != "[]") {
            bufferAssistantChunks(id, requireNotNull(update.attemptId), update.chunksJson)
        }
        return snapshot()
    }

    /**
     * 批量接受快通道帧：内部**逐帧**调用 [acceptFrame]，因此 `revision`/`index` 仍逐帧连续
     * （快通道的硬约束，否则会触发重订阅）；被折叠的只是下游昂贵链路
     * 「chunk -> ConversationStore -> MVI patch -> 投影规划」。中间快照一律丢弃，
     * 只在最后 flush 一次并返回最终快照。
     */
    fun acceptAssistantStreamBatch(
        frames: List<Pair<String, GatewayFrame>>,
        correlatedSessionId: String?
    ): SharedMobileSnapshot {
        if (frames.isEmpty()) return snapshot()
        isBatchingAssistantChunks = true
        try {
            frames.forEach { (rawJson, frame) -> acceptFrame(rawJson, frame, correlatedSessionId) }
        } finally {
            isBatchingAssistantChunks = false
            // 即使中途抛异常也必须落地，否则缓冲会跨调用泄漏到下一次批量。
            flushAssistantChunks()
        }
        return snapshot()
    }

    /** 累积快通道 chunk；未处于批量模式时立即提交，保持 [acceptFrame] 的同步语义。 */
    private fun bufferAssistantChunks(sessionId: String, attemptId: String, chunksJson: String) {
        val chunks = runCatching { adapterJson.decodeFromString<List<AssistantChunk>>(chunksJson) }
            .getOrElse {
                // 解析失败：先落地已缓冲内容，再按原语义同步提交本帧。
                flushAssistantChunks()
                conversationStore.assistantChunks(sessionId, attemptId, chunksJson)
                return
            }
        if (pendingAssistantSessionId != sessionId || pendingAssistantAttemptId != attemptId) {
            flushAssistantChunks()
            pendingAssistantSessionId = sessionId
            pendingAssistantAttemptId = attemptId
        }
        pendingAssistantChunks += chunks
        if (!isBatchingAssistantChunks) flushAssistantChunks()
    }

    /** 把缓冲的 chunk 一次性交给共享 Conversation Store；无缓冲时为 no-op。 */
    private fun flushAssistantChunks() {
        val sessionId = pendingAssistantSessionId
        val attemptId = pendingAssistantAttemptId
        if (sessionId == null || attemptId == null || pendingAssistantChunks.isEmpty()) {
            pendingAssistantChunks.clear()
            pendingAssistantSessionId = null
            pendingAssistantAttemptId = null
            return
        }
        val chunksJson = adapterJson.encodeToString(pendingAssistantChunks.toList())
        pendingAssistantChunks.clear()
        pendingAssistantSessionId = null
        pendingAssistantAttemptId = null
        conversationStore.assistantChunks(sessionId, attemptId, chunksJson)
    }

    fun reset(): SharedMobileSnapshot {
        cancelSnapshotWait()
        // 丢弃未提交的 chunk 缓冲，避免旧 attempt 的内容泄漏到 reset 之后。
        pendingAssistantChunks.clear()
        pendingAssistantSessionId = null
        pendingAssistantAttemptId = null
        assistantStream = AssistantStreamState()
        usesAssistantStream = false
        (historyEvents.keys + queueSessionIds).toSet().forEach {
            historyStore.clearSession(it)
            conversationStore.clearSession(it)
        }
        historyEvents.clear()
        trajectoryHistorySessionId = null
        trajectoryHistoryEvents = null
        trajectoryHistoryNodes = emptyList()
        historyLastSequences.clear()
        historyErrors.clear()
        historyHasMore.clear()
        historySessionStates.clear()
        historyPendingSessionId = null
        liveSessionIds.clear()
        conversationItems.clear()
        conversationLastSequences.clear()
        queueSessionIds = emptySet()
        controlSnapshot = mobileStore.reset()
        lastFrameKind = null
        lastError = null
        return snapshot()
    }

    fun loadFixture(): SharedMobileSnapshot {
        reset()
        controlSnapshot = mobileStore.loadManualTestFixture()
        controlSnapshot.selectedSessionId?.let { sessionId ->
            conversationItems[sessionId] = controlSnapshot.conversation
        }
        lastFrameKind = controlSnapshot.lastFrameKind
        return snapshot()
    }

    fun close() {
        historySubscription.cancel()
        conversationSubscription.cancel()
    }

    fun historyTimedOut(sessionId: String) {
        historyErrors[sessionId] = "历史记录加载超时，请重试"
        if (snapshotWait?.first == sessionId) {
            snapshotWait = null
            lastError = "历史记录加载超时，请重试"
        }
        historyStore.timedOut(sessionId)
    }

    fun historyCancelled(sessionId: String) {
        if (snapshotWait?.first == sessionId) snapshotWait = null
        historyStore.cancelled(sessionId)
    }

    private fun awaitSnapshot(sessionId: String) {
        historyErrors.remove(sessionId)
        cancelSnapshotWait()
        historyStore.awaitSnapshot(sessionId)
        snapshotWait = sessionId to ++waitGeneration
    }

    private fun cancelSnapshotWait() {
        snapshotWait?.first?.let(historyStore::cancelled)
        snapshotWait = null
    }

    fun trajectory(sessionId: String?): List<TrajectoryNode> {
        if (sessionId == null) return emptyList()
        val events = historyEvents[sessionId].orEmpty()
        if (trajectoryHistorySessionId != sessionId || trajectoryHistoryEvents !== events) {
            trajectoryHistoryNodes = TrajectoryProjection.make(events)
            trajectoryHistoryEvents = events
            trajectoryHistorySessionId = sessionId
        }
        val transient = assistantStream.transientTrajectoryNodes(sessionId)
        return if (transient.isEmpty()) trajectoryHistoryNodes else trajectoryHistoryNodes + transient
    }

    internal fun acceptHistoryMviEventForTest(event: SharedMviEvent) = acceptHistoryMviEvent(event)

    internal fun acceptConversationMviEventForTest(event: SharedMviEvent) =
        acceptConversationMviEvent(event)

    private fun acceptHistory(frame: GatewayFrame, correlatedSessionId: String?) {
        val sessionId = correlatedSessionId?.takeIf(String::isNotBlank)
        if (sessionId == null) {
            lastError = "history-correlation-missing"
            return
        }
        historyErrors.remove(sessionId)
        val normalized = frame.events.orEmpty().map { it.normalized(sessionId) }
        historyStore.processingStarted(sessionId, normalized.size, frame.hasMore == true)
        val result = historyStore.pageReceived(
            sessionId = sessionId,
            eventsJson = adapterJson.encodeToString(normalized),
            byteCount = frame.bytes ?: 0,
            hasMore = frame.hasMore == true,
            nextBeforeSequence = frame.nextBeforeSeq,
            remoteActivityTimestamp = frame.time
        )
        if (!result.accepted) {
            lastError = result.errorCode ?: "history-page-failed"
            return
        }
        // 只在页面真正被接受、且确实带着事件时置位：被拒绝的帧没有安装任何内容，
        // 若也打上标记，会永久跳过该会话的缓存播种（直到下次 hello/reset）。
        if (normalized.isNotEmpty()) liveSessionIds += sessionId
        historyHasMore[sessionId] = frame.hasMore == true
        conversationStore.replaceSession(sessionId, adapterJson.encodeToString(historyEvents[sessionId].orEmpty()))
        assistantStream.activeAttemptId()?.takeIf { assistantStream.hasBaseline(sessionId) }?.let {
            conversationStore.assistantChunks(sessionId, it, assistantStream.replayChunksJson())
        }
    }

    private fun acceptLive(rawJson: String, frame: GatewayFrame) {
        val sessionId = frame.sessionId
        val sequence = frame.seq
        val timestamp = frame.time
        val gatewayEvent = frame.event
        if (sessionId.isNullOrBlank() || sequence == null || timestamp == null || gatewayEvent == null) {
            lastError = "live-event-invalid"
            return
        }
        // ConversationStore 已经增量处理 token。旧 MobileStore 会为每个 chunk 复制全部
        // SessionEvent 并从头重建 Conversation；长回复因此越到后面越慢。chunk 不会更新
        // Session 列表元数据，禁止再进入这条兼容性全量投影路径。
        if (gatewayEvent.type != "assistant/chunk") {
            controlSnapshot = mobileStore.acceptFrame(rawJson)
        }
        val record = SessionEvent(sessionId, sequence, timestamp, gatewayEvent, frame.surfaceOp, frame.sourceEventSeqs)
        val recordJson = adapterJson.encodeToString(record)
        val historyResult = historyStore.liveEventReceived(recordJson)
        if (!historyResult.accepted) {
            lastError = historyResult.errorCode ?: "history-live-failed"
            return
        }
        // 与 acceptHistory 同理：被拒绝的实时帧不构成权威内容，不能打标记。
        liveSessionIds += sessionId
        val conversationResult = conversationStore.receiveEvent(recordJson)
        if (!conversationResult.accepted) {
            conversationStore.replaceSession(sessionId, adapterJson.encodeToString(historyEvents[sessionId].orEmpty()))
        }
    }

    private fun acceptHistoryMviEvent(event: SharedMviEvent) {
        if (!historyEnvelope.validate(event)) {
            lastError = "history-envelope-invalid"
            return
        }
        val plan = runCatching { planHistoryEvent(event) }.getOrElse {
            historyEnvelope.reject()
            lastError = "history-adapter-failed"
            return
        }
        historyEvents.clear()
        historyEvents.putAll(plan.eventsBySession)
        historyLastSequences.clear()
        historyLastSequences.putAll(plan.lastSequencesBySession)
        historySessionStates.clear()
        historySessionStates.putAll(plan.sessionStatesBySession)
        historyPendingSessionId = plan.pendingSessionId
        historyEnvelope.commit(event)
        if (event.kind == "error") lastError = event.errorCode ?: "history-store-error"
        // 基线/分页落地后写一次缓存；不含每 token 路径。
        if (event.kind != "error") {
            plan.eventsBySession.keys.forEach(onConversationBaselineChanged)
        }
        plan.effects.forEach { effect ->
            runCatching { onHistoryPageRequested(effect.sessionId, effect.beforeSequence, assistantStream.formatVersion(effect.sessionId)) }
                .onFailure { lastError = "history-effect-failed" }
        }
    }

    private fun planHistoryEvent(event: SharedMviEvent): HistoryPlan {
        if (event.kind == "error") {
            require(event.statePayloadJson == null && decodeHistoryEffects(event).isEmpty())
            require(!event.errorCode.isNullOrBlank())
            return HistoryPlan(
                historyEvents.toMap(),
                historyLastSequences.toMap(),
                historySessionStates.toMap(),
                historyPendingSessionId,
                emptyList()
            )
        }
        if (event.kind == "snapshot") {
            val bootstrap = adapterJson.decodeFromString<SharedHistoryBootstrap>(
                requireNotNull(event.statePayloadJson)
            )
            require(bootstrap.schema == 1 && decodeHistoryEffects(event).isEmpty())
            bootstrap.eventsBySession.forEach { (sessionId, records) ->
                require(sessionId.isNotBlank() && records.all { it.sessionId == sessionId })
                require(records.zipWithNext().all { (left, right) -> left.seq < right.seq })
            }
            return HistoryPlan(
                bootstrap.eventsBySession,
                bootstrap.eventsBySession.mapValues { it.value.lastOrNull()?.seq ?: -1 },
                bootstrap.state.sessions,
                bootstrap.state.pendingSessionId,
                emptyList()
            )
        }
        require(event.kind == "transition")
        val patch = adapterJson.decodeFromString<SharedHistoryPatch>(
            requireNotNull(event.statePayloadJson)
        )
        require(patch.schema == 1 && patch.sessionId.isNotBlank())
        require(patch.outcome in HISTORY_OUTCOMES)
        val effects = decodeHistoryEffects(event)
        require(effects.all { it.action == "request-page" && it.sessionId == patch.sessionId })
        require((patch.outcome == "request-page") == (effects.size == 1))
        val next = historyEvents.toMutableMap()
        val nextSequences = historyLastSequences.toMutableMap()
        val nextSessionStates = historySessionStates.toMutableMap()
        patch.session?.let { nextSessionStates[patch.sessionId] = it }
        val nextPendingSessionId = if (patch.pendingSessionChanged) {
            patch.pendingSessionId
        } else {
            historyPendingSessionId
        }
        patch.eventPatch?.let { eventPatch ->
            val old = next[patch.sessionId].orEmpty()
            next[patch.sessionId] = when (eventPatch.kind) {
                "replace" -> {
                    val replacement = requireNotNull(eventPatch.replacementEvents)
                    require(eventPatch.record == null)
                    require(replacement.all { it.sessionId == patch.sessionId })
                    require(replacement.zipWithNext().all { (left, right) -> left.seq < right.seq })
                    val previousTail = nextSequences[patch.sessionId] ?: old.lastOrNull()?.seq ?: -1
                    val replacementTail = replacement.lastOrNull()?.seq ?: -1
                    require(replacement.isEmpty() || replacementTail >= previousTail)
                    if (replacement.isEmpty()) nextSequences.remove(patch.sessionId)
                    else nextSequences[patch.sessionId] = replacementTail
                    replacement
                }
                "append", "upsert" -> {
                    val record = requireNotNull(eventPatch.record)
                    require(eventPatch.replacementEvents == null && record.sessionId == patch.sessionId)
                    val previousTail = nextSequences[patch.sessionId] ?: old.lastOrNull()?.seq ?: -1
                    if (eventPatch.kind == "append") {
                        require(record.seq > previousTail && eventPatch.index == old.size)
                    } else {
                        require(record.seq <= previousTail && eventPatch.index != null)
                    }
                    val merged = old.associateBy(SessionEvent::seq).toMutableMap().apply {
                        put(record.seq, record)
                    }.values.sortedBy(SessionEvent::seq)
                    eventPatch.index?.let { require(it == merged.indexOfFirst { item -> item.seq == record.seq }) }
                    nextSequences[patch.sessionId] = maxOf(previousTail, record.seq)
                    merged
                }
                else -> error("unknown history patch kind")
            }
        }
        return HistoryPlan(
            next,
            nextSequences,
            nextSessionStates,
            nextPendingSessionId,
            effects
        )
    }

    private fun decodeHistoryEffects(event: SharedMviEvent): List<SharedHistoryEffect> =
        adapterJson.decodeFromString(event.effectsJson)

    private fun acceptConversationMviEvent(event: SharedMviEvent) {
        if (!conversationEnvelope.validate(event)) {
            lastError = "conversation-envelope-invalid"
            return
        }
        val plan = runCatching { planConversationEvent(event) }.getOrElse {
            conversationEnvelope.reject()
            lastError = "conversation-adapter-failed"
            return
        }
        conversationItems.clear()
        conversationItems.putAll(plan.itemsBySession)
        conversationLastSequences.clear()
        conversationLastSequences.putAll(plan.lastSequencesBySession)
        conversationEnvelope.commit(event)
        if (event.kind == "error") lastError = event.errorCode ?: "conversation-store-error"
    }

    private fun planConversationEvent(event: SharedMviEvent): ConversationPlan {
        // Conversation 路径的 effectsJson 恒为 "[]"（SharedConversationStore.dispatch 不传该字段）。
        // 逐 token 反序列化一个必然为空的数组是纯浪费，改为字符串比较。
        require(event.effectsJson.isBlank() || event.effectsJson == "[]")
        if (event.kind == "error") {
            require(event.statePayloadJson == null && !event.errorCode.isNullOrBlank())
            return ConversationPlan(conversationItems.toMap(), conversationLastSequences.toMap())
        }
        if (event.kind == "snapshot") {
            val bootstrap = adapterJson.decodeFromString<SharedConversationBootstrap>(
                requireNotNull(event.statePayloadJson)
            )
            require(bootstrap.schema == 1)
            return ConversationPlan(conversationItems.toMap(), conversationLastSequences.toMap())
        }
        require(event.kind == "transition")
        val patch = adapterJson.decodeFromString<SharedConversationPatch>(
            requireNotNull(event.statePayloadJson)
        )
        require(patch.schema == 1 && patch.sessionId.isNotBlank() && patch.lastSequence >= -1)
        val next = conversationItems.toMutableMap()
        val nextSequences = conversationLastSequences.toMutableMap()
        val previousSequence = nextSequences[patch.sessionId] ?: -1
        if (patch.replacesAll) {
            val replacement = requireNotNull(patch.replacementItems)
            require(patch.operations.isEmpty())
            require(replacement.map(ConversationItem::id).distinct().size == replacement.size)
            val isExplicitClear = replacement.isEmpty() && patch.lastSequence == -1
            require(isExplicitClear || patch.lastSequence >= previousSequence)
            next[patch.sessionId] = replacement
            if (isExplicitClear) nextSequences.remove(patch.sessionId)
            else nextSequences[patch.sessionId] = patch.lastSequence
            return ConversationPlan(next, nextSequences)
        }
        require(patch.replacementItems == null)
        // 不可见持久事件可以只推进水位；独立临时流仍不能使水位倒退。
        require(patch.lastSequence >= previousSequence)
        require(patch.operations.isNotEmpty() || patch.lastSequence > previousSequence)
        val items = next[patch.sessionId].orEmpty().toMutableList()
        // 流式 append-text 每 token 触发一次；线性 indexOfFirst 会让单会话成本退化为
        // O(行数) × O(token)。这里维护 id -> index 索引，把定位降到 O(1)。
        val indexById = HashMap<String, Int>(items.size * 2)
        items.forEachIndexed { index, item -> indexById[item.id] = index }
        patch.operations.forEach { operation ->
            when (operation.kind) {
                "insert" -> {
                    val item = requireNotNull(operation.item)
                    require(operation.itemId == null && operation.delta == null)
                    require(!indexById.containsKey(item.id))
                    indexById[item.id] = items.size
                    items += item
                }
                "append-text" -> {
                    require(operation.item == null && !operation.itemId.isNullOrBlank() && operation.delta != null)
                    val itemId = requireNotNull(operation.itemId)
                    val index = indexById[itemId]
                    requireNotNull(index) { "append-text 目标行不存在" }
                    val old = items[index]
                    items[index] = old.copy(
                        text = old.text + operation.delta,
                        epochSeconds = operation.epochSeconds ?: old.epochSeconds
                    )
                }
                "replace" -> {
                    val item = requireNotNull(operation.item)
                    require(operation.itemId == item.id && operation.delta == null && operation.epochSeconds == null)
                    val index = indexById[item.id]
                    requireNotNull(index) { "replace 目标行不存在" }
                    items[index] = item
                }
                "remove" -> {
                    require(operation.item == null && !operation.itemId.isNullOrBlank() && operation.delta == null)
                    val itemId = requireNotNull(operation.itemId)
                    val index = indexById.remove(itemId)
                    requireNotNull(index) { "remove 目标行不存在" }
                    items.removeAt(index)
                    // 删除会令其后所有下标左移；remove 在流式路径上罕见，重建即可。
                    indexById.clear()
                    items.forEachIndexed { shiftedIndex, item -> indexById[item.id] = shiftedIndex }
                }
                else -> error("unknown conversation operation")
            }
        }
        next[patch.sessionId] = items
        nextSequences[patch.sessionId] = patch.lastSequence
        return ConversationPlan(next, nextSequences)
    }

    private data class HistoryPlan(
        val eventsBySession: Map<String, List<SessionEvent>>,
        val lastSequencesBySession: Map<String, Int>,
        val sessionStatesBySession: Map<String, HistorySessionState>,
        val pendingSessionId: String?,
        val effects: List<SharedHistoryEffect>
    )

    private data class ConversationPlan(
        val itemsBySession: Map<String, List<ConversationItem>>,
        val lastSequencesBySession: Map<String, Int>
    )

    companion object {
        private val HISTORY_OUTCOMES = setOf(
            "none", "request-page", "stopped", "completed", "failed"
        )
    }
}


/** 快通道文本增量；只有这类帧允许延迟合批，其余帧一律先 flush 以保序。 */
internal fun GatewayFrame.isAssistantStreamChunk(): Boolean {
    if (kind != "assistant-stream") return false
    val value = frame?.objectValue ?: return false
    return value["type"]?.stringValue == "chunk"
}

private fun mobileConversationLabels(): ConversationProjectionLabels {
    val isChinese = Locale.getDefault().language.startsWith("zh")
    return if (isChinese) {
        ConversationProjectionLabels(
            commandRunning = "正在执行…",
            commandCompacting = "正在压缩…",
            commandCompleted = "已完成",
            commandFailed = "执行失败",
            compactedHistory = "已压缩 {items} 条历史记录（约 {tokens} tokens）",
            interrupted = "生成已中断"
        )
    } else {
        ConversationProjectionLabels()
    }
}

internal class MviEnvelopeValidator(private val expectedDomain: String) {
    private var initialized = false
    private var failed = false
    private var lastSequence = 0L
    private val recentTransactions = ArrayDeque<String>()
    private val transactionSet = mutableSetOf<String>()
    internal val retainedTransactionCountForTest: Int get() = transactionSet.size

    fun validate(event: SharedMviEvent): Boolean {
        if (failed) return false
        val validBase = event.schema == 2 && event.domain == expectedDomain &&
            event.transactionId.isNotBlank() && event.transactionId !in transactionSet &&
            event.metadataJson == null
        val validSequence = when (event.kind) {
            "snapshot" -> !initialized
            "transition", "error" -> initialized && event.sequence == lastSequence + 1
            else -> false
        }
        if (!validBase || !validSequence) {
            failed = true
            return false
        }
        return true
    }

    fun commit(event: SharedMviEvent) {
        check(!failed)
        initialized = true
        lastSequence = event.sequence
        recentTransactions.addLast(event.transactionId)
        transactionSet += event.transactionId
        if (recentTransactions.size > MAXIMUM_RECENT_TRANSACTIONS) {
            transactionSet -= recentTransactions.removeFirst()
        }
    }

    fun reject() {
        failed = true
    }

    companion object {
        private const val MAXIMUM_RECENT_TRANSACTIONS = 64
    }
}
