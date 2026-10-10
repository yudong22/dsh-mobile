package com.clarklevis.dsh.android

import android.app.Application
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

    override fun onCreate() {
        super<Application>.onCreate()
        hosts = AndroidMultiGatewayStore(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
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
