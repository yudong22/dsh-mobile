package com.clarklevis.dsh.android

import android.content.Context
import android.util.Log
import com.clarklevis.dsh.shared.gateway.GatewayPushRegistration
import com.google.firebase.messaging.FirebaseMessaging
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

    /**
     * 向 Firebase 主动取一次当前投递地址，成功后落盘并回调。
     *
     * 为什么必须主动取：`onNewToken` **只在 token 首次签发或轮换时**回调。而「装上 App
     * 时 token 早就存在」这条路径不会触发它——重装、清应用数据、换机恢复都会落到这里。
     * 此前只等回调，于是这些情况下 `dsh.push` 永远不生成、设备从不上报、也就永远收不到
     * 离线推送（网关面板表现为「平台未知」）。
     *
     * Firebase 未初始化时（没有 `google-services.json`，PR CI 与部分自建构建即如此）
     * `getInstance()` 会抛 `IllegalStateException`；那种构建本就不投递推送，这里安静返回
     * 而不是让连接流程崩掉。
     *
     * @param onToken 已落盘的新地址；失败或不可用时不被调用。
     */
    fun fetchAndSave(context: Context, onToken: (GatewayPushRegistration) -> Unit) {
        val messaging = runCatching { FirebaseMessaging.getInstance() }
            .onFailure { Log.w(TAG, "Firebase not initialised; push unavailable: ${it.message}") }
            .getOrNull() ?: return
        messaging.token
            .addOnSuccessListener { token ->
                val registration = pushRegistrationForStoredToken(token)
                if (registration == null) {
                    // 空白 token 不落盘：上报它只会被网关拒绝。
                    Log.w(TAG, "Firebase returned a blank token; not registering")
                    return@addOnSuccessListener
                }
                Log.i(TAG, "fetched FCM token (length=${token.length})")
                save(context, token)
                onToken(registration)
            }
            .addOnFailureListener(::logFailure)
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