package com.clarklevis.dsh.android

import com.clarklevis.dsh.shared.gateway.GatewayProfile
import com.clarklevis.dsh.shared.protocol.GatewayPairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扫码配对的「这是哪台机器」判定与地址合并（v1.8.3 遗留项，v1.9.0 落实）。
 *
 * 两个缺陷此前都没有任何测试覆盖，且都只能在「用第二种二维码再扫一次」时才暴露，
 * 因此这里把两种二维码场景直接写成用例。
 */
class PairingIdentityResolverTest {

    private val remoteFirst = "wss://gateway.example.com/ws/mobile"
    private val localFirst = "ws://192.168.1.20:3080/ws/mobile"

    private fun payload(
        gatewayId: String? = null,
        name: String? = "denisMacBook",
        publicUrl: String = remoteFirst
    ) = GatewayPairingPayload(
        version = 2,
        publicUrl = publicUrl,
        pairingCode = "code",
        expiresAt = 0.0,
        gatewayId = gatewayId,
        gatewayName = name
    )

    private fun profile(
        localId: String,
        gatewayId: String? = null,
        endpoints: List<String>,
        name: String = "denisMacBook"
    ) = GatewayProfile(
        localId = localId,
        gatewayId = gatewayId,
        gatewayName = name,
        endpoints = endpoints,
        preferredEndpoint = endpoints.firstOrNull()
    )

    // ---- 缺陷 B：身份漂移（不得为同一台机器新建 localId）----

    /**
     * 同一台**不带 gatewayId** 的机器，两种二维码（地址顺序不同）必须命中同一 profile。
     *
     * 旧实现只看 `endpoints.first()`，两种二维码的 `first()` 不同 → 判为「新设备」→
     * 新建 localId → 挂在该 id 下的会话缓存全部对不上。
     */
    @Test
    fun sameGatewayWithoutIdIsMatchedEvenWhenEndpointOrderDiffers() {
        val existing = listOf(profile("keep-me", endpoints = listOf(remoteFirst, localFirst)))

        // 这次扫的是「本地优先」的二维码：first() 与已存的不同，但地址集合有交集。
        val resolved = PairingIdentityResolver.resolve(
            existing,
            payload(publicUrl = localFirst),
            listOf(localFirst, remoteFirst)
        )

        assertEquals("必须复用已有 localId，否则缓存会失联", "keep-me", resolved.localId)
    }

    /** 地址完全不同的机器仍然要新建 profile（不能把两台机器并成一台）。 */
    @Test
    fun differentGatewayWithoutIdStillCreatesANewProfile() {
        val existing = listOf(profile("machine-a", endpoints = listOf(localFirst)))

        val resolved = PairingIdentityResolver.resolve(
            existing,
            payload(publicUrl = "ws://192.168.1.99:3080/ws/mobile"),
            listOf("ws://192.168.1.99:3080/ws/mobile")
        )

        assertNotEquals("machine-a", resolved.localId)
    }

    /** 带 gatewayId 时以它为准，地址无关。 */
    @Test
    fun gatewayIdIsAuthoritativeEvenWithDifferentEndpoints() {
        val id = "d56a1098-8519-43a1-9dce-fb99863bf5bb"
        val existing = listOf(profile("mac", gatewayId = id, endpoints = listOf(remoteFirst)))

        val resolved = PairingIdentityResolver.resolve(
            existing,
            payload(gatewayId = id, publicUrl = "ws://10.0.0.5:3080/ws/mobile"),
            listOf("ws://10.0.0.5:3080/ws/mobile")
        )

        assertEquals("mac", resolved.localId)
    }

    /** 老网关首次不带 gatewayId、升级后带上：同一 profile 要补上身份而不是新建。 */
    @Test
    fun gatewayIdIsBackfilledOntoExistingProfile() {
        val existing = listOf(profile("mac", gatewayId = null, endpoints = listOf(remoteFirst)))
        val id = "a56a1098-8519-43a1-9dce-fb99863bf5bb"

        val resolved = PairingIdentityResolver.resolve(
            existing,
            payload(gatewayId = id, publicUrl = remoteFirst),
            listOf(remoteFirst)
        )

        assertEquals("mac", resolved.localId)
        assertEquals(id, resolved.gatewayId)
    }

    // ---- 缺陷 A：地址丢失（必须合并而不是覆盖）----

    /**
     * 先扫远程优先、再扫本地优先：两边地址都必须保留。
     *
     * 旧实现是 `endpoints = endpoints` 覆盖，第二次扫码会把远程地址删掉，
     * 用户观察到「远程优先的服务器不在了」。
     *
     * 注意二维码本身就带候选地址列表：`publicUrl` + `endpoints`，所以两次扫码
     * 天然有交集（这正是能判定「同一台机器」的依据）。
     */
    @Test
    fun endpointsAreMergedNotReplaced() {
        // 已存：第一次扫「远程优先」，只拿到了远程地址。
        val existing = listOf(profile("mac", endpoints = listOf(remoteFirst)))

        // 第二次扫「本地优先」的二维码：publicUrl=local，候选里还有远程。
        val resolved = PairingIdentityResolver.resolve(
            existing,
            payload(publicUrl = localFirst),
            listOf(localFirst, remoteFirst)
        )

        assertTrue("远程地址必须保留", remoteFirst in resolved.endpoints)
        assertTrue("本次地址必须保留", localFirst in resolved.endpoints)
        assertEquals("preferred 应指向本次刚验证过的地址", localFirst, resolved.preferredEndpoint)
    }

