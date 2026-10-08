package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.gateway.GatewayIdentity
import com.clarklevis.dsh.shared.gateway.GatewayProfile
import com.clarklevis.dsh.shared.protocol.GatewayPairingPayload
import java.util.UUID

/**
 * 扫码配对时的「这是哪台机器」判定与地址合并。
 *
 * 抽成独立对象是为了可单测：这两条规则各自都曾产生用户可见故障，
 * 且都不容易靠手工回归覆盖（要真的用两种二维码扫同一台机器）。
 *
 * 两个历史缺陷：
 * 1. **地址丢失**：命中已有 profile 时用 `endpoints = endpoints` 整体**覆盖**，
 *    于是「先扫远程优先二维码、再扫本地优先二维码」会把远程地址丢掉，
 *    表现为「远程优先的服务器不在了」。
 * 2. **身份漂移**：网关不带 `gatewayId` 时，靠 `endpoints.first() in it.endpoints`
 *    判身份。两种二维码的候选顺序不同 → `first()` 不同 → 匹配失败 → **新建 profile**。
 *    新 `localId` 会让挂在其下的会话缓存（`host_$localId`）全部对不上，
 *    用户看到「切回去是空的」。
 *
 * 同时约束：`GatewayIdentity.endpoints` 已保证 ≤ 16 个地址，
 * 合并必须保持该上限，否则 identity 校验会在下次启动时拒绝整份资料（fail-closed）。
 */
internal object PairingIdentityResolver {

    /** 与 `AndroidMultiGatewayStore.MAX_ENDPOINTS` 一致；合并后不得超出。 */
    const val MAX_ENDPOINTS = 16

    /**
     * 找出这次扫码对应哪个已有 profile。
     *
     * 顺序：
     * 1. `gatewayId` 精确匹配（权威身份）；
     * 2. 若无精确命中，用**地址交集**去匹配「还没有 gatewayId」的旧记录——
     *    覆盖「网关升级后才开始带 gatewayId」这一真实路径。此时不能新建：
     *    新 `localId` 会让该机器的会话缓存全部失联；
     * 3. 仍无命中才认为是一台新机器。
     *
     * 第 2 步**只**匹配 `gatewayId == null` 的记录：已有其它非空 gatewayId 的记录
     * 属于别的机器，绝不能因为地址巧合而被合并（那会把两台机器混成一台）。
     *
     * 兜底刻意用**整个 endpoint 集合求交**而不是只看 `first()`：
     * 只要新旧二维码共享任意一个地址，就认为是同一台机器——这正是历史判错处。
     */
    fun findExisting(
        profiles: List<GatewayProfile>,
        payload: GatewayPairingPayload,
        endpoints: List<String>
    ): GatewayProfile? {
        val gatewayId = payload.gatewayId
        if (gatewayId != null) {
            profiles.firstOrNull { gatewayId.equals(it.gatewayId, ignoreCase = true) }
                ?.let { return it }
        }
        val incoming = endpoints.toSet()
        return profiles.firstOrNull { profile ->
            profile.gatewayId == null && profile.endpoints.any { it in incoming }
        }
    }

    /**
     * 生成/更新 profile：命中已有则**合并**地址，未命中才新建。
     *
     * 合并规则：
     * - 本次可用地址置顶（`preferredEndpoint` 指向它），因为那是用户刚验证过的路径；
     * - 保留旧 profile 里仍在用的其它地址（远程/本地互备的关键）；
     * - 去重并截断到 [MAX_ENDPOINTS]，超出时优先丢弃**末尾**（最旧的）地址。
     */
    fun resolve(
        profiles: List<GatewayProfile>,
        payload: GatewayPairingPayload,
        endpoints: List<String>
    ): GatewayProfile {
        val existing = findExisting(profiles, payload, endpoints)
        if (existing == null) {
            return GatewayProfile(
                localId = UUID.randomUUID().toString(),
                gatewayId = payload.gatewayId?.lowercase(),
                gatewayName = payload.gatewayName ?: GatewayIdentity.host(endpoints.first()),
                endpoints = endpoints.distinct().take(MAX_ENDPOINTS),
                preferredEndpoint = endpoints.first(),
                server = !GatewayIdentity.isLocal(GatewayIdentity.host(endpoints.first()))
            )
        }
        val merged = (endpoints + existing.endpoints).distinct().take(MAX_ENDPOINTS)
        val preferred = endpoints.first().takeIf { it in merged } ?: merged.first()
        return existing.copy(
            // gatewayName/gatewayId 可能这次才拿到（老网关首次不带、升级后带上）。
            gatewayId = payload.gatewayId?.lowercase() ?: existing.gatewayId,
            gatewayName = payload.gatewayName ?: existing.gatewayName,
            endpoints = merged,
            preferredEndpoint = preferred
        )
    }

    /**
     * 合并同一台机器的重复 profile（历史数据修复）。
     *
     * 早期版本会为同一台机器建出多条 profile，每条各有一个 `localId`，
     * 而缓存挂在 `localId` 下。修复时**必须保留最早那条的 localId**，
     * 否则用户的会话缓存会指向一个已被删除的 id，表现为「升级后什么都没了」。
     *
     * 判定为同一台机器的条件（任一成立）：
     * - `gatewayId` 相同且非空；
     * - 都无 `gatewayId`，但地址集合有交集。
     *
     * 返回去重后的列表；顺序保持「首次出现」的次序，保证幂等。
     */
    fun deduplicate(profiles: List<GatewayProfile>): List<GatewayProfile> {
        val result = mutableListOf<GatewayProfile>()
        for (profile in profiles) {
            val duplicateIndex = result.indexOfFirst { candidate -> sameMachine(candidate, profile) }
            if (duplicateIndex < 0) {
                result += profile
            } else {
                // 保留**先出现**的那条的 localId 与名称，只并地址。
                val kept = result[duplicateIndex]
                val mergedEndpoints = (kept.endpoints + profile.endpoints).distinct().take(MAX_ENDPOINTS)
                result[duplicateIndex] = kept.copy(
                    gatewayId = kept.gatewayId ?: profile.gatewayId,
                    endpoints = mergedEndpoints,
                    preferredEndpoint = (kept.preferredEndpoint ?: profile.preferredEndpoint)
                        ?.takeIf { it in mergedEndpoints }
                        ?: mergedEndpoints.first(),
                    lastConnectedAt = maxOf(
                        kept.lastConnectedAt ?: 0L,
                        profile.lastConnectedAt ?: 0L
                    ).takeIf { it > 0L }
                )
            }
        }
        return result
    }

    /** 两个 profile 是否指向同一台机器。 */
    private fun sameMachine(a: GatewayProfile, b: GatewayProfile): Boolean {
        if (a.gatewayId != null && b.gatewayId != null) {
            return a.gatewayId.equals(b.gatewayId, ignoreCase = true)
        }
        if (a.gatewayId != null || b.gatewayId != null) return false
        return a.endpoints.any { it in b.endpoints.toSet() }
    }
}
