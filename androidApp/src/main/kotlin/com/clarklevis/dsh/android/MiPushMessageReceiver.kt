package com.clarklevis.dsh.android

import android.content.Context
import android.content.Intent
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import com.clarklevis.dsh.android.FCM_DATA_GATEWAY_ID
import com.clarklevis.dsh.android.FCM_DATA_SESSION_ID
import com.xiaomi.mipush.sdk.ErrorCode
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mi Push 的广播接收器（小米/红米的系统级推送，绕过 MIUI/HyperOS 的后台冻结，
 * 因此能在 FCM 自启被冻结的机型上可靠送达）。
 *
 * 与 FCM 的 `GatewayMessagingService` 不同，Mi Push 的注册结果和点击都走这个接收器：
 *  - [onReceiveRegisterResult]：注册成功时拿到 regId，落盘并经由 [AndroidPushRegistrationBus]
 *    上报网关（注册是异步的，regId 只在这里回调，不能同步取）。
 *  - [onNotificationMessageClicked]：用户点击通知时拿到 `MiPushMessage.getExtra()` 里的
 *    `gatewayId`/`sessionId`，直接打开对应会话——复用 FCM 分支已有的同名字段约定。
 *
 * 注册与点击的数据键名刻意与 FCM 一致，所以 [DshAndroidApplication.openNotificationSession]
 * 无需任何改动。
 */
class MiPushMessageReceiver : PushMessageReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage) {
        if (message.resultCode != ErrorCode.SUCCESS.toLong()) {
            Log.w(TAG, "Mi Push register failed: code=${message.resultCode} reason=${message.reason}")
            return
        }
        val regId = message.commandArguments?.firstOrNull()
        if (regId.isNullOrBlank()) {
            Log.w(TAG, "Mi Push register succeeded but returned a blank regId")
            return
        }
        Log.i(TAG, "Mi Push registered (regId length=${regId.length})")
        AndroidPushRegistrationStore.save(context, GatewayPushPlatform.MIPUSH, regId)
        scope.launch {
            AndroidPushRegistrationBus.submit(AndroidPushRegistrationStore.current(context))
        }
    }

    override fun onNotificationMessageClicked(context: Context, message: MiPushMessage) {
        val gatewayId = message.extra?.get(FCM_DATA_GATEWAY_ID)
        val sessionId = message.extra?.get(FCM_DATA_SESSION_ID)
        val intent = Intent().apply {
            // 与 FCM 数据键、[NotificationRouting]/[DshAndroidApplication] 约定保持一致，
            // 因此点击直接走既有的 FCM 分支打开会话，无需任何额外路由。
            putExtra(FCM_DATA_GATEWAY_ID, gatewayId)
            putExtra(FCM_DATA_SESSION_ID, sessionId)
        }
        if (gatewayId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            Log.w(TAG, "Mi Push click missing gatewayId/sessionId; opening app root")
        } else {
            Log.i(TAG, "Mi Push notification clicked: session=$sessionId gateway=$gatewayId")
        }
        DshAndroidApplication.openNotificationSession(context, intent)
    }

    override fun onCommandResult(context: Context, message: MiPushCommandMessage) {
        // 仅记录，避免静默吞掉解绑等命令的结果。
        Log.d(TAG, "Mi Push command result: command=${message.command} code=${message.resultCode}")
    }

    override fun onRequirePermissions(context: Context, permissions: Array<String>) {
        Log.d(TAG, "Mi Push requires permissions: ${permissions.contentToString()}")
    }

    override fun onReceivePassThroughMessage(context: Context, message: MiPushMessage) {
        // 透传消息此处不处理：本应用只用通知栏消息 + 点击深链。
        Log.d(TAG, "Mi Push pass-through received (ignored): ${message.content}")
    }

    override fun onNotificationMessageArrived(context: Context, message: MiPushMessage) {
        // 到达即展示，无需额外动作；去重由网关 `dedupeKey` 在点击/展示侧保障。
        Log.d(TAG, "Mi Push message arrived: ${message.title}")
    }

    private companion object {
        const val TAG = "MiPushReceiver"
    }
}
