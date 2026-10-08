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
import androidx.compose.ui.test.performScrollToIndex
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

    /**
     * 判定「键盘确实占了足够高度」的门槛（px）。
     *
     * 下面断言要求气泡上移 > 100px，而位移与 IME inset 近似 1:1，
     * 因此门槛必须明显高于 100，否则「键盘只占 58px」这种半可用状态
     * 会通过前置检查却必然断言失败。
     */
    private val minimumImeInsetPx = 150

    companion object {
        /** 夹具推入 14 条消息（见 `showConversation`），最后一条的下标即 13。 */
        private const val LAST_MESSAGE_INDEX = 13
    }

    /**
     * 聚焦输入框后，输入区必须被软键盘顶起；点时间线应取消焦点并收起键盘。
     *
     * **断言锚点用输入框而不是最后一条消息气泡**：气泡在 `LazyColumn` 里，会被回收——
     * 探针实测约 1/4 的运行中聚焦后它整整 5 秒都不在语义树里（`bubbleSeen=0`），
     * 于是以「条件超时」失败。这与 IME、与产品行为都无关，是夹具依赖了可回收节点。
     * 输入框则始终被组合，且它正是 `imePadding()` 直接作用的那个节点，
     * 语义上更贴切（实测改用该锚点后连续 8 次全绿）。
     */
    @Test
    fun inputFocusMovesLatestMessageAboveImeAndTimelineTapHidesIme() {
        val fixture = showConversation()
        val composer = compose.onNodeWithTag("composer-input")
        // 先滚到底，保证讨论的「最后一条」确实存在（也让用例自证夹具是完整的）。
        compose.onNodeWithTag("conversation-timeline-list").performScrollToIndex(LAST_MESSAGE_INDEX)
        compose.onNode(
            hasTestTag("user-text-bubble") and hasText("末尾标记", substring = true)
        ).assertIsDisplayed()
        val composerTopBeforeIme = composer.fetchSemanticsNode().boundsInRoot.top

        composer.performClick().assertIsFocused()
        waitForIme(fixture.view, visible = true)
        // 本轮必须真的被键盘顶起：IME 可见但几乎不占高度（模拟器开了硬件键盘时就是这种）时，
        // `imePadding()` 自然不移动任何东西，下面的断言会以「条件超时」这种看不出原因的方式
        // 失败。这里把环境前提显式化，让它以可读的原因跳过，而不是伪装成产品回归。
        assumeImeOccupiesSpace(fixture.view)
        compose.waitUntil(timeoutMillis = 5_000) {
            composer.fetchSemanticsNode().boundsInRoot.top < composerTopBeforeIme - 100f
        }

        compose.onNodeWithTag("conversation-timeline").performTouchInput {
            click(Offset(center.x, 40f))
        }

        composer.assertIsNotFocused()
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
     * 断言 IME 真的占了**足够**高度，否则跳过。
     *
     * 模拟器/AOSP 镜像带硬件键盘时（AVD `hw.keyboard=yes`，本仓库默认镜像就是），软键盘
     * 「可见」但几乎不占高度：`WindowInsets.ime()` 可能是 0，也可能只有几十像素。
     * 那种环境下本用例测不出任何东西，却会以条件超时失败——历史上它正是这样被误记成
     * 产品回归的（见 `Docs/test-evidence/2026-10-07-ui-redesign/verification.md`）。
     *
     * 实测（同机对照）：`hw.keyboard=yes` → ime 可见但 bottom=0、气泡仅移动 58px；
     * `hw.keyboard=no` → bottom=883、气泡移动 878px。注意只设
     * `settings put secure show_ime_with_hard_keyboard 1` **不足以**产生 inset，
     * 必须用不带硬件键盘的 AVD。
     *
     * 阈值取 [minimumImeInsetPx]：它必须明显大于下面断言要求的 100px 位移，
     * 否则「键盘只占 58px」这种半可用状态会通过前置检查却又必然断言失败。
     * （实测该状态下 6 次里失败 1 次，属环境不确定性而非产品问题。）
     */
    private fun assumeImeOccupiesSpace(view: View) {
        val imeBottom = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        assumeTrue(
            "软键盘可见但不占足够高度（ime inset=${imeBottom}px，需要 > ${minimumImeInsetPx}px）：" +
                "该 AVD 启用了硬件键盘，本用例无法验证 IME 位移。" +
                "请改用 `hw.keyboard=no` 的 AVD 重跑（关闭模拟器后改 " +
                "~/.android/avd/<name>.avd/config.ini 的 hw.keyboard，再重启模拟器）。" +
                "详见 Docs/pre-existing-device-failures-repair.md。",
            imeBottom > minimumImeInsetPx
        )
    }

    private data class Fixture(
        val holder: AndroidSharedStateHolder,
        val view: View
    )
}
