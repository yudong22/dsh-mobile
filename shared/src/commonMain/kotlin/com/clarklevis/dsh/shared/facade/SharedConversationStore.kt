package com.clarklevis.dsh.shared.facade

import com.clarklevis.dsh.shared.projection.ConversationItem
import com.clarklevis.dsh.shared.projection.ConversationProjectionLabels
import com.clarklevis.dsh.shared.projection.ConversationProjectionOperation
import com.clarklevis.dsh.shared.projection.ConversationProjector
import com.clarklevis.dsh.shared.protocol.SessionEvent
import com.clarklevis.dsh.shared.protocol.RawSessionEvent
import com.clarklevis.dsh.shared.protocol.JsonValue
import kotlin.time.Clock
import com.clarklevis.dsh.shared.protocol.wireJson
import com.clarklevis.dsh.shared.sync.AssistantChunk
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class SharedConversationBootstrap(val schema: Int = 1)

/**
 * 高频 Conversation 专用 patch。live token 只发送有序 operation；历史基线才使用
 * replacementItems，禁止复用低频 SessionControl 的字典快照协议。
 *
 * 除了全量替换（[replacesAll]）与行内 operation，还有一个**前缀增量**通道
 * （[prefixItems] / [prefixReplaceCount] / [baseItemCount]），供历史分页使用：
 * 向后翻页只是往列表**前面**插入更早的行，绝大多数行不变，没必要每页重传全部行。
 */
@Serializable
data class SharedConversationPatch(
    val schema: Int = 1,
    val sessionId: String,
    val operations: List<ConversationProjectionOperation> = emptyList(),
    val replacesAll: Boolean = false,
    val replacementItems: List<ConversationItem>? = null,
    val lastSequence: Int = -1,
    /**
     * 前缀增量的新表头：结果 = `prefixItems + 现有列表.drop(prefixReplaceCount)`。
     *
     * 用于历史分页（向后翻页）。只有 [baseItemCount] 与消费端当前行数一致时才有意义——
     * 消费端据此 fail-closed，避免投影与镜像分叉后静默拼出错误列表。
     */
    val prefixItems: List<ConversationItem>? = null,
    /**
     * 现有列表开头需要被 [prefixItems] 取代的行数。
     * 后续行（后缀）**不变**，因此不必传输。
     */
    val prefixReplaceCount: Int = 0,
    /**
     * 生成本 patch 时，服务端投影的行数。消费端用它校验自己与投影是否同步；
     * 不一致说明镜像已分叉，必须 fail-closed 而不是继续拼接。
     */
    val baseItemCount: Int = -1
)

