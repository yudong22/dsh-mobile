package com.clarklevis.dsh.shared.facade

import com.clarklevis.dsh.shared.projection.ConversationItem
import com.clarklevis.dsh.shared.protocol.GatewayEvent
import com.clarklevis.dsh.shared.protocol.SessionEvent
import com.clarklevis.dsh.shared.protocol.wireJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 历史基线的**顺序不变量** + `replaceSession` 两个变体的等价性。
 *
 * 为什么钉这个：
 *  - 向后翻页取的是**更早**的事件（`loadOlderHistory` → `beforeSequence`），
 *    而 `ConversationProjector.insert` 是追加到末尾、MVI patch 也没有 prepend 操作。
 *    实测过：若把「全量重排」改成「增量追加」，旧消息会排到列表**末尾**
 *    （`[新-1, 新-2, 旧-1, 旧-2]`），是比慢更严重的回归。
 *  - 新增的列表重载（跳过 JSON 往返）必须与 JSON 变体产出**完全相同**的投影，
 *    否则 Android 与 iOS 会分叉。
 */
class SharedConversationBaselineTest {

    private fun event(seq: Int, text: String, sessionId: String = "s") = SessionEvent(
        sessionId = sessionId,
        seq = seq,
        time = 1_700_000_000.0 + seq,
        event = GatewayEvent(type = "user/message", text = text)
    )

    private fun patch(event: SharedMviEvent): SharedConversationPatch =
        wireJson.decodeFromString(event.statePayloadJson!!)

    /** 调一次 replace 并返回投影出的正文顺序。 */
    private fun projectionTexts(
        baseline: List<SessionEvent>,
        viaJson: Boolean
    ): List<String> {
        val store = SharedConversationStore()
        val received = mutableListOf<SharedMviEvent>()
        store.subscribe(SharedMviEventObserver(received::add))
        val result = if (viaJson) {
            store.replaceSession("s", wireJson.encodeToString(baseline))
        } else {
            store.replaceSession("s", baseline)
        }
        assertTrue(result.accepted, "baseline should be accepted")
        val replacement = patch(received.last())
        assertTrue(replacement.replacesAll)
        return replacement.replacementItems.orEmpty().map { it.text }
    }

    @Test
    fun `json baseline orders events by sequence`() {
        val outOfOrder = listOf(event(12, "新-2"), event(9, "旧-1"), event(11, "新-1"), event(10, "旧-2"))
        assertEquals(listOf("旧-1", "旧-2", "新-1", "新-2"), projectionTexts(outOfOrder, viaJson = true))
    }

    @Test
    fun `list baseline orders events by sequence`() {
        val outOfOrder = listOf(event(12, "新-2"), event(9, "旧-1"), event(11, "新-1"), event(10, "旧-2"))
        assertEquals(listOf("旧-1", "旧-2", "新-1", "新-2"), projectionTexts(outOfOrder, viaJson = false))
    }

    /** 两个变体必须产出相同的投影，否则 Android / iOS 会分叉。 */
    @Test
    fun `list and json baselines produce identical projections`() {
        val events = listOf(event(3, "c"), event(1, "a"), event(2, "b"))
        assertEquals(
            projectionTexts(events, viaJson = true),
            projectionTexts(events, viaJson = false)
        )
    }

    /** 向后翻页的真实形状：更早的一页必须排在已有内容**前面**。 */
    @Test
    fun `appending an older page keeps it ahead of newer content`() {
        val store = SharedConversationStore()
        val received = mutableListOf<SharedMviEvent>()
        store.subscribe(SharedMviEventObserver(received::add))

        // 断言的是**结果顺序**，不是传输形态：基线可能走全量替换，也可能走前缀增量
        // （见 SharedConversationPrefixPatchTest）。这里按消费端同样的规则逐步重建。
        var items = emptyList<ConversationItem>()
        fun applyLatest() {
            val p = patch(received.last { it.kind == "transition" })
            items = when {
                p.prefixItems != null -> p.prefixItems + items.drop(p.prefixReplaceCount)
                else -> p.replacementItems.orEmpty()
            }
        }

        store.replaceSession("s", listOf(event(11, "新-1"), event(12, "新-2")))
        applyLatest()
        assertEquals(listOf("新-1", "新-2"), items.map { it.text })

        // 向后翻页后整体重排：旧的在最前
        store.replaceSession(
            "s",
            listOf(event(9, "旧-1"), event(10, "旧-2"), event(11, "新-1"), event(12, "新-2"))
        )
        applyLatest()
        assertEquals(listOf("旧-1", "旧-2", "新-1", "新-2"), items.map { it.text })
    }

    @Test
    fun `cross-session baseline is rejected by both variants`() {
        val store = SharedConversationStore()
        val foreign = listOf(event(1, "x", sessionId = "other"))

        assertFalse(store.replaceSession("s", wireJson.encodeToString(foreign)).accepted)
        assertFalse(store.replaceSession("s", foreign).accepted)
    }
}
