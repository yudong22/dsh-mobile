package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.facade.SharedMviEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Android 消费端对**前缀增量 patch**（历史分页优化）的处理。
 *
 * 生产端（`SharedConversationStore`）在向后翻页时只发新增前缀 + `baseItemCount`；
 * 这里验证消费端拼接正确，并在**镜像与投影不同步时 fail-closed**——
 * 宁可拒绝也不能静默拼出错误列表。
 */
class ConversationPrefixPatchConsumerTest {

    private fun item(id: String, text: String) =
        """{"id":"$id","kind":"USER","title":"You","text":"$text","images":[],"isError":false,"epochSeconds":1.0}"""

    private fun transition(
        sequence: Long,
        transactionId: String,
        payload: String
    ) = SharedMviEvent(
        sequence = sequence,
        transactionId = transactionId,
        domain = "conversation",
        kind = "transition",
        statePayloadJson = payload
    )

    /** 首次基线：全量替换出 a、b 两行。 */
    private fun seedBaseline(projection: AndroidGatewayProjection) {
        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 1,
                transactionId = "conversation-replace:1",
                payload = """{"schema":1,"sessionId":"s","operations":[],"replacesAll":true,
                    "replacementItems":[${item("a", "A")},${item("b", "B")}],"lastSequence":10}"""
            )
        )
    }

    @Test
    fun prefixPatchPrependsNewerRowsAheadOfExistingOnes() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")
        assertEquals(listOf("A", "B"), projection.snapshot().conversation.map { it.text })

        // 向后翻页：更早的 ok1、ok0 插到前面；后缀 a、b 不传输
        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-replace:2",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("c", "OLDER1")},${item("d", "OLDER2")}],
                    "prefixReplaceCount":0,"baseItemCount":2,"lastSequence":10}"""
            )
        )

        assertNull(projection.snapshot().lastError)
        assertEquals(
            listOf("OLDER1", "OLDER2", "A", "B"),
            projection.snapshot().conversation.map { it.text }
        )
        projection.close()
    }

    /**
     * 行内容变化时（不只是新增）也必须正确：`prefixReplaceCount > 0` 表示
     * 镜像开头若干行被新前缀取代。
     */
    @Test
    fun prefixPatchCanReplaceExistingLeadingRows() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")

        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-replace:2",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("a2", "A2")}],
                    "prefixReplaceCount":1,"baseItemCount":2,"lastSequence":10}"""
            )
        )

        assertNull(projection.snapshot().lastError)
        assertEquals(listOf("A2", "B"), projection.snapshot().conversation.map { it.text })
        projection.close()
    }

    /**
     * **fail-closed**：`baseItemCount` 与镜像行数不符（说明漏收过 patch）时必须拒绝，
     * 而不是尽力拼接。拒绝会让适配器永久失败——这是刻意的：
     * 拼错列表比停更更难发现。
     */
    @Test
    fun prefixPatchWithMismatchedBaseIsRejectedAndMirrorKeepsPreviousContent() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")

        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-replace:2",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("c", "X")}],
                    "prefixReplaceCount":0,"baseItemCount":99,"lastSequence":10}"""
            )
        )

        // 拒绝：错误被记录，且镜像**保持原内容**（未被污染）
        assertEquals("conversation-adapter-failed", projection.snapshot().lastError)
        assertEquals(listOf("A", "B"), projection.snapshot().conversation.map { it.text })
        projection.close()
    }

    @Test
    fun prefixPatchWithOutOfRangeReplaceCountIsRejected() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")

        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-replace:2",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("c", "X")}],
                    "prefixReplaceCount":5,"baseItemCount":2,"lastSequence":10}"""
            )
        )

        assertEquals("conversation-adapter-failed", projection.snapshot().lastError)
        assertEquals(listOf("A", "B"), projection.snapshot().conversation.map { it.text })
        projection.close()
    }

    /** 前缀 patch 不得同时携带 operations / replacementItems（形状互斥）。 */
    @Test
    fun prefixPatchCarryingOperationsIsRejected() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")

        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-replace:2",
                payload = """{"schema":1,"sessionId":"s",
                    "operations":[{"kind":"insert","item":${item("z", "Z")}}],
                    "prefixItems":[${item("c", "X")}],
                    "prefixReplaceCount":0,"baseItemCount":2,"lastSequence":10}"""
            )
        )

        assertEquals("conversation-adapter-failed", projection.snapshot().lastError)
        assertEquals(listOf("A", "B"), projection.snapshot().conversation.map { it.text })
        projection.close()
    }

    /** 校验失败不得静默：之后再有合法 patch 也不能假装成功（适配器已 fail-closed）。 */
    @Test
    fun rejectedPrefixPatchKeepsAdapterFailedClosed() {
        val projection = AndroidGatewayProjection()
        seedBaseline(projection)
        projection.selectSession("s")

        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 2,
                transactionId = "conversation-bad:2",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("c", "X")}],
                    "prefixReplaceCount":0,"baseItemCount":99,"lastSequence":10}"""
            )
        )
        assertEquals("conversation-adapter-failed", projection.snapshot().lastError)

        // 之后的合法 patch 会被 envelope 拒绝（failed 状态不可恢复）
        projection.acceptConversationMviEventForTest(
            transition(
                sequence = 3,
                transactionId = "conversation-replace:3",
                payload = """{"schema":1,"sessionId":"s","operations":[],
                    "prefixItems":[${item("c", "Y")}],
                    "prefixReplaceCount":0,"baseItemCount":2,"lastSequence":10}"""
            )
        )
        assertEquals(listOf("A", "B"), projection.snapshot().conversation.map { it.text })
        projection.close()
    }
}
