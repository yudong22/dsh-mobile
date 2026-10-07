package com.clarklevis.dsh.android

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUiParityDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Suppress("DEPRECATION")
    fun activityUsesResizeInsteadOfPanningTheConversationAboveTheIme() {
        val adjustMode = compose.activity.window.attributes.softInputMode and
            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjustMode)
    }

    @Test
    fun workspaceChromeMatchesTheIosSourceAndExposesAccessibilitySemantics() {
        compose.onNode(hasTestTag("workspace-screen")).assertIsDisplayed()
        // 首页已精简：hero 标题、副标题、会话搜索框与任务列表均已移除，
        // 会话列表改由侧边抽屉承载（见 drawerOpensFromDeepSeekMarkAndNavigatesToPluginPage）。
        compose.onNode(hasTestTag("workspace-hero-title")).assertDoesNotExist()
        compose.onNode(hasTestTag("workspace-session-search")).assertDoesNotExist()
        compose.onNode(hasText("新建会话")).assertIsDisplayed()
        compose.onNode(hasTestTag("workspace-card")).performClick()
        compose.onNode(hasTestTag("workspace-menu")).assertIsDisplayed()
        compose.onNode(hasText("添加工作区")).assertIsDisplayed()
        compose.onNode(hasText("全部会话")).assertDoesNotExist()
        compose.onNode(hasTestTag("workspace-ungrouped")).performClick()
        compose.onNode(hasText("归档")).assertDoesNotExist()
        // 设备认证与设置已从顶栏下移到底栏 Tab。
        // 注意：底栏的设置 Tab 自身也带 contentDescription "设置"，因此不能只按
        // contentDescription 断言顶栏已无该入口，否则会误命中底栏节点。
        compose.onNode(hasTestTag("brand-runtime-settings-button")).assertIsDisplayed()
        compose.onNode(hasContentDescription("设备认证", substring = true)).assertDoesNotExist()
        compose.onNode(hasTestTag("tab-settings")).performClick()
        compose.onNode(hasText("新会话默认配置", substring = true)).assertIsDisplayed()
    }

    @Test
    fun offlineNewSessionOpensComposerWithoutShowingAnInternalSubscribeError() {
        compose.onNode(hasText("新建会话")).performClick()
        compose.onNode(hasTestTag("composer-input")).assertIsDisplayed()
        compose.onNode(hasText("unsubscribe: not-connected")).assertDoesNotExist()
    }

    @Test
    fun drawerOpensFromDeepSeekMarkAndNavigatesToPluginPage() {
        compose.onNode(hasContentDescription("打开侧边栏")).performClick()
        compose.onNode(hasTestTag("drawer-plugins")).assertIsDisplayed().performClick()
        compose.onNode(hasText("插件功能尚未接入")).assertIsDisplayed()
    }

    /**
     * 首页任务列表已移除，会话列表与重命名/删除入口改由抽屉承载。
     * 这里锁定抽屉确实渲染了「任务」区块，避免它再次退化成纯展示列表。
     */
    @Test
    fun drawerTaskSectionHostsTheSessionList() {
        compose.onNode(hasContentDescription("打开侧边栏")).performClick()
        compose.onNode(hasTestTag("drawer-new-task")).assertIsDisplayed()
        compose.onNode(hasText("任务")).assertIsDisplayed()
    }

    /**
     * 定时任务已从抽屉下移到主底栏，因此入口改为底栏 Tab。
     */
    @Test
    fun bottomBarSchedulesTabOpensScheduledTasks() {
        compose.onNode(hasTestTag("tab-schedules")).assertIsDisplayed().performClick()
        compose.onNode(hasTestTag("scheduled-tasks-screen")).assertIsDisplayed()
        compose.onNode(hasText("定时任务")).assertIsDisplayed()
    }

    /**
     * 底栏构成：任务 / 项目 / 定时任务 / 设置 / 扫码。
     * 「专家」「资料库」必须不再出现，避免回归。
     */
    @Test
    fun bottomBarShowsTheFiveDestinationsAndDropsExpertsAndLibrary() {
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-tasks")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-projects")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-schedules")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-settings")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-scan")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-experts")).assertDoesNotExist()
        compose.onNode(hasTestTag("tab-library")).assertDoesNotExist()
    }

    /** 扫码已从顶栏下移到底栏：点按后在底栏原位展开认证菜单，含扫码与手动输入。 */
    @Test
    fun bottomBarScanTabOpensTheAuthenticationMenu() {
        compose.onNode(hasTestTag("tab-scan")).performClick()
        compose.onNode(hasTestTag("gateway-auth-menu")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-scan")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-manual")).assertIsDisplayed()
    }
}
