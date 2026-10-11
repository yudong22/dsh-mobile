package com.clarklevis.dsh.android

import android.content.Context
import android.util.Log
import com.xiaomi.mipush.sdk.MiPushClient

/**
 * 在小米/红米机型上注册 Mi Push（系统级推送，绕过 MIUI/HyperOS 的后台冻结，
 * 因此在 FCM 自启被冻结的机型上也能可靠送达）。
 *
 * 注册是**异步**的：成功与否、regId 都只在 [MiPushMessageReceiver.onReceiveRegisterResult]
 * 里回调，这里发出的调用只是发起握手。注册结果连同 regId 由接收器落盘并通过
 * [AndroidPushRegistrationBus] 上报网关。
 *
 * appId/appKey 来自 `local.properties`（`mipush.appId` / `mipush.appKey`），经
 * `BuildConfig` 注入；两者任一为空时直接跳过——非小米硬件或尚未配置 SDK 凭证时，
 * 应用照常构建与运行，只少一个推送通道。
 */
object MiPushInitializer {
    private const val TAG = "MiPushInit"

    fun register(context: Context) {
        val appId = BuildConfig.MIPUSH_APP_ID
        val appKey = BuildConfig.MIPUSH_APP_KEY
        if (appId.isBlank() || appKey.isBlank()) {
            Log.d(TAG, "Mi Push not configured (no appId/appKey in local.properties); skipping")
            return
        }
        // Mi Push 在每次冷启动都应重新注册：SDK 内部幂等，重复 register 不会重复推送。
        // 用 applicationContext 避免持有 Activity 引用导致泄漏。
        try {
            MiPushClient.registerPush(context.applicationContext, appId, appKey)
            Log.i(TAG, "Mi Push registerPush invoked (appId=$appId)")
        } catch (e: Throwable) {
            Log.w(TAG, "Mi Push registerPush failed: ${e.message}")
        }
    }
}
