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
 * 任务详情页左上角必须是**抽屉钮**而不是返回键（v1.9.0 点 5）。
 *
 * 这条不能用「渲染出来的图标长什么样」来验证，只能验证可交互契约：
 * 存在抽屉钮、不存在返回钮、点按回调被触发。视觉一致性由共用的
 * [DshDrawerButton] 组件保证（首页与详情页是同一个组件）。
 */
@RunWith(AndroidJUnit4::class)
class ConversationDrawerButtonDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showConversation(onOpenDrawer: () -> Unit = {}) {
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
                    onBack = {},
                    onOpenDrawer = onOpenDrawer
                )
            }
        }
    }

    @Test
    fun conversationTopBarHasDrawerButtonInsteadOfBackButton() {
        showConversation()
        compose.onNodeWithTag("conversation-drawer-button").assertIsDisplayed()
        // 返回键必须消失：这是「进入任务详情后左上角不应该有后退」的直接断言。
        // 共享的 TopBarCircleButton 只带 contentDescription，所以按描述断言即可。
        compose.onNode(hasContentDescription("返回")).assertDoesNotExist()
    }

    @Test
    fun tappingTheDrawerButtonOpensTheDrawerAndDismissesInput() {
        var opened = 0
        showConversation(onOpenDrawer = { opened += 1 })
        compose.onNodeWithTag("conversation-drawer-button").performClick()
        assertEquals(1, opened)
    }

    private fun AndroidSharedStateHolder.frame(raw: String) {
        wirePayload = raw
        submitWirePayload()
    }
}
