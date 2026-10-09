package com.clarklevis.dsh.android.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 页头槽位宽度推导 —— **页头重叠缺陷的根因守卫**（毫秒级单测）。
 *
 * 背景：任务详情页左侧有「返回 + 抽屉」两个 40dp 钮，右侧只有「更多」一个。
 * 修复前右侧槽位被写死 40dp，而内容需要 88dp，于是两个钮重叠 63px、
 * 靠右那个被挤出屏幕（真机 uiautomator 实测，见
 * `Docs/v1.9.7-conversation-detail-plan.md` §1.1）。
 *
 * 这个不变量是缺陷的**充要条件**：只要 `n` 个钮的槽位宽 ≥ `n×40 + (n-1)×8`，
 * 就装得下、不会重叠。此前没有任何测试覆盖「槽位宽 vs 内容宽」的关系，
 * 所以缺陷能静默渲染出来。
 *
 * 这些原本由设备测试（`ConversationDrawerButtonDeviceTest`）验证；
 * 设备测试已按需求删除，改为在此保留同一不变量——**纯算术，不再依赖设备**。
 */
class DshPageHeaderSlotWidthTest {

    @Test
    fun `single button slot is exactly one circle`() {
        assertEquals(DshPageHeaderCircleButtonSize, dshPageHeaderSlotWidth(1))
    }

    @Test
    fun `no buttons still reserve one slot so the title stays centred`() {
        // Tab 根页面左侧无按钮，但仍要留等宽占位，标题才在整屏中线。
        assertEquals(DshPageHeaderCircleButtonSize, dshPageHeaderSlotWidth(0))
    }

    /** 详情页的关键用例：两个钮的槽位必须 >= 88dp（40 + 8 + 40）。 */
    @Test
    fun `two button slot fits both circles plus the gap`() {
        val expected = DshPageHeaderCircleButtonSize * 2 + DshPageHeaderButtonGap
        assertEquals(expected, dshPageHeaderSlotWidth(2))
        assertEquals(88.dp, dshPageHeaderSlotWidth(2))
    }

    @Test
    fun `slot always holds every button without overlap`() {
        (0..4).forEach { count ->
            val slot = dshPageHeaderSlotWidth(count)
            if (count == 0) return@forEach
            val needed = dshPageHeaderCircleButtonSize(count) +
                DshPageHeaderButtonGap * (count - 1)
            assertEquals(
                "槽位宽必须恰好装下 $count 个钮（不留缝、不重叠）",
                needed,
                slot
            )
        }
    }

    private fun dshPageHeaderCircleButtonSize(count: Int) =
        DshPageHeaderCircleButtonSize * count
}
