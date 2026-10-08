package com.clarklevis.dsh.android.ui

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clarklevis.dsh.android.AndroidSharedStateHolder
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ConversationKeyboardDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inputFocusMovesLatestMessageAboveImeAndTimelineTapHidesIme() {
        val fixture = showConversation()
        val latestMessage = compose.onNode(
            hasTestTag("user-text-bubble") and hasText("末尾标记", substring = true)
        )
        latestMessage.assertIsDisplayed()
        val messageTopBeforeIme = latestMessage.fetchSemanticsNode().boundsInRoot.top

        compose.onNodeWithTag("composer-input").performClick().assertIsFocused()
        waitForIme(fixture.view, visible = true)
        // 本轮必须真的被键盘顶起：IME 可见但 inset 为 0（模拟器开了硬件键盘时就是这种）时，
        // `imePadding()` 自然不会移动任何东西，下面的断言会以「条件超时」这种看不出原因的方式
        // 失败。这里把这个环境前提显式化，让它以可读的原因失败/跳过，而不是伪装成产品回归。
        assumeImeOccupiesSpace(fixture.view)
        compose.waitUntil(timeoutMillis = 5_000) {
            latestMessage.fetchSemanticsNode().boundsInRoot.top < messageTopBeforeIme - 100f
        }

        compose.onNodeWithTag("conversation-timeline").performTouchInput {
            click(Offset(center.x, 40f))
        }

        compose.onNodeWithTag("composer-input").assertIsNotFocused()
        waitForIme(fixture.view, visible = false)
        fixture.holder.close()
    }

    @Test
    fun successfulSendClearsFocusAndHidesIme() {
        val fixture = showConversation()
        compose.runOnIdle { fixture.holder.messageDraft = "你好" }
        compose.onNodeWithTag("composer-input").performClick().assertIsFocused()
        waitForIme(fixture.view, visible = true)
        val submission = compose.runOnIdle {
            fixture.holder.captureMessageSubmissionForTest()
        }

        compose.runOnIdle {
            fixture.holder.applyMessageSendResultForTest(submission, sent = true)
        }

        compose.onNodeWithTag("composer-input").assertIsNotFocused()
        waitForIme(fixture.view, visible = false)
        fixture.holder.close()
    }

    private fun showConversation(): Fixture {
        val holder = AndroidSharedStateHolder().apply {
            wirePayload =
                """{"kind":"sessions","items":[{"sessionId":"keyboard-session","updatedAt":1786937352000,"running":false,"blank":false,"cwd":"/tmp/keyboard","agentPreset":"standard"}]}"""
            submitWirePayload()
            selectSession("keyboard-session")
            repeat(14) { index ->
                val text = if (index == 13) {
                    "末尾标记 ${"最后一条内容".repeat(8)}"
                } else {
                    "第${index + 1}条消息 ${"用于填充会话列表".repeat(8)}"
                }
                wirePayload =
                    """{"kind":"event","sessionId":"keyboard-session","seq":${index + 1},"time":${1786937353 + index},"event":{"type":"user/message","source":"user","text":"$text"}}"""
                submitWirePayload()
            }
        }
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            DshTheme {
                ConversationScreen(
                    stateHolder = holder,
                    onPickImage = {},
                    onBack = {}
                )
            }
        }
        return Fixture(holder, view)
    }

    private fun waitForIme(view: View, visible: Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
        }
    }

    /**
     * 断言 IME 真的占了高度（`ime()` inset > 0），否则跳过。
     *
     * 模拟器/AOSP 镜像带硬件键盘时（AVD `hw.keyboard=yes`，本仓库默认镜像就是），软键盘
     * 「可见」但不占高度：`WindowInsets.ime()` 变成 0，`imePadding()` 无位移。
     * 那种环境下本用例测不出任何东西，却会以 5s 条件超时失败——历史上它正是这样被误记成
     * 产品回归的（见 `Docs/test-evidence/2026-10-07-ui-redesign/verification.md`）。
     *
     * 实测（同机对照）：`hw.keyboard=yes` → ime 可见但 bottom=0、气泡仅移动 58px；
     * `hw.keyboard=no` → bottom=883、气泡移动 878px。注意只设
     * `settings put secure show_ime_with_hard_keyboard 1` **不足以**产生 inset，
     * 必须用不带硬件键盘的 AVD。
     */
    private fun assumeImeOccupiesSpace(view: View) {
        val imeBottom = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        assumeTrue(
            "软键盘可见但不占高度（ime inset=0）：该 AVD 启用了硬件键盘，本用例无法验证 IME 位移。" +
                "请改用 `hw.keyboard=no` 的 AVD 重跑（关闭模拟器后改 " +
                "~/.android/avd/<name>.avd/config.ini 的 hw.keyboard，再重启模拟器）。" +
                "详见 Docs/pre-existing-device-failures-repair.md。",
            imeBottom > 0
        )
    }

    private data class Fixture(
        val holder: AndroidSharedStateHolder,
        val view: View
    )
}
