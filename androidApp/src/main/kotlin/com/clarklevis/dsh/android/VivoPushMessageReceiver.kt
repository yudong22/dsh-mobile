package com.clarklevis.dsh.android

import android.content.Context
import android.content.Intent
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import com.clarklevis.dsh.android.FCM_DATA_GATEWAY_ID
import com.clarklevis.dsh.android.FCM_DATA_SESSION_ID
import com.vivo.push.sdk.OpenClientPushMessageReceiver
import com.vivo.push.model.UPSNotificationMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * vivo 推送的广播接收器（vivo/iQOO 的系统级推送，绕过后台冻结，因此能在 FCM 自启被冻结
 * 的机型上可靠送达）。
 *
 * 与 Mi Push 的 [MiPushMessageReceiver] 同构：
 *  - [onReceiveRegId]：注册成功时拿到 regId，落盘并经由 [AndroidPushRegistrationBus]
 *    上报网关（注册是异步的，regId 只在这里回调，不能同步取）。
 *  - [onNotificationMessageClicked]：用户点击通知时拿到 [UPSNotificationMessage.getParams]
 *    里的 `gatewayId`/`sessionId`，直接打开对应会话——复用 FCM 分支已有的同名字段约定。
 *
 * vivo 的点击不像 Mi Push 那样用 `intent_uri` 深链，而是回调里显式启动 [MainActivity]，
 * 因此这里自行构建带 `gatewayId`/`sessionId` 的 Intent 并交给
 * [DshAndroidApplication.openNotificationSession]，与 FCM/Mi Push 完全一致的路由。
 */
class VivoPushMessageReceiver : OpenClientPushMessageReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceiveRegId(context: Context, regId: String?) {
        if (regId.isNullOrBlank()) {
            Log.w(TAG, "vivo push onReceiveRegId returned a blank regId")
            return
        }
        Log.i(TAG, "vivo push registered (regId length=${regId.length})")
        AndroidPushRegistrationStore.save(context, GatewayPushPlatform.VIVOPUSH, regId)
        scope.launch {
            AndroidPushRegistrationBus.submit(AndroidPushRegistrationStore.current(context))
        }
    }

    override fun onNotificationMessageClicked(context: Context, message: UPSNotificationMessage) {
        val params = message.params ?: emptyMap()
        val gatewayId = params[FCM_DATA_GATEWAY_ID]
        val sessionId = params[FCM_DATA_SESSION_ID]
        val intent = Intent().apply {
            // 与 FCM / Mi Push 数据键、[DshAndroidApplication] 约定保持一致：
            // 点击直接走既有的打开会话分支，无需任何额外路由。
            putExtra(FCM_DATA_GATEWAY_ID, gatewayId)
            putExtra(FCM_DATA_SESSION_ID, sessionId)
        }
        if (gatewayId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            Log.w(TAG, "vivo push click missing gatewayId/sessionId; opening app root")
        } else {
            Log.i(TAG, "vivo push notification clicked: session=$sessionId gateway=$gatewayId")
        }
        DshAndroidApplication.openNotificationSession(context, intent)
    }

    override fun onTransmissionMessage(context: Context, message: com.vivo.push.model.UnvarnishedMessage) {
        // 透传消息此处不处理：本应用只用通知栏消息 + 点击深链。
        Log.d(TAG, "vivo push transmission received (ignored): ${message.message}")
    }

    private companion object {
        const val TAG = "VivoPushReceiver"
    }
}