/** KMP 持有逐 session projector；平台只 dispatch 原始事件并订阅增量 UI patch。 */
class SharedConversationStore(
    private val labels: ConversationProjectionLabels = ConversationProjectionLabels()
) {
    private val projectors = mutableMapOf<String, ConversationProjector>()
    private val steeringRecords = mutableMapOf<String, List<SessionEvent>>()
    private val events = SharedMviEventEmitter("conversation")

    fun subscribe(observer: SharedMviEventObserver): SharedMviSubscription = try {
        events.subscribe(observer, wireJson.encodeToString(SharedConversationBootstrap()))
    } catch (error: Throwable) {
        events.subscribeError(
            observer,
            "conversation-subscribe-failed",
            error.message ?: error::class.simpleName ?: "unknown-error"
        )
    }

    fun receiveEvent(eventJson: String): SharedMviDispatchResult = dispatch("event") {
        val record = wireJson.decodeFromString<SessionEvent>(eventJson)
        require(record.sessionId.isNotBlank()) { "sessionId must not be blank" }
        val projector = projectors.getOrPut(record.sessionId) { ConversationProjector(labels) }
        require(record.seq > projector.lastSequence) {
            "event sequence ${record.seq} is not newer than ${projector.lastSequence}; replace baseline first"
        }
        val operations = projector.foldWithOperations(listOf(record))
        // 不可见持久事件仍会推进流水位，必须发布给平台镜像。
        // 否则后续独立流携带正确水位时，iOS 会因镜像滞后而误判协议错误。
        SharedConversationPatch(
            sessionId = record.sessionId,
            operations = operations,
            lastSequence = projector.lastSequence
        )
    }

    /**
     * 单个 streaming 增量（**仅旧版 Mobile Gateway 路径**）。
     *
     * 历史背景（已核对主机与网关源码）：DSH 主机的 `session.seq` 是**逐事件**的 journal 下标
     * （`packages/core/session/src/index.ts` 的 `seq: SessionSeq(this.log.length)`），
     * 并非 turn 级水位。但 **rc.2 之前的 Mobile Gateway** 合成 `assistant/chunk` 时，
     * 每个 chunk 都复用「下一个持久事件」的 seq，导致同一 turn 内多个 chunk **共享同一个 seq**
     * （该网关自己的审计文档记录了这个形状；`a8dbe57` 已改为独立的 `assistant-stream` 帧）。
     *
     * 因此 [receiveEvent] 的单调性守卫对那条旧路径不适用，需要本方法绕过它。
     *
     * **现状**：当前网关发的是 `assistant-stream` 帧，**不带 seq**（改带
     * `revision`/`index`），由 [assistantChunks] / `foldAssistantChunks` 消费，
     * 根本不经过 [SessionEvent]。所以本方法目前只服务于旧版网关；
     * Android 平台也**尚未调用**它（iOS 调用了，见 `AppStore.swift`）。
     * 保留而不删除，是为了兼容仍在跑的旧版网关——它们仍会发同 seq 的 chunk。
     *
     * 该路径只做行内变更：projector 用 per-key stream index 定位目标行，既不重建历史，
     * 也不重新序列化整个事件列表。缺少已建立基线的 projector 时 fail closed——
     * 平台据此回退到 [replaceSession]，绝不静默丢弃内容。
     */
    fun receiveStreamDelta(eventJson: String): SharedMviDispatchResult = dispatch("stream") {
        val record = wireJson.decodeFromString<SessionEvent>(eventJson)
        require(record.sessionId.isNotBlank()) { "sessionId must not be blank" }
        val projector = projectors[record.sessionId]
            ?: error("no conversation baseline for session ${record.sessionId}; replace baseline first")
        val previousSequence = projector.lastSequence
        val operations = projector.foldWithOperations(listOf(record))
        if (operations.isEmpty() && projector.lastSequence == previousSequence) return@dispatch null
        SharedConversationPatch(
            sessionId = record.sessionId,
            operations = operations,
            lastSequence = projector.lastSequence
        )
    }

    /** 平台用它决定能否走 streaming 行内路径，否则回退到 baseline。 */
    fun hasProjection(sessionId: String): Boolean = projectors.containsKey(sessionId)

    fun assistantChunks(sessionId: String, attemptId: String, chunksJson: String): SharedMviDispatchResult =
        dispatch("assistant-chunks") {
            val chunks = wireJson.decodeFromString<List<AssistantChunk>>(chunksJson)
            val projector = projectors.getOrPut(sessionId) { ConversationProjector(labels) }
            val operations = projector.foldAssistantChunks(attemptId, chunks)
            if (operations.isEmpty()) null else SharedConversationPatch(
                sessionId = sessionId, operations = operations, lastSequence = projector.lastSequence
            )
        }

    fun clearAssistantChunks(sessionId: String): SharedMviDispatchResult = dispatch("assistant-clear") {
        val projector = projectors[sessionId] ?: return@dispatch null
        val operations = projector.clearAssistantChunks()
        if (operations.isEmpty()) null else SharedConversationPatch(
            sessionId = sessionId, operations = operations, lastSequence = projector.lastSequence
        )
    }

    fun replaceSteeringMessages(sessionId: String, itemsJson: String): SharedMviDispatchResult = dispatch("steering") {
        require(sessionId.isNotBlank())
        val items = requireNotNull(JsonValue.fromJsonElement(wireJson.parseToJsonElement(itemsJson)).arrayValue)
        val now = Clock.System.now().toEpochMilliseconds().toDouble()
        val records = items.filter { it["placement"]?.stringValue == "steering" }.map { row ->
            val message = requireNotNull(row["message"])
            require(!message["id"]?.stringValue.isNullOrBlank())
            requireNotNull(message["content"]?.arrayValue)
            RawSessionEvent("user/message", 0, now, message).normalized(sessionId)
        }
        steeringRecords[sessionId] = records
        val projector = projectors.getOrPut(sessionId) { ConversationProjector(labels) }
        val operations = projector.replaceSteeringMessages(records)
        if (operations.isEmpty()) null else SharedConversationPatch(
            sessionId = sessionId, operations = operations, lastSequence = projector.lastSequence
        )
    }

    /**
     * 用 JSON 基线整体替换某 session 的投影。
     *
     * 保留 JSON 变体是因为**跨语言边界**需要它：iOS 侧（`KMPSharedAdapter.swift`）
     * 只能以 `String` 与 KMP 通信。Android 侧**不应**再用这个变体——
     * 它手上本来就有 `List<SessionEvent>`，编码成 JSON 再让这里解码回来是纯浪费
     * （实测占 `replaceSession` 总耗时的 **92%**；见 [replaceSession] 的列表重载）。
     */
    fun replaceSession(sessionId: String, eventsJson: String): SharedMviDispatchResult =
        dispatch("replace") {
            require(sessionId.isNotBlank()) { "sessionId must not be blank" }
            val records = wireJson.decodeFromString<List<SessionEvent>>(eventsJson)
            require(records.all { it.sessionId == sessionId }) { "baseline contains another session" }
            replacementPatch(sessionId, records)
        }

    /**
     * 列表版基线替换：**跳过 JSON 往返**。
     *
     * 为什么需要它：分页/重连路径上，调用方（`AndroidGatewayProjection`）手里的
     * `historyEvents[sessionId]` 本来就是 `List<SessionEvent>`。此前它每页都
     * `adapterJson.encodeToString(...)` 编成字符串，再传给上面的 JSON 变体解码回列表，
     * 然后才 rebuild——**一次分页要序列化并反序列化整份累积历史**。
     *
     * 实测（40 页、每页 1 条 2KB 事件，累积路径）：
     * ```
     * encode 47.6% · decode 44.5% · 排序 2.6% · rebuild 5.3%
     * → 可省的 encode+decode 占 92.1%
     * ```
     * 且该放大随页数呈 **O(n²)**（翻第 N 页要编码前 N 页之和；30 页实测 29.2x）。
     *
     * **注意：这只是省掉冗余的序列化，不是把「全量重排」改成「增量追加」。**
     * 向后翻页取的是更早的事件，而投影的 `insert` 是**追加到末尾**、patch 也没有
     * prepend 操作——改成增量追加会把旧消息排到列表最后（实测顺序错误）。
     * 因此这里仍然全量 rebuild，只是不再多绕一趟 JSON。
     */
    fun replaceSession(sessionId: String, records: List<SessionEvent>): SharedMviDispatchResult =
        dispatch("replace") {
            require(sessionId.isNotBlank()) { "sessionId must not be blank" }
            require(records.all { it.sessionId == sessionId }) { "baseline contains another session" }
            replacementPatch(sessionId, records)
        }

    /**
     * [replaceSession] 两个变体共用的投影重建与 patch 构造。
     *
     * **为什么要做前缀 diff**：向后翻页只是往列表**前面**插入更早的行，已有行几乎不变。
     * 但原先每页都发 `replacesAll + 全部行`，于是 MVI 边界每页序列化整份投影
     * （实测 30 页/150 条消息累积 2316 KiB，是单遍的 9.0x，且随深度 O(n²) 增长）。
     *
     * **为什么这样是安全的**：`ConversationItem.id = "${sessionId}-${seq}"` 由 seq 决定，
     * 重建是确定性的。这里用**数据类全等**求最长公共后缀：
     * 结果恒等于 `nextItems`，与全量替换**逐项相同**——若中间某行确实变了，
     * 公共后缀自然变短、更多行进前缀，正确性不依赖任何假设。
     *
     * 增量不划算时（例如首次基线、列表被整体改写）退回全量替换。
     */
    private fun replacementPatch(
        sessionId: String,
        records: List<SessionEvent>
    ): SharedConversationPatch {
        val normalized = records.associateBy(SessionEvent::seq).values.sortedBy(SessionEvent::seq)
        val projector = ConversationProjector(labels).apply { rebuild(normalized) }
        projector.replaceSteeringMessages(steeringRecords[sessionId].orEmpty())
        val nextItems = projector.items
        val previousItems = projectors[sessionId]?.items.orEmpty()
        projectors[sessionId] = projector
        return baselinePatch(sessionId, previousItems, nextItems, projector.lastSequence)
    }

    /**
     * 在「全量替换」与「前缀增量」之间选择，两者语义**完全等价**，
     * 只影响跨边界的载荷大小。
     *
     * 只在「确实两头都有内容」时走增量（`suffixLength > 0 && prefixLength > 0`）：
     *  - `prefixLength == nextItems.size`（没有保留任何后缀）→ 首次基线或整体改写，全量更直观；
     *  - `prefixLength == 0`（前面没新增）→ 没有可省的内容，全量代价相同。
     * 这样也天然避开「投影侧为空、镜像侧非空」的退化情形（如测试用 `loadFixture`
     * 直接填镜像而不经本 store），不会让消费端的 `baseItemCount` 校验误触发。
     */
    private fun baselinePatch(
        sessionId: String,
        previousItems: List<ConversationItem>,
        nextItems: List<ConversationItem>,
        lastSequence: Int
    ): SharedConversationPatch {
        val suffixLength = commonSuffixLength(previousItems, nextItems)
        val prefixLength = nextItems.size - suffixLength
        val replacedCount = previousItems.size - suffixLength
        if (suffixLength == 0 || prefixLength == 0) {
            return SharedConversationPatch(
                sessionId = sessionId,
                replacesAll = true,
                replacementItems = nextItems,
                lastSequence = lastSequence
            )
        }
        return SharedConversationPatch(
            sessionId = sessionId,
            prefixItems = nextItems.take(prefixLength),
            prefixReplaceCount = replacedCount,
            baseItemCount = previousItems.size,
            lastSequence = lastSequence
        )
    }

    /** 两个列表从尾部开始的**数据类全等**连续长度（不做任何结构假设）。 */
    private fun commonSuffixLength(
        previous: List<ConversationItem>,
        next: List<ConversationItem>
    ): Int {
        val max = minOf(previous.size, next.size)
        var matched = 0
        while (matched < max && previous[previous.size - 1 - matched] == next[next.size - 1 - matched]) {
            matched += 1
        }
        return matched
    }

    fun clearSession(sessionId: String): SharedMviDispatchResult = dispatch("clear") {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        projectors.remove(sessionId)
        steeringRecords.remove(sessionId)
        SharedConversationPatch(
            sessionId = sessionId,
            replacesAll = true,
            replacementItems = emptyList(),
            lastSequence = -1
        )
    }

    private inline fun dispatch(
        operation: String,
        block: () -> SharedConversationPatch?
    ): SharedMviDispatchResult = try {
        val patch = block()
        if (patch == null) {
            SharedMviDispatchResult(true, null, null)
        } else {
            val event = events.emitTransition(
                transactionId = "conversation-$operation:${events.currentSequence + 1}",
                statePayloadJson = wireJson.encodeToString(patch)
            )
            SharedMviDispatchResult(true, event.transactionId, event.sequence)
        }
    } catch (error: Throwable) {
        val code = "conversation-$operation-failed"
        val message = error.message ?: error::class.simpleName ?: "unknown-error"
        val event = events.emitError("$code:${events.currentSequence + 1}", code, message)
        SharedMviDispatchResult(false, event.transactionId, event.sequence, code, message)
    }
}
