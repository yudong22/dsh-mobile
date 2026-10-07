package com.clarklevis.dsh.android.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 后台通知偏好。开关默认开启：App 退到后台后，Agent 提问、需要审批或执行结束时
 * 通过 Android 通知提醒用户。通知内容本身仍只在 App 处于后台时弹出
 * （[AndroidAgentNotificationManager] 会忽略前台状态）。
 */
internal class AgentNotificationSettings(private val preferences: SharedPreferences) {
    var notifyInBackground by mutableStateOf(
        preferences.getBoolean(KEY_NOTIFY_IN_BACKGROUND, true)
    )
        private set

    fun updateNotifyInBackground(value: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFY_IN_BACKGROUND, value).apply()
        notifyInBackground = value
    }

    companion object {
        const val PREFERENCES_NAME = "agent_notifications"
        internal const val KEY_NOTIFY_IN_BACKGROUND = "notify_in_background"

        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
}

internal val LocalAgentNotificationSettings = staticCompositionLocalOf<AgentNotificationSettings> {
    error("AgentNotificationSettings must be provided by DshTheme")
}

@Composable
internal fun rememberAgentNotificationSettings(): AgentNotificationSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AgentNotificationSettings(AgentNotificationSettings.preferences(context))
    }
}
