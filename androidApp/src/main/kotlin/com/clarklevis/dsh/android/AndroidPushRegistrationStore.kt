package com.clarklevis.dsh.android

import android.content.Context
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushRegistration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 持有本机 FCM 投递地址，供网关通道就绪时上报。
 *
 * token 由系统异步签发，而网关通道可能尚未建立，因此落盘保存、连接建立后重发。
 * 与 iOS 的 `AgentPushRegistrationManager` 对称。
 */
object AndroidPushRegistrationStore {
    private const val TAG = "PushRegistration"
    private const val PREFS = "dsh.push"
    private const val KEY_TOKEN = "fcm.token"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    /**
     * 读本机当前的投递地址；**不消费**，可重复读取。
     *
     * 落盘值是唯一真相来源。此前用内存 `pending` + `consume()` 保存待上报值，一旦
     * 通道未就绪就把它清空，而 `onNewToken` 之外没有任何地方再取用它，于是该 token
     * 永远不会上报——这是安卓端注册始终为空的原因。改为幂等读取后，注册失败与断线
     * 重连都能自然重发；服务端 `registerPush` 按平台覆盖，重复上报是幂等的。
     */
    fun current(context: Context): GatewayPushRegistration? =
        pushRegistrationForStoredToken(prefs(context).getString(KEY_TOKEN, null))

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    internal fun recordDelivered(context: Context, dedupeKey: String?) {
        if (dedupeKey.isNullOrBlank()) return
        // 记录到与本地通知相同的去重环，避免同一事件弹两次。
        DedupeRegistry.remember("agent.remote.$dedupeKey")
    }

    internal fun logFailure(throwable: Throwable) {
        Log.w(TAG, "push registration failed: ${throwable.message}")
    }
}

/**
 * 落盘 token → 待上报注册。
 *
 * 纯函数，便于在没有 Android 运行时的情况下测试「空白 token 不上报」这一约定。
 * 空白 token 必须返回 null：上报空地址会让网关侧 `normalizePushRegistration` 拒绝，
 * 或在更坏的情况下占用该平台的单槽位。
 */
internal fun pushRegistrationForStoredToken(token: String?): GatewayPushRegistration? =
    token?.takeIf { it.isNotBlank() }?.let { fcmRegistration(it) }

/** 通道与注册之间的单向通道：注册方订阅，已就绪的网关运行时收集。 */
object AndroidPushRegistrationBus {
    private val _registrations = MutableSharedFlow<GatewayPushRegistration>(extraBufferCapacity = 8)
    val registrations: SharedFlow<GatewayPushRegistration> = _registrations.asSharedFlow()

    suspend fun submit(registration: GatewayPushRegistration) {
        _registrations.emit(registration)
    }
}

/**
 * 远程与本地通知共用的去重标识环。
 *
 * 本地路径此前把去重状态放在通知管理器内部；远程到达时需要能查询同一个环，
 * 因此这里只做进程内的判重集合，持久化仍由本地通知管理器负责。
 */
internal object DedupeRegistry {
    private val seen = mutableSetOf<String>()

    fun remember(identifier: String) {
        if (seen.add(identifier) && seen.size > 256) {
            val iterator = seen.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    fun contains(identifier: String): Boolean = identifier in seen
}