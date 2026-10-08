package com.clarklevis.dsh.android

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

    /**
     * 未连接时「新建会话」必须置灰而不是可点后报错。
     *
     * 此前的版本叫 `offlineNewSessionOpensComposer...`，期望未连接也能打开输入框；
     * 但 `prepareNewSession()` 一直要求 CONNECTED，两者矛盾导致该测试长期失败。
     * 现在统一为「未连接即置灰」，并断言不会再弹出内部错误文案。
     */
    @Test
    fun offlineNewSessionIsDisabledInsteadOfFailing() {
        compose.onNode(hasTestTag("new-task-button")).assertIsNotEnabled()
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
     * 这里锁定抽屉确实渲染了「任务 (n)」区块与其中的会话列表容器，
     * 避免它再次退化成纯展示列表。
     *
     * 注意 `hasText` 默认是**精确匹配**，而该区块渲染的是 `任务 (0)` 这种带计数的文案，
     * 所以这里必须断言 testTag（或带 substring = true），不能写成 `hasText("任务")`——
     * 那样只会命中底栏的同名 Tab，删掉整个区块测试也照样通过。
     */
    @Test
    fun drawerTaskSectionHostsTheSessionList() {
        compose.onNode(hasContentDescription("打开侧边栏")).performClick()
        compose.onNode(hasTestTag("drawer-new-task")).assertIsDisplayed()
        compose.onNode(hasTestTag("drawer-section-任务")).assertIsDisplayed()
        compose.onNode(hasText("任务 (", substring = true)).assertIsDisplayed()
        // 会话列表容器：有会话时是 drawer-session-<id>，空态时是「暂无会话」占位。
        compose.onNode(hasTestTag("drawer-task-list")).assertIsDisplayed()
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
