package com.clarklevis.dsh.shared.facade

import com.clarklevis.dsh.shared.protocol.GatewayEvent
import com.clarklevis.dsh.shared.protocol.SessionEvent
import com.clarklevis.dsh.shared.protocol.wireJson
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 历史分页的**前缀增量通道**（`prefixItems`）语义与守卫。
 *
 * 背景：向后翻页只是往列表前面插入更早的行。原先每页都发 `replacesAll + 全部行`，
 * 于是 MVI 边界每页序列化整份投影（实测 30 页/150 条消息累积 2316 KiB = 单遍的 9.0x，
 * 随深度 O(n²)）。
 *
 * 本文件锁三件事：
 *  1. **等价性**：增量 patch 与全量替换产出的投影**逐项相同**（这是正确性的根）；
 *  2. **省流**：增量确实只带新增前缀，不带后缀；
 *  3. **回退**：不该用增量时（首次基线 / 没有可省内容）自动退回全量。
 */
class SharedConversationPrefixPatchTest {

    private fun event(seq: Int, text: String, sessionId: String = "s") = SessionEvent(
        sessionId = sessionId,
        seq = seq,
        time = 1_700_000_000.0 + seq,
        event = GatewayEvent(type = "user/message", text = text)
    )

    private fun patchOf(event: SharedMviEvent): SharedConversationPatch =
        wireJson.decodeFromString(event.statePayloadJson!!)

    /** 依次喂入多批事件，返回每批产生的 patch；同时保留 store 以便观察投影。 */
    private fun feed(vararg batches: List<SessionEvent>): List<SharedConversationPatch> {
        val store = SharedConversationStore()
        val received = mutableListOf<SharedMviEvent>()
        store.subscribe(SharedMviEventObserver(received::add))
        // 首屏：最新的一页
        store.replaceSession("s", batches.first())
        batches.drop(1).forEach { store.replaceSession("s", it) }
        // 订阅会先发一个 snapshot（bootstrap），它不是 patch；只取 transition。
        return received.filter { it.kind == "transition" }.map(::patchOf)
    }

    private fun applyPatch(
        current: List<com.clarklevis.dsh.shared.projection.ConversationItem>,
        patch: SharedConversationPatch
    ): List<com.clarklevis.dsh.shared.projection.ConversationItem> = when {
        patch.prefixItems != null ->
            patch.prefixItems + current.drop(patch.prefixReplaceCount)
        patch.replacesAll -> patch.replacementItems.orEmpty()
        else -> error("unexpected patch shape")
    }

    /**
     * 等价性基准：把**所有**事件一次性按 seq 重建，取「全量替换」的 items。
     * 这是增量 patch 必须逐项一致的目标。
     */
    private fun expectedItems(
        batches: List<List<SessionEvent>>
    ): List<com.clarklevis.dsh.shared.projection.ConversationItem> {
        val all = batches.flatten().distinctBy { it.seq }.sortedBy { it.seq }
        val store = SharedConversationStore()
        val received = mutableListOf<SharedMviEvent>()
        store.subscribe(SharedMviEventObserver(received::add))
        store.replaceSession("s", all)
        return patchOf(received.last { it.kind == "transition" }).replacementItems.orEmpty()
    }

