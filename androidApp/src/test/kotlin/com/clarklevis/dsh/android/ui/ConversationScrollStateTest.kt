package com.clarklevis.dsh.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationScrollStateTest {
    @Test
    fun `bottom delta includes the full last row and composer padding`() {
        assertEquals(
            1_000f,
            timelineBottomScrollDelta(
                lastItemOffset = 0,
                lastItemSize = 1_600,
                viewportEndOffset = 800,
                afterContentPadding = 200
            )
        )
        assertEquals(
            0f,
            timelineBottomScrollDelta(
                lastItemOffset = 500,
                lastItemSize = 100,
                viewportEndOffset = 800,
                afterContentPadding = 200
            )
        )
    }

    @Test
    fun `the list is pinned only when it cannot scroll farther forward`() {
        assertTrue(isTimelinePinnedToBottom(totalItems = 0, canScrollForward = false))
        assertTrue(isTimelinePinnedToBottom(totalItems = 8, canScrollForward = false))
        assertFalse(isTimelinePinnedToBottom(totalItems = 8, canScrollForward = true))
    }

    @Test
    fun `streamed layout changes cannot unpin without a scroll`() {
        assertFalse(
            shouldPublishPinnedState(
                isScrolling = false,
                pinned = false,
                isProgrammaticScroll = false
            )
        )
        assertTrue(
            shouldPublishPinnedState(
                isScrolling = true,
                pinned = false,
                isProgrammaticScroll = false
            )
        )
        assertFalse(
            shouldPublishPinnedState(
                isScrolling = true,
                pinned = false,
                isProgrammaticScroll = true
            )
        )
        assertTrue(
            shouldPublishPinnedState(
                isScrolling = false,
                pinned = true,
                isProgrammaticScroll = true
            )
        )
    }

    @Test
    fun `older history requires a real backward user scroll near the top`() {
        assertFalse(
            shouldLoadOlderHistory(
                HistoryPagingGestureState(0, false, true, false)
            )
        )
        assertFalse(
            shouldLoadOlderHistory(
                HistoryPagingGestureState(0, true, true, true)
            )
        )
        assertFalse(
            shouldLoadOlderHistory(
                HistoryPagingGestureState(0, true, false, false)
            )
        )
        assertFalse(
            shouldLoadOlderHistory(
                HistoryPagingGestureState(3, true, true, false)
            )
        )
        assertTrue(
            shouldLoadOlderHistory(
                HistoryPagingGestureState(2, true, true, false)
            )
        )
    }

    @Test
    fun `initial history overlay only covers a cold baseline load`() {
        assertTrue(
            shouldShowInitialHistoryOverlay(
                isLoading = true,
                isLoadingOlder = false,
                hasLocalContent = false
            )
        )
        assertFalse(
            shouldShowInitialHistoryOverlay(
                isLoading = true,
                isLoadingOlder = false,
                hasLocalContent = true
            )
        )
        assertFalse(
            shouldShowInitialHistoryOverlay(
                isLoading = true,
                isLoadingOlder = true,
                hasLocalContent = true
            )
        )
        assertFalse(
            shouldShowInitialHistoryOverlay(
                isLoading = false,
                isLoadingOlder = false,
                hasLocalContent = false
            )
        )
        assertFalse(
            shouldShowInitialHistoryOverlay(
                isLoading = true,
                isLoadingOlder = false,
                hasLocalContent = true
            )
        )
    }

    @Test
    fun `history progress text follows the ios states`() {
        assertEquals(
            "正在从 Mobile Gateway 同步会话内容…",
            historyLoadingProgressText(0, null)
        )
        assertEquals(
            "正在自动加载更早记录 · 已同步 12 个事件",
            historyLoadingProgressText(12, null)
        )
        assertEquals(
            "正在加载历史记录 · 12/20",
            historyLoadingProgressText(12, 20)
        )
    }

    /**
     * 已有旧内容、刷新失败时必须提示。
     *
     * 这是「消息流不更新却没有提示」的回归守卫：此前 `selectedHistoryError` 只在
     * 列表为空的分支里被读，于是**只要本地还有旧消息，刷新失败/快照超时就是静默的**，
     * 用户看到一份静止的旧消息流、既没有 loading 也没有错误、也没有刷新入口。
     */
    @Test
    fun `stale content with a history error is surfaced`() {
        assertTrue(
            shouldShowStaleHistoryNotice(
                error = "历史记录加载超时，请重试",
                isLoading = false,
                hasLocalContent = true
            )
        )
    }

    /** 列表为空时不走这条：那种情况由原来的全屏错误块承担，避免重复提示。 */
    @Test
    fun `empty timeline keeps using the full screen error block`() {
        assertFalse(
            shouldShowStaleHistoryNotice(
                error = "历史记录加载超时，请重试",
                isLoading = false,
                hasLocalContent = false
            )
        )
    }

    /** 正在加载时不提示错误——尚未失败，提示会误导。 */
    @Test
    fun `no stale notice while a reload is in flight`() {
        assertFalse(
            shouldShowStaleHistoryNotice(
                error = "历史记录加载超时，请重试",
                isLoading = true,
                hasLocalContent = true
            )
        )
    }

    /** 没有错误时不提示。 */
    @Test
    fun `no stale notice without an error`() {
        assertFalse(
            shouldShowStaleHistoryNotice(error = null, isLoading = false, hasLocalContent = true)
        )
    }

    /** 成功加载后提示必须消失（错误被清空即可）。 */
    @Test
    fun `stale notice clears once the error is gone`() {
        assertFalse(
            shouldShowStaleHistoryNotice(error = null, isLoading = false, hasLocalContent = true)
        )
    }
}
