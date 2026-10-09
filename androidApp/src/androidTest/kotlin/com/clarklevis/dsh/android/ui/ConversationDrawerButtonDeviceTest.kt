package com.clarklevis.dsh.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务详情页左上角是**返回键**（v1.9.4 起）。
 *
 * 此前这里是抽屉钮、且刻意没有返回键（v1.9.0 点 5：认为抽屉里切任务比返回列表更快）。
 * 现在改为返回键，理由有两点：
 *  - 详情页已不挂底栏（可视空间全部还给对话），抽屉钮同时是「退出详情」的唯一可见入口，
 *    语义上不如显式的返回键清楚；
 *  - 与首页/项目/定时任务/设置四个页面统一为「页头左上角 = 返回」。
 *
 * 这条不能用「渲染出来的图标长什么样」验证，只能验证可交互契约：
 * 存在返回钮、不存在抽屉钮、点按回调被触发。
 */
@RunWith(AndroidJUnit4::class)
class ConversationDrawerButtonDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showConversation(onBack: () -> Unit = {}) {
        val holder = AndroidSharedStateHolder().apply {
            frame("""{"kind":"hello","historyFormatVersion":3,"capabilities":["assistant-stream-v1"]}""")
            selectSession("s")
            frame("""{"kind":"subscribed","sessionId":"s","subscriptionId":"sub","assistantStream":true}""")
        }
        compose.setContent {
            DshTheme {
                ConversationScreen(
                    stateHolder = holder,
                    onPickImage = {},
                    onBack = onBack
                )
            }
        }
    }

    @Test
    fun conversationTopBarHasBackButtonInsteadOfDrawerButton() {
        showConversation()
        // 共享的 TopBarCircleButton 只带 contentDescription，所以按描述断言。
        compose.onNode(hasContentDescription("返回")).assertIsDisplayed()
        // 抽屉钮必须消失：详情页不再提供抽屉入口（底栏也已移除）。
        compose.onNode(hasTestTag("conversation-drawer-button")).assertDoesNotExist()
    }

    @Test
    fun tappingTheBackButtonInvokesTheCallback() {
        var backs = 0
        showConversation(onBack = { backs += 1 })
        compose.onNode(hasContentDescription("返回")).performClick()
        assertEquals(1, backs)
    }

    private fun AndroidSharedStateHolder.frame(raw: String) {
        wirePayload = raw
        submitWirePayload()
    }
}