    /**
     * 合并结果去重且不超过 16。
     *
     * 超出会让下次启动的 identity 校验 fail-closed，整份主机资料被拒绝
     * （表现为「所有设备都消失了」），所以这是硬约束。
     */
    @Test
    fun mergeDeduplicatesAndRespectsTheEndpointLimit() {
        val many = (1..20).map { "ws://192.168.1.$it:3080/ws/mobile" }
        val existing = listOf(profile("mac", endpoints = many.take(16)))
        // 新二维码带远程地址 + 若干个与已存重叠的地址（重叠才会判为同一台机器）。
        val incoming = listOf(remoteFirst) + many.take(5)

        val resolved = PairingIdentityResolver.resolve(
            existing, payload(publicUrl = remoteFirst), incoming
        )

        assertEquals(16, resolved.endpoints.size)
        assertEquals(resolved.endpoints.size, resolved.endpoints.distinct().size)
        assertTrue("新地址应优先保留", remoteFirst in resolved.endpoints)
        assertTrue("preferred 必须仍属于 endpoints", resolved.preferredEndpoint in resolved.endpoints)
    }

    // ---- 历史数据修复：幂等去重 ----

    /**
     * 同一台机器（带 gatewayId）的多条记录合并成一条，且**保留最早那条的 localId**。
     *
     * 保留最早 localId 是关键：缓存挂在它下面，换成后来那条会让用户「升级后数据没了」。
     */
    @Test
    fun duplicatesAreMergedKeepingTheOldestLocalId() {
        val id = "d56a1098-8519-43a1-9dce-fb99863bf5bb"
        val duplicated = listOf(
            profile("oldest", gatewayId = id, endpoints = listOf(remoteFirst)),
            profile("newer", gatewayId = id, endpoints = listOf(localFirst))
        )

        val result = PairingIdentityResolver.deduplicate(duplicated)

        assertEquals(1, result.size)
        assertEquals("必须保留最早的 localId（缓存挂在它下面）", "oldest", result.single().localId)
        assertTrue(remoteFirst in result.single().endpoints)
        assertTrue(localFirst in result.single().endpoints)
    }

    /** 无 gatewayId 时按地址交集判重。 */
    @Test
    fun duplicatesWithoutGatewayIdAreMergedByEndpointOverlap() {
        val duplicated = listOf(
            profile("oldest", endpoints = listOf(remoteFirst, localFirst)),
            profile("newer", endpoints = listOf(localFirst))
        )

        val result = PairingIdentityResolver.deduplicate(duplicated)

        assertEquals(1, result.size)
        assertEquals("oldest", result.single().localId)
    }

    /** 不同机器不得被合并。 */
    @Test
    fun distinctMachinesAreNotMerged() {
        val list = listOf(
            profile("a", endpoints = listOf(localFirst)),
            profile("b", endpoints = listOf("ws://192.168.1.77:3080/ws/mobile"))
        )

        assertEquals(2, PairingIdentityResolver.deduplicate(list).size)
    }

    /** 迁移必须**幂等**：再跑一次结果不变（否则每次启动都会改写磁盘）。 */
    @Test
    fun deduplicateIsIdempotent() {
        val id = "d56a1098-8519-43a1-9dce-fb99863bf5bb"
        val once = PairingIdentityResolver.deduplicate(
            listOf(
                profile("a", gatewayId = id, endpoints = listOf(remoteFirst)),
                profile("b", gatewayId = id, endpoints = listOf(localFirst)),
                profile("c", endpoints = listOf("ws://192.168.9.9:3080/ws/mobile"))
            )
        )

        assertEquals(once, PairingIdentityResolver.deduplicate(once))
    }

    /** 无重复时原样返回，且不改变顺序。 */
    @Test
    fun alreadyCleanProfilesAreReturnedUnchanged() {
        val clean = listOf(
            profile("a", gatewayId = "d56a1098-8519-43a1-9dce-fb99863bf5bb", endpoints = listOf(remoteFirst)),
            profile("b", gatewayId = "a56a1098-8519-43a1-9dce-fb99863bf5bb", endpoints = listOf(localFirst))
        )

        assertEquals(clean, PairingIdentityResolver.deduplicate(clean))
    }

    /** 全新的机器（没有任何已存 profile）必须新建。 */
    @Test
    fun firstEverPairingCreatesAProfile() {
        val resolved = PairingIdentityResolver.resolve(emptyList(), payload(), listOf(remoteFirst))

        assertTrue(resolved.localId.isNotBlank())
        assertEquals(listOf(remoteFirst), resolved.endpoints)
        assertEquals(remoteFirst, resolved.preferredEndpoint)
    }

    /** 公网地址应标记为 server；本地地址不是。 */
    @Test
    fun serverFlagFollowsThePreferredEndpointHost() {
        val remote = PairingIdentityResolver.resolve(emptyList(), payload(publicUrl = remoteFirst), listOf(remoteFirst))
        assertTrue("公网地址应为 server", remote.server)

        val local = PairingIdentityResolver.resolve(emptyList(), payload(publicUrl = localFirst), listOf(localFirst))
        assertTrue("本地地址不应标记为 server", !local.server)
    }
}
