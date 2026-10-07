package com.clarklevis.dsh.android.platform

import android.content.Context
import com.clarklevis.dsh.shared.platform.GatewayConversationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 会话正文缓存的 Android 实现。
 *
 * 放在 `cacheDir`（系统可回收），与 [AndroidAttachmentCache] 同样的原子写 + 限额淘汰形态。
 *
 * 设计取舍（见 Docs/next-version-plan.md §3）：
 * - **不用短 TTL**：一周后打开看到空会话，比看到略旧的内容更糟。失效以「体积 + 条数」为主，
 *   正确性失效交给显式的会话删除 / 宿主身份变更 / schema 变更；
 * - 单会话与总量双限额，防止长会话或大量会话把缓存撑爆；
 * - 半截写入（`.tmp` 残留）一律视为无效，避免把损坏 JSON 当作有效缓存。
 */
class AndroidConversationCache(
    context: Context,
    gatewayId: String? = null,
    private val maximumEntryBytes: Long = MAXIMUM_ENTRY_BYTES,
    private val maximumTotalBytes: Long = MAXIMUM_TOTAL_BYTES
) : GatewayConversationCache {
    private val directory = context.cacheDir
        .resolve(gatewayId?.let { "gateway-conversation/$it" } ?: "gateway-conversation")

    init {
        directory.mkdirs()
    }

    override suspend fun read(sessionId: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(sessionId)
        if (!file.isFile) return@withContext null
        if (file.length() > maximumEntryBytes) {
            file.delete()
            return@withContext null
        }
        runCatching { file.readText() }.getOrNull()?.takeIf(String::isNotBlank)
    }

    override suspend fun write(sessionId: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = payload.toByteArray()
        if (bytes.size > maximumEntryBytes) return@withContext false
        directory.mkdirs()
        val target = fileFor(sessionId)
        val temporary = File(directory, "${target.name}.tmp")
        val committed = runCatching {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                target.writeBytes(bytes)
                temporary.delete()
            }
            target.isFile && target.length() == bytes.size.toLong()
        }.getOrDefault(false)
        temporary.delete()
        if (!committed) {
            target.delete()
            return@withContext false
        }
        prune()
        target.isFile
    }

    override suspend fun remove(sessionId: String) = withContext(Dispatchers.IO) {
        fileFor(sessionId).delete()
        Unit
    }

    override suspend fun removeExpired() = withContext(Dispatchers.IO) {
        prune()
    }

    /** 按最近修改时间淘汰，直到满足总量限额；`.tmp` 残留直接清除。 */
    private fun prune() {
        val files = directory.listFiles().orEmpty()
        files.filter { it.name.endsWith(".tmp") }.forEach(File::delete)
        val valid = files.filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy(File::lastModified)
        var total = valid.sumOf(File::length)
        valid.forEach { file ->
            if (total > maximumTotalBytes) {
                total -= file.length()
                file.delete()
            }
        }
    }

    private fun fileFor(sessionId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray())
        return directory.resolve(digest.joinToString("") { "%02x".format(it) })
    }

    companion object {
        /** 单会话上限 2 MiB（典型会话 0.25–1 MiB）。 */
        const val MAXIMUM_ENTRY_BYTES: Long = 2L * 1_024 * 1_024

        /** 总量上限 64 MiB，超出按最旧淘汰。 */
        const val MAXIMUM_TOTAL_BYTES: Long = 64L * 1_024 * 1_024
    }
}
