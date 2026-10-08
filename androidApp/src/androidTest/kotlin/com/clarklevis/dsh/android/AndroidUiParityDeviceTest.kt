package com.clarklevis.dsh.android

import android.view.WindowManager
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // 首页已精简：hero 标题、副标题与会话搜索框均已移除。
        // 全量会话列表仍由侧边抽屉承载（见 drawerOpensFromDeepSeekMarkAndNavigatesToPluginPage），
        // 首页正文只保留「当前项目目录下的最近活跃会话」，见
        // homeListsRecentSessionsForTheCurrentWorkspace。
        compose.onNode(hasTestTag("workspace-hero-title")).assertDoesNotExist()
        compose.onNode(hasTestTag("workspace-session-search")).assertDoesNotExist()
        compose.onNode(hasText("新建会话")).assertIsDisplayed()
        // 项目卡已退化为**纯指示**：断言它没有点击动作，而不是「点一下再断言菜单不存在」——
        // 后者在卡片仍可点但菜单坏掉时也会通过，等于测不出这条退化。
        compose.onNode(hasTestTag("workspace-card")).assertIsDisplayed().assertHasNoClickAction()
        compose.onNode(hasTestTag("workspace-menu")).assertDoesNotExist()
        compose.onNode(hasText("添加工作区")).assertDoesNotExist()
        compose.onNode(hasTestTag("workspace-ungrouped")).assertDoesNotExist()
        compose.onNode(hasText("全部会话")).assertDoesNotExist()
        compose.onNode(hasText("归档")).assertDoesNotExist()
        // 设备认证与设置已从顶栏下移到底栏 Tab。
        // 注意：底栏的设置 Tab 自身也带 contentDescription "设置"，因此不能只按
        // contentDescription 断言顶栏已无该入口，否则会误命中底栏节点。
        // 这一段必须在离开首页之前断言（下面点走「项目」Tab 后首页已不在栈顶）。
        compose.onNode(hasTestTag("brand-runtime-settings-button")).assertIsDisplayed()
        compose.onNode(hasContentDescription("设备认证", substring = true)).assertDoesNotExist()
        // 切换与新增项目的落点：底栏「项目」Tab（首页项目卡已不再承担该职责）。
        // 注意「项目」是独立页面、不带底栏，断言完必须返回首页才能继续点底栏 Tab。
        compose.onNode(hasTestTag("tab-projects")).performClick()
        compose.onNode(hasTestTag("projects-list")).assertIsDisplayed()
        compose.onNode(hasText("未分组", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("添加项目")).assertIsDisplayed()
        compose.onNode(hasContentDescription("返回")).performClick()
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
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

    /**
     * 首页正文必须在**当前项目目录**下列出最近活跃会话，而不是空着。
     *
     * 这一条有意断言得宽：设备测试跑在未配对/未连接的应用上（见 offlineNewSessionIsDisabled…），
     * 因此没有真实会话可分。可断言的是壳体确实挂上了、并且**没有**把「加载中」误写成
     * 「没有会话」——后者的文案在连接中只应出现进度词。有内容的排序/截断由
     * `HomeRecentSessionsTest` 的纯函数单测覆盖，不在这里靠网络碰运气。
     */
    @Test
    fun homeListsRecentSessionsForTheCurrentWorkspace() {
        compose.onNode(hasTestTag("home-recent-sessions")).assertIsDisplayed()
        compose.onNode(hasTestTag("home-recent-status")).assertIsDisplayed()
        compose.onNodeWithText("最近活跃").assertIsDisplayed()
        // 未连接时给的是引导语，不能出现带会话数的「暂无会话」式空态（那是连接中/已连接的语义）。
        compose.onNode(hasText("暂无会话")).assertDoesNotExist()
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

    /**
     * 底栏的内容区必须容得下每一项（[DshTabItem] 固定 72dp）。
     *
     * 回归自真机 bug：`DshBottomTabBar` 曾把 `height(84.dp)` 写在 `navigationBarsPadding()`
     * 之后，使 84dp 成为**含 inset 的总高**。真机导航栏 inset 为 20dp 时内容区只剩 64dp，
     * 小于 72dp，于是图标与标签被裁到屏幕外（真机上只剩「任务」两个字）。
     *
     * 注意断言的是**底栏自身高度**而非「是否在屏内」：in-set 大小随设备而异，
     * 在 inset 小的模拟器上旧写法也能通过「在屏内」的检查，因此那种断言抓不到这个 bug
     * （实测确认：旧的 assertIsDisplayed 版本在带 bug 的布局上同样通过）。
     * 这里直接比较底栏高度与单项所需高度，与设备 inset 无关。
     */
    @Test
    fun bottomBarContentHeightFitsEveryTabItem() {
        val barHeight = compose.onNodeWithTag("bottom-tab-bar")
            .fetchSemanticsNode().size.height

        // DshTabItem 使用 Modifier.height(72.dp)；底栏内容区必须不低于它，
        // 否则每项都会被裁掉一截。dp→px 用设备自身密度换算。
        val requiredPx = with(compose.density) { 72.dp.roundToPx() }
        assertTrue(
            "底栏高度($barHeight px)小于单项所需($requiredPx px)，标签会被裁切",
            barHeight >= requiredPx
        )
    }

    /**
     * 扫码已从顶栏下移到底栏：点按后在底栏原位展开认证菜单，含扫码与手动输入。
     */
    @Test
    fun bottomBarScanTabOpensTheAuthenticationMenu() {
        compose.onNode(hasTestTag("tab-scan")).performClick()
        compose.onNode(hasTestTag("gateway-auth-menu")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-scan")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-manual")).assertIsDisplayed()
    }
}
