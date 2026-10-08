package com.clarklevis.dsh.android

import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.clarklevis.dsh.shared.gateway.GatewayConnectionState
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * 首页「最近活跃的项目会话」的真机链路验收：**必须**先启动
 * `scripts/multi_gateway_smoke_server.py`，并用 `-e homeRecentSmoke true` 显式启用；
 * 普通设备回归直接跳过（没有网关时首页必然只有空态）。
 *
 * 为什么这条不能只用纯函数单测替代：单测证明的是排序/过滤，证明不了「宿主推来的
 * `sessions` / `workspaces` 帧真的会出现在首页正文里」。这条测试把扫码配对、
 * 握手、`sessions` 帧、`workspaces` 帧、首页组合串起来跑一遍。
 */
class HomeRecentSessionsSmokeDeviceTest {
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

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val hosts get() =
        (instrumentation.targetContext.applicationContext as DshAndroidApplication).hosts

    @Test
    fun connectedGatewayShowsItsProjectSessionsOnTheHomeBody() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("homeRecentSmoke") == "true")
        try {
            eventually {
                hosts.ready && hosts.activeGraph.networkMonitor.state.value ==
                    com.clarklevis.dsh.shared.platform.GatewayNetworkState.AVAILABLE
            }
            pair("A", 18781, "d56a1098-8519-43a1-9dce-fb99863bf5bb")
            eventually { connected() }

            // 夹具的唯一会话挂在唯一项目下；首页正文必须把它显示出来，
            // 且标题右侧是「运行中」而不是相对时间（夹具 running=false，所以是时间）。
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodes(hasTestTag("home-session-same-session"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("home-recent-sessions").assertExists()
            // 列表不再截断，因此也不该再有「全部 N」这种「被截掉了，去别处看」的入口。
            compose.onNodeWithTag("home-recent-show-all").assertDoesNotExist()
            // 会话行点进去应打开会话页（首页正文不再只是展示）。
            // 断言用宿主状态 + 首页区块消失，而不是找输入框：会话页的空态/加载态
            // 会改变底栏 chrome 的可见性，输入框不是稳定锚点。
            compose.onNodeWithTag("home-session-same-session").performClick()
            eventually {
                hosts.activeGraph.stateHolder.snapshot.selectedSessionId == "same-session"
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasTestTag("home-recent-sessions"))
                    .fetchSemanticsNodes().isEmpty()
            }
        } finally {
            instrumentation.runOnMainSync {
                hosts.remove(hosts.profiles.filter { it.gatewayName == "Smoke A" })
            }
        }
    }

    private fun connected(): Boolean =
        hosts.activeGraph.gatewayRuntime.state.value.connection == GatewayConnectionState.CONNECTED &&
            hosts.activeGraph.stateHolder.snapshot.workspaces.isNotEmpty() &&
            hosts.activeGraph.stateHolder.snapshot.sessions.isNotEmpty()

    private fun pair(name: String, port: Int, gatewayId: String) {
        val payload = JSONObject()
            .put("version", 2)
            .put("publicUrl", "ws://10.0.2.2:$port/ws/mobile")
            .put("pairingCode", "smoke-$name")
            .put("expiresAt", System.currentTimeMillis() + 600_000)
            .put("gatewayId", gatewayId)
            .put("gatewayName", "Smoke $name")
        val encoded = Base64.encodeToString(
            payload.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        instrumentation.runOnMainSync { hosts.pair(encoded) }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            instrumentation.runOnMainSync { matches = condition() }
            if (matches) return
            SystemClock.sleep(100)
        }
        var detail = ""
        instrumentation.runOnMainSync {
            detail = "active=${hosts.activeProfile?.gatewayName}, error=${hosts.error}, " +
                "state=${hosts.activeGraph.gatewayRuntime.state.value.connection}"
        }
        throw AssertionError("等待模拟网关状态超时：$detail")
    }
}
