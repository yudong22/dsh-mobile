package com.clarklevis.dsh.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏路由映射与动画判定的回归测试（v1.9.0 点 2 / 点 6）。
 *
 * 这两条都是「看代码对、上真机才发现不对」的类型：
 * - 选中态错 → 点完 Tab 后高亮还停在上一个，用户以为没切过去；
 * - 动画判定错 → 平级切换出现下钻的滑入动画（点 2 的原始抱怨），
 *   或者更糟：真正的下钻变成瞬移，失去「进了一层」的反馈。
 */
class DshTabRoutingTest {

    @Test
    fun everyBottomBarDestinationMapsToItsTab() {
        assertEquals(DshTab.TASKS, dshTabForRoute("workspace"))
        assertEquals(DshTab.PROJECTS, dshTabForRoute("projects"))
        assertEquals(DshTab.SCHEDULES, dshTabForRoute("scheduled-tasks"))
        assertEquals(DshTab.SETTINGS, dshTabForRoute("settings"))
    }

    /** 会话详情归入「任务列表」：它是某条任务的详情，不是独立目的地。 */
    @Test
    fun conversationDetailHighlightsTheTasksTab() {
        assertEquals(DshTab.TASKS, dshTabForRoute("conversation"))
    }

    /** 二级下钻页没有底栏。 */
    @Test
    fun drillDownPagesHaveNoBottomBar() {
        assertNull(dshTabForRoute("plugins"))
        assertNull(dshTabForRoute("settings/agent-presets"))
        assertNull(dshTabForRoute("settings/default-model"))
        assertNull(dshTabForRoute(null))
    }

    /** 平级 Tab 之间切换：不播滑动动画（点 2 的核心诉求）。 */
    @Test
    fun switchingBetweenBottomBarDestinationsIsNotAnimated() {
        assertTrue(isTabToTab("workspace", "projects"))
        assertTrue(isTabToTab("workspace", "settings"))
        assertTrue(isTabToTab("workspace", "scheduled-tasks"))
        assertTrue(isTabToTab("settings", "projects"))
        assertTrue(isTabToTab("scheduled-tasks", "workspace"))
    }

    /**
     * 进入/离开会话详情仍是下钻：必须保留滑动。
     *
     * 会话页虽挂底栏（归在「任务列表」下），但不能因此被当成平级目的地——
     * 那会把「进入任务」变成瞬移，丢掉层级反馈。
     */
    @Test
    fun enteringConversationDetailStaysAnimated() {
        assertFalse(isTabToTab("workspace", "conversation"))
        assertFalse(isTabToTab("projects", "conversation"))
        assertFalse(isTabToTab("conversation", "workspace"))
    }

    /** 下钻页与 Tab 页之间也要保留动画（例如设置 → Agent 预设）。 */
    @Test
    fun drillingIntoSubPagesStaysAnimated() {
        assertFalse(isTabToTab("settings", "settings/agent-presets"))
        assertFalse(isTabToTab("settings/agent-presets", "settings"))
        assertFalse(isTabToTab("workspace", "plugins"))
    }

    /** 同一目的地内的重组（launchSingleTop 重入）不该被误判成切换。 */
    @Test
    fun sameRouteIsNotATabSwitch() {
        assertFalse(isTabToTab("workspace", "workspace"))
        assertFalse(isTabToTab(null, "workspace"))
        assertFalse(isTabToTab("workspace", null))
    }

    /**
     * 已在会话页时必须**不再导航**，否则抽屉连续切任务会压满返回栈（点 5 的回归点）。
     *
     * 抽屉现在可以在会话页打开，这是提升到应用外壳后新出现的路径；
     * 提升前它不可达，所以这条曾经不是问题。
     */
    @Test
    fun switchingTasksFromInsideConversationDoesNotNavigateAgain() {
        assertFalse(requiresConversationNavigation("conversation"))
    }

    /** 从其它任何页面打开任务都必须导航过去。 */
    @Test
    fun openingATaskFromOtherPagesNavigatesToConversation() {
        assertTrue(requiresConversationNavigation("workspace"))
        assertTrue(requiresConversationNavigation("projects"))
        assertTrue(requiresConversationNavigation("scheduled-tasks"))
        assertTrue(requiresConversationNavigation("settings"))
        assertTrue(requiresConversationNavigation(null))
    }
}
