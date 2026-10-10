package com.clarklevis.dsh.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * 系统是否允许本 App 发通知——通知的唯一真相。
 *
 * 这里刻意**不**再做一份 App 自己的「后台时通知我」偏好。FCM/APNs 的 `notification`
 * 载荷由系统直接展示，App 拦截不到，所以本地开关只能控制「App 自己发的那条本地通知」，
 * 控制不了推送；两份状态并存还会互相打架：用户在系统弹窗里点了「允许」，App 内仍按
 * 落盘的旧值显示关闭，看起来像坏了。改成只读镜像系统状态后只剩一个真相，用户要改就
 * 去系统设置（见 [openAppNotificationSettings]）。
 */
internal enum class NotificationSystemState {
    /** 系统允许本 App 发通知。 */
    ALLOWED,

    /** Android 13+ 的 `POST_NOTIFICATIONS` 未授予。 */
    PERMISSION_NOT_GRANTED,

    /** 权限在，但用户在系统设置里关掉了本 App 的通知。 */
    DISABLED_BY_USER;

    /** 能否真正弹出一条通知。上报给网关的 `notifyInBackground` 也由它派生。 */
    val canNotify: Boolean get() = this == ALLOWED
}

/**
 * 由两项系统事实推导状态。
 *
 * `permissionGranted` 优先于 `notificationsEnabled`：未授权时 `areNotificationsEnabled()`
 * 在部分 ROM 上仍返回 true，此时若按后者判断会误报「已允许」，而通知实际弹不出来。
 */
internal fun notificationSystemState(
    notificationsEnabled: Boolean,
    permissionGranted: Boolean,
): NotificationSystemState = when {
    !permissionGranted -> NotificationSystemState.PERMISSION_NOT_GRANTED
    !notificationsEnabled -> NotificationSystemState.DISABLED_BY_USER
    else -> NotificationSystemState.ALLOWED
}

/** 读取当前系统状态。只读，不缓存。 */
internal fun readNotificationSystemState(context: Context): NotificationSystemState {
    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return notificationSystemState(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        permissionGranted = permissionGranted,
    )
}

/**
 * 系统状态，且**每次回到前台重读**。
 *
 * 用户可能刚在系统设置里改过通知开关，而 Compose 不会因此重组；不重读的话设置页会一直
 * 显示离开时的旧值。
 */
@Composable
internal fun rememberNotificationSystemState(): NotificationSystemState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readNotificationSystemState(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = readNotificationSystemState(context)
    }
    return state
}

/**
 * 跳到本 App 的系统通知设置页。
 *
 * 用 `ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`：直接落在本 App 的通知页，
 * 而不是让用户自己在系统设置里翻。部分 ROM 不认这个 Action，此时退化为应用详情页。
 */
internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}
