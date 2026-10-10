package com.clarklevis.dsh.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clarklevis.dsh.android.ui.AppearanceSettings

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppearanceSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动窗口已由 Starting 主题绘制，Activity 创建后切回常规主题。
        setTheme(R.style.Theme_DeepSeekHarness)
        super.onCreate(savedInstanceState)
        consumeNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            DeepSeekHarnessAndroidApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        (application as DshAndroidApplication).openNotificationSession(intent)
        // 两组键都清掉，否则配置变更重建 Activity 时会再次触发同一次跳转。
        intent?.removeExtra(EXTRA_NOTIFICATION_GATEWAY_ID)
        intent?.removeExtra(EXTRA_NOTIFICATION_SESSION_ID)
        intent?.removeExtra(FCM_DATA_GATEWAY_ID)
        intent?.removeExtra(FCM_DATA_SESSION_ID)
    }
}