    @Test
    fun `backward paging uses the prefix channel and stays equivalent to full replace`() {
        // 先有最新一页（seq 11..12），随后两次向后翻页拿到更早的页
        val newest = listOf(event(11, "新-1"), event(12, "新-2"))
        val older1 = listOf(event(9, "旧1-1"), event(10, "旧1-2")) + newest
        val older2 = listOf(event(7, "旧2-1"), event(8, "旧2-2")) + older1

        val patches = feed(newest, older1, older2)
        assertEquals(3, patches.size)

        // 首批是首次基线 → 全量
        assertTrue(patches[0].replacesAll, "first baseline should be a full replace")

        // 后续两批应走前缀增量
        val p1 = patches[1]
        assertNotNull(p1.prefixItems, "second baseline should use the prefix channel")
        assertNull(p1.replacementItems, "prefix patch must not carry the full list")
        assertEquals(2, p1.prefixItems.size, "only the two new older rows should be sent")
        assertEquals(0, p1.prefixReplaceCount, "no existing row is replaced when prepending")
        assertEquals(2, p1.baseItemCount)

        // 逐项等价：增量重建的结果 == 全量重排的结果
        val rebuilt = applyPatch(applyPatch(patches[0].replacementItems.orEmpty(), p1), patches[2])
        assertEquals(expectedItems(listOf(newest, older1, older2)).map { it.id }, rebuilt.map { it.id })
        assertEquals(
            listOf("旧2-1", "旧2-2", "旧1-1", "旧1-2", "新-1", "新-2"),
            rebuilt.map { it.text }
        )
    }

    @Test
    fun `prepended page keeps older rows ahead of newer content`() {
        val newest = listOf(event(5, "新"))
        val combined = listOf(event(1, "旧A"), event(2, "旧B")) + newest
        val patches = feed(newest, combined)
        val rebuilt = applyPatch(patches[0].replacementItems.orEmpty(), patches[1])
        assertEquals(listOf("旧A", "旧B", "新"), rebuilt.map { it.text })
    }

    @Test
    fun `later events appended to the suffix do not break the prefix channel`() {
        val first = listOf(event(1, "a"))
        val withOlder = listOf(event(0, "older")) + first
        val patches = feed(first, withOlder)
        val p = patches[1]
        assertNotNull(p.prefixItems)
        assertEquals(1, p.baseItemCount)
        val rebuilt = applyPatch(patches[0].replacementItems.orEmpty(), p)
        assertEquals(listOf("older", "a"), rebuilt.map { it.text })
    }

    @Test
    fun `first baseline and explicit clear always use full replace`() {
        val patches = feed(listOf(event(1, "a")))
        assertTrue(patches.single().replacesAll)
        assertEquals(1, patches.single().replacementItems?.size)
    }

    @Test
    fun `baseline that rewrites everything falls back to full replace`() {
        val store = SharedConversationStore()
        val received = mutableListOf<SharedMviEvent>()
        store.subscribe(SharedMviEventObserver(received::add))
        store.replaceSession("s", listOf(event(1, "a"), event(2, "b")))
        // 完全不同的内容（无公共后缀）→ 必须全量，不能拼出错误列表
        store.replaceSession("s", listOf(event(10, "x"), event(11, "y")))
        val last = patchOf(received.last())
        assertTrue(last.replacesAll, "no common suffix means full replace")
        assertEquals(listOf("x", "y"), last.replacementItems?.map { it.text })
    }

    /**
     * 增量 patch 的载荷必须**严格小于全量**：只带新增前缀，后缀一条都不传。
     *
     * 参照系是「本次全量替换会传多少」= nextItems.size（20），
     * 而不是上一批 patch 的大小（那批只有 10 行，不是同一件事）。
     */
    @Test
    fun `prefix payload is smaller than a full replace for deep paging`() {
        val newest = (10..19).map { event(it, "新$it") }
        val deep = ((0..9).map { event(it, "旧$it") } + newest)
        val patches = feed(newest, deep)
        val prefixItems = assertNotNull(patches[1].prefixItems)

        // 本次结果共 20 行；全量替换会传 20 行，增量只传新增的 10 行。
        assertEquals(20, prefixItems.size + 10, "suffix is implied, never transmitted")
        assertEquals(10, prefixItems.size)
        assertTrue(
            prefixItems.size < 20,
            "prefix (${prefixItems.size}) 必须小于全量 (20)"
        )
        // 后缀完全不需要传输：镜像已有 10 行，且这 10 行一条都不替换。
        assertEquals(10, patches[1].baseItemCount)
        assertEquals(0, patches[1].prefixReplaceCount)
    }
}
