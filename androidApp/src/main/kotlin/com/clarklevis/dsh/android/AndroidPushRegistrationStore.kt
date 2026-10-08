package com.clarklevis.dsh.android

import android.content.Context
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushRegistration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 持有待上报的 FCM 投递地址，并把注册/注销请求交给已就绪的网关通道。
 *
 * 与 iOS 的 `AgentPushRegistrationManager` 对称：token 由系统异步签发，而网关通道
 * 可能尚未建立，因此 token 先落盘，等 `hello` 之后由通道消费。
 */
object AndroidPushRegistrationStore {
    private const val TAG = "PushRegistration"
    private const val PREFS = "dsh.push"
    private const val KEY_TOKEN = "fcm.token"

    private var pending: GatewayPushRegistration? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
        pending = fcmRegistration(token)
    }

    /** 取走并清空待上报注册；通道未就绪时返回 null 等待下一次触发。 */
    fun consume(): GatewayPushRegistration? = pending.also { pending = null }

    /** 通道就绪后从落盘值重新排队，用于进程重启后的补报。 */
    fun restoreFromDisk(context: Context) {
        val token = prefs(context).getString(KEY_TOKEN, null) ?: return
        if (token.isBlank()) return
        pending = fcmRegistration(token)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
        pending = null
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