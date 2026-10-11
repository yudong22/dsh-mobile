package com.clarklevis.dsh.android

import android.content.Context
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import com.vivo.push.PushClient
import com.vivo.push.PushConfig
import com.vivo.push.listener.IPushQueryActionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 在 vivo/iQOO 机型上初始化 vivo 推送（系统级推送，绕过 vivo 后台冻结，
 * 因此在 FCM 自启被冻结的机型上也能可靠送达）。
 *
 * 与 Mi Push 相同，注册是**异步**的：regId 不在这里同步返回，而是经由
 * [VivoPushMessageReceiver.onReceiveRegId] 回调后落盘并上报网关。这里只负责
 * 初始化 SDK 并发起握手。
 *
 * appId/appKey 来自 `local.properties`（`vivo.appId` / `vivo.appKey`），经
 * `BuildConfig` 注入；两者任一为空时直接跳过——非 vivo 硬件或尚未配置 SDK 凭证时，
 * 应用照常构建与运行，只少一个推送通道。
 */
object VivoPushInitializer {
    private const val TAG = "VivoPushInit"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun register(context: Context) {
        val appId = BuildConfig.VIVO_APP_ID
        val appKey = BuildConfig.VIVO_APP_KEY
        if (appId.isBlank() || appKey.isBlank()) {
            Log.d(TAG, "vivo push not configured (no appId/appKey in local.properties); skipping")
            return
        }
        val applicationContext = context.applicationContext
        if (!PushClient.getInstance(applicationContext).isSupport) {
            Log.d(TAG, "vivo push unsupported on this device (not a vivo/iQOO handset); skipping")
            return
        }
        try {
            // agreePrivacyStatement(true) 是 vivo SDK 初始化前的硬性前置，未同意会抛异常。
            PushClient.getInstance(applicationContext).initialize(
                PushConfig.Builder().agreePrivacyStatement(true).build(),
            )
            // 向系统推送服务发起握手；regId 经由接收器回调。
            PushClient.getInstance(applicationContext).turnOnPush(object : com.vivo.push.IPushActionListener {
                override fun onStateChanged(state: Int) {
                    if (state != 0) {
                        Log.w(TAG, "vivo push turnOnPush state=$state")
                    } else {
                        Log.i(TAG, "vivo push turnOnPush invoked")
                    }
                }
            })
            // 主动查询一次：部分机型 regId 已在系统侧就绪，可直接拿到并上报，
            // 不必等下一次推送触发注册回调。
            PushClient.getInstance(applicationContext).getRegId(object : IPushQueryActionListener {
                override fun onSuccess(result: String?) {
                    val regId = result?.takeIf { it.isNotBlank() }
                    if (regId == null) {
                        Log.d(TAG, "vivo push getRegId returned blank; will wait for receiver callback")
                        return
                    }
                    Log.i(TAG, "vivo push getRegId succeeded (regId length=${regId.length})")
                    AndroidPushRegistrationStore.save(applicationContext, GatewayPushPlatform.VIVOPUSH, regId)
                    scope.launch {
                        AndroidPushRegistrationBus.submit(AndroidPushRegistrationStore.current(applicationContext))
                    }
                }

                override fun onFail(errorCode: Int?) {
                    Log.d(TAG, "vivo push getRegId failed: code=$errorCode")
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "vivo push initialize failed: ${e.message}")
        }
    }
}
