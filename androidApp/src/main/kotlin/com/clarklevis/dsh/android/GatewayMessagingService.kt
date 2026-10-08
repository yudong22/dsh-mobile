package com.clarklevis.dsh.android

import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import com.clarklevis.dsh.shared.gateway.GatewayPushRegistration
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 接收网关投递的远程通知。
 *
 * 与本地通知的分工：本地通知由 socket 帧驱动，而 App 进入后台时 socket 已断开
 * （见 `GatewayRuntime` 的后台挂起路径），因此离线期间的事件只能由这里接收。
 * 两者靠 `dedupeKey` 去重，避免同一事件弹两次。
 *
 * 未配置 Firebase 时（没有 `google-services.json`）本服务不会被注册，通知不投递；
 * 这不是错误状态：App 仍可经前台服务保活，只是失去离线提醒。
 */
class GatewayMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        if (token.isBlank()) return
        Log.i(TAG, "FCM token rotated")
        // 按平台保存单个地址，token 轮换后旧地址会静默失效，必须立即重报。
        AndroidPushRegistrationStore.save(applicationContext, token)
        AndroidPushRegistrationStore.consume()?.let { registration ->
            scope.launch { AndroidPushRegistrationBus.submit(registration) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val gatewayId = message.data[GATEWAY_ID_KEY] ?: return
        val sessionId = message.data[SESSION_ID_KEY] ?: return
        val kind = message.data[KIND_KEY] ?: "unknown"
        // 后台展示由系统完成；这里只登记去重标识与跳转目标。
        scope.launch {
            AndroidPushRegistrationStore.recordDelivered(
                applicationContext,
                message.data[DEDUPE_KEY_KEY]
            )
            Log.i(TAG, "push delivered: kind=$kind session=$sessionId gateway=$gatewayId")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GatewayMessaging"
        const val GATEWAY_ID_KEY = "gatewayId"
        const val SESSION_ID_KEY = "sessionId"
        const val DEDUPE_KEY_KEY = "dedupeKey"
        const val KIND_KEY = "kind"
    }
}

/** 便于测试与预览的注册表构造。 */
internal fun fcmRegistration(token: String): GatewayPushRegistration =
    GatewayPushRegistration(platform = GatewayPushPlatform.FCM, token = token)