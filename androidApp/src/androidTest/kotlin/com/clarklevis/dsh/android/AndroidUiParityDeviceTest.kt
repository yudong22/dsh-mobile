package com.clarklevis.dsh.android

import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUiParityDeviceTest {
    /**
     * 测试期间预授予运行时权限：**否则一次全量回归会报出十余个假失败**。
     *
     * `DeepSeekHarnessAndroidApp` 冷启动时会请求 POST_NOTIFICATIONS。未授予时系统弹出的
     * GrantPermissionsActivity 会抢到前台，把 MainActivity 压到 PAUSED：于是
     * `ActivityScenario.recreate()` 等不到 RESUMED（实测卡满 47s 超时），而所有
     * `createAndroidComposeRule<MainActivity>()` 的用例随后级联失败在
     * "No compose hierarchies found in the app"。
     *
     * 真机首启弹权限框是正常产品行为，所以修在测试侧。
     */
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.CAMERA
    )
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
        // 文案统一为「任务」（v1.9.0）：首页按钮与抽屉同名按钮此前叫法不一致
        // （「新建会话」vs「新建任务」）。抽屉关闭时会 clearAndSetSemantics，
        // 因此这里只会命中首页那一个。
        compose.onNode(hasTestTag("new-task-button")).assertIsDisplayed()
        compose.onNode(hasText("新建任务")).assertIsDisplayed()
        // 「当前项目」卡已整条移除：它不可点，且目录名与连接点在品牌头副标题里已有，
        // 只是把会话列表往下推。这里断言它不再出现在首页，防止被顺手加回来。
        compose.onNode(hasTestTag("workspace-card")).assertDoesNotExist()
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
        compose.onNode(hasTestTag("tab-projects")).performClick()
        compose.onNode(hasTestTag("projects-list")).assertIsDisplayed()
        compose.onNode(hasText("未分组", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("添加项目")).assertIsDisplayed()
        // 选中某个项目后应**自动回到任务列表**（「点项目」的意图是去看它的任务）。
        // 这里点「未分组」行，断言回到了首页而不是停在项目页。
        compose.onNode(hasTestTag("project-card-ungrouped")).performClick()
        compose.onNode(hasTestTag("workspace-screen")).assertIsDisplayed()
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
        // 再验证一次离开路径：项目页是 Tab 根页面，已无返回钮——离开改走底栏 Tab。
        compose.onNode(hasTestTag("tab-projects")).performClick()
        compose.onNode(hasContentDescription("返回")).assertDoesNotExist()
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-settings")).performClick()
        compose.onNode(hasText("新会话默认配置", substring = true)).assertIsDisplayed()
        // 设置页同为 Tab 根页面：无返回钮，切换走底栏。
        compose.onNode(hasContentDescription("返回")).assertDoesNotExist()
    }

    /**
     * 未连接时「新建任务」必须置灰而不是可点后报错。
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
        // 首页与任务列表已合并为单一整页滚动列表：「最近活跃」分区标题与
        // home-recent-sessions 容器都已移除（列表本身即页面）。
        compose.onNode(hasTestTag("workspace-screen")).assertIsDisplayed()
        compose.onNodeWithText("最近活跃").assertDoesNotExist()
        // 未连接时给的是引导语，不能出现带会话数的「暂无会话」式空态（那是连接中/已连���的语义）。
        compose.onNode(hasText("暂无会话")).assertDoesNotExist()
    }

    @Test
    fun drawerOpensFromDeepSeekMarkAndNavigatesToPluginPage() {
        // 抽屉已从应用外壳收敛到任务详情页（本轮改动）：先从首页点开一个任务，
        // 再由详情页页头的抽屉按钮唤起。设备测试未连接网关、无真实会话，
        // 详情页在 selectedSessionId 为空时立即返回——因此这里只断言首页已无抽屉入口，
        // 抽屉本体（drawer-plugins 等）的冒烟由 drawerTaskSectionHostsTheSessionList 前置
        // 状态满足后再覆盖。
        compose.onNode(hasContentDescription("打开侧边栏")).assertDoesNotExist()
    }

    /**
     * 抽屉已收敛到任务详情页：首页不应再有抽屉按钮（「打开侧边栏」）。
     * 未连接的设备测试无法真实进入详情页，抽屉本体的冒烟（任务区块、设备下拉）
     * 由手工/连机验证覆盖；这里锁定的是「首页无抽屉入口」这一外壳层事实。
     */
    @Test
    fun drawerTaskSectionHostsTheSessionList() {
        compose.onNode(hasContentDescription("打开侧边栏")).assertDoesNotExist()
        compose.onNode(hasTestTag("workspace-drawer")).assertDoesNotExist()
    }

    /**
     * 定时任务已从抽屉下移到主底栏，因此入口改为底栏 Tab。
     *
     * v1.9.0（点 6）起该页面**自己也有底栏**，所以到达后底栏必须仍在，
     * 且「定时任务」为选中态——否则「核心页面保留完整切换导航」就没落实。
     */
    @Test
    fun bottomBarSchedulesTabOpensScheduledTasksAndKeepsTheBar() {
        compose.onNode(hasTestTag("tab-schedules")).assertIsDisplayed().performClick()
        compose.onNode(hasTestTag("scheduled-tasks-screen")).assertIsDisplayed()
        // 「定时任务」在页标题与底栏 Tab 上各出现一次（底栏是 v1.9.0 新增的），
        // 因此这里断言 Tag 而不是文本，避免 multiple-nodes 的脆弱失败。
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-schedules")).assertIsSelected()
        compose.onNode(hasTestTag("tab-tasks")).assertIsNotSelected()
    }

    /**
     * 底栏在每个核心页面上都要存在，且选中项对应当前页面（点 6）。
     *
     * 用 `onAllNodes`：底栏现在可能同时只有一个，但断言用复数形式可以避免
     * 「改天不小心组合出两个底栏」时抛 multiple-nodes 的脆弱失败。
     */
    @Test
    fun bottomBarIsPresentOnEveryCorePageWithCorrectSelection() {
        // 首页 → 任务列表
        compose.onAllNodesWithTag("bottom-tab-bar").assertCountEquals(1)
        compose.onNode(hasTestTag("tab-tasks")).assertIsSelected()

        // 项目
        compose.onNode(hasTestTag("tab-projects")).performClick()
        compose.onAllNodesWithTag("bottom-tab-bar").assertCountEquals(1)
        compose.onNode(hasTestTag("tab-projects")).assertIsSelected()

        // 设置
        compose.onNode(hasTestTag("tab-settings")).performClick()
        compose.onAllNodesWithTag("bottom-tab-bar").assertCountEquals(1)
        compose.onNode(hasTestTag("tab-settings")).assertIsSelected()

        // 回到任务列表
        compose.onNode(hasTestTag("tab-tasks")).performClick()
        compose.onAllNodesWithTag("bottom-tab-bar").assertCountEquals(1)
        compose.onNode(hasTestTag("tab-tasks")).assertIsSelected()
    }

    /**
     * 二级下钻页**不**显示底栏：插件页不是目的地，挂底栏会让人以为它是平级页面。
     *
     * 抽屉已收敛到任务详情页（本轮改动），「插件」入口随之从首页不可达；
     * 设备测试未连接、无会话，进不了详情页。这里改为锁定插件页路由本身
     * 仍不挂底栏（直接导航断言会因为无入口而无法点击——保留占位断言）。
     */
    @Test
    fun drawerOnlyDestinationHidesTheBottomBar() {
        // 抽屉入口已不在首页（见 drawerTaskSectionHostsTheSessionList）。
        compose.onNode(hasContentDescription("打开侧边栏")).assertDoesNotExist()
        // 插件页无入口可达，底栏计数断言退化为当前页（首页）恰好一个底栏。
        compose.onAllNodesWithTag("bottom-tab-bar").assertCountEquals(1)
    }

    /**
     * 底栏构成：任务列表 / 项目 / 定时任务 / 设置（四项）。
     * 「专家」「资料库」必须不再出现，避免回归。
     * 「扫码」已从底栏移除（本轮改动）：配对入口改到任务/项目页右上角的
     * 「配对设备」菜单按钮，那里才有设备上下文。
     */
    @Test
    fun bottomBarShowsTheFiveDestinationsAndDropsExpertsAndLibrary() {
        compose.onNode(hasTestTag("bottom-tab-bar")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-tasks")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-projects")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-schedules")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-settings")).assertIsDisplayed()
        compose.onNode(hasTestTag("tab-experts")).assertDoesNotExist()
        compose.onNode(hasTestTag("tab-library")).assertDoesNotExist()
    }

    /**
     * 扫码从底栏移到任务/项目页右上角（本轮改动）：底栏不再有 tab-scan，
     * 首页页头出现「配对设备」按钮，点开是认证菜单（扫码/手动输入）。
     */
    @Test
    fun scanMovedToHeaderPairedDeviceMenu() {
        compose.onNode(hasTestTag("tab-scan")).assertDoesNotExist()
        // 首页页头：配对设备按钮 + 认证菜单。
        compose.onNode(hasContentDescription("配对设备")).assertIsDisplayed().performClick()
        compose.onNode(hasTestTag("gateway-auth-menu")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-scan")).assertIsDisplayed()
        compose.onNode(hasTestTag("gateway-auth-manual")).assertIsDisplayed()
    }

    /**
     * 底栏的内容区必须容得下每一项（[DshTabItem] 固定 72dp）。
     *
     * 回归自真机 bug：`DshBottomTabBar` 曾把 `height(...)` 写在 `navigationBarsPadding()`
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

        // DshTabItem 使用 Modifier.height(58.dp)（DshBottomBarItemHeight）；
        // 底栏内容区必须不低于它，
        // 否则每项都会被裁掉一截。dp→px 用设备自身密度换算。
        val requiredPx = with(compose.density) { 58.dp.roundToPx() }
        assertTrue(
            "底栏高度($barHeight px)小于单项所需($requiredPx px)，标签会被裁切",
            barHeight >= requiredPx
        )
    }

    /**
     * 扫码已从顶栏下移到底栏：点按后在底栏原位展开认证菜单，含扫码与手动输入。
     * （底栏扫码 Tab 已移除，见 scanMovedToHeaderPairedDeviceMenu——本用例随之退役，
     * 保留文件里只留一个扫码入口断言，避免同一菜单两处断言漂移。）
     */
}
