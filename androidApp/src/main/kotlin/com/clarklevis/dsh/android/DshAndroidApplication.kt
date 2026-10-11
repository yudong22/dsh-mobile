package com.clarklevis.dsh.android

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.clarklevis.dsh.android.platform.GatewayLifecycleEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DshAndroidApplication : Application(), DefaultLifecycleObserver {
    lateinit var hosts: AndroidMultiGatewayStore
        private set
    val graph: AndroidAppGraph get() = hosts.activeGraph
    @Volatile
    internal var isInForeground = false
        private set
    internal var pendingNotificationSession by mutableStateOf<AndroidNotificationSessionRoute?>(null)
        private set

    internal fun openNotificationSession(intent: Intent?) {
        // 两组键都要认：本地通知用命名空间键，FCM 推送用裸键（见 NotificationRouting）。
        val route = notificationRouteFromExtras(
            namespacedGatewayId = intent?.getStringExtra(EXTRA_NOTIFICATION_GATEWAY_ID),
            namespacedSessionId = intent?.getStringExtra(EXTRA_NOTIFICATION_SESSION_ID),
            fcmGatewayId = intent?.getStringExtra(FCM_DATA_GATEWAY_ID),
            fcmSessionId = intent?.getStringExtra(FCM_DATA_SESSION_ID),
        )
        if (route != null) pendingNotificationSession = route
    }

    internal fun clearNotificationSession(route: AndroidNotificationSessionRoute) {
        if (pendingNotificationSession == route) pendingNotificationSession = null
    }

    /**
     * 供非 Activity 组件（如 Mi Push 接收器）在进程外（或 App 未在前台时）触发会话跳转。
     *
     * 接收器拿不到本 Application 实例，这里从 [Context.getApplicationContext] 取回，
     * 再走与 FCM 点击完全相同的 [openNotificationSession] 分支。
     */
    internal companion object {
        internal fun openNotificationSession(context: Context, intent: Intent?) {
            (context.applicationContext as? DshAndroidApplication)
                ?.openNotificationSession(intent)
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        hosts = AndroidMultiGatewayStore(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // 注册 Mi Push（小米/红米系统级推送）。凭证缺失时该方法内部静默跳过。
        MiPushInitializer.register(this)
        // 注册 vivo 推送（vivo/iQOO 系统级推送）。凭证缺失或非 vivo 硬件时静默跳过。
        VivoPushInitializer.register(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        graph.diagnostics.lifecycle(GatewayLifecycleEvent.FOREGROUND)
        graph.gatewayRuntime.applicationDidBecomeActive()
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
        hosts.stopPresence()
        hosts.cancelPairing()
        graph.diagnostics.lifecycle(GatewayLifecycleEvent.BACKGROUND)
        graph.gatewayScope.launch { graph.gatewayRuntime.applicationDidEnterBackground() }
    }
}
