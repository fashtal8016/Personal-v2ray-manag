package com.example

import com.example.data.parser.FingerprintGenerator
import com.example.data.parser.V2RayUriParser
import com.example.data.testing.RankingEngine
import com.example.model.ProxyProtocol
import com.example.model.SecurityType
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.TestResultEntity
import com.example.model.TransportProtocol
import com.example.util.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V2RayUnitTest {

    @Test
    fun testVlessUriParsing() {
        val uri = "vless://25b583f7-92ea-4a4b-9147-380d322d713c@hk.example.com:443?type=ws&security=tls&sni=hk.example.com&path=%2Fws-path#HongKong-Fast"
        val profile = V2RayUriParser.parseUri(uri)

        assertNotNull(profile)
        assertEquals(ProxyProtocol.VLESS, profile?.protocol)
        assertEquals("hk.example.com", profile?.server)
        assertEquals(443, profile?.port)
        assertEquals("25b583f7-92ea-4a4b-9147-380d322d713c", profile?.uuid)
        assertEquals(TransportProtocol.WS, profile?.transport)
        assertEquals(SecurityType.TLS, profile?.security)
        assertEquals("hk.example.com", profile?.sni)
        assertEquals("/ws-path", profile?.path)
        assertEquals("HongKong-Fast", profile?.name)
    }

    @Test
    fun testVlessRealityParsing() {
        val uri = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@us.example.com:8443?type=tcp&security=reality&sni=yahoo.com&fp=chrome&pbk=AbCdEf123456&sid=1234&flow=xtls-rprx-vision#US-Reality"
        val profile = V2RayUriParser.parseUri(uri)

        assertNotNull(profile)
        assertEquals(ProxyProtocol.VLESS, profile?.protocol)
        assertEquals("us.example.com", profile?.server)
        assertEquals(8443, profile?.port)
        assertEquals(SecurityType.REALITY, profile?.security)
        assertEquals("yahoo.com", profile?.sni)
        assertEquals("AbCdEf123456", profile?.publicKey)
        assertEquals("1234", profile?.shortId)
        assertEquals("xtls-rprx-vision", profile?.flow)
        assertEquals("US-Reality", profile?.name)
    }

    @Test
    fun testTrojanUriParsing() {
        val uri = "trojan://secret-password@jp.example.com:443?type=tcp&security=tls&sni=jp.example.com#Tokyo-01"
        val profile = V2RayUriParser.parseUri(uri)

        assertNotNull(profile)
        assertEquals(ProxyProtocol.TROJAN, profile?.protocol)
        assertEquals("jp.example.com", profile?.server)
        assertEquals(443, profile?.port)
        assertEquals("secret-password", profile?.uuid)
        assertEquals(SecurityType.TLS, profile?.security)
        assertEquals("Tokyo-01", profile?.name)
    }

    @Test
    fun testFingerprintDeduplication() {
        val fp1 = FingerprintGenerator.generate(
            protocol = ProxyProtocol.VLESS,
            server = "sg.example.com",
            port = 443,
            uuid = "some-uuid",
            transport = TransportProtocol.WS,
            security = SecurityType.TLS,
            sni = "sg.example.com",
            path = "/vless",
            flow = ""
        )

        val fp2 = FingerprintGenerator.generate(
            protocol = ProxyProtocol.VLESS,
            server = "SG.EXAMPLE.COM", // case insensitive host
            port = 443,
            uuid = "some-uuid",
            transport = TransportProtocol.WS,
            security = SecurityType.TLS,
            sni = "sg.example.com",
            path = "/vless",
            flow = ""
        )

        assertEquals("Identical endpoints should produce identical fingerprints", fp1, fp2)
    }

    @Test
    fun testRankingSelectionTop50() {
        // Create 80 candidate nodes with varying latencies and reliabilities
        val candidates = mutableListOf<Pair<ServerProfile, ServerStatsEntity>>()
        for (i in 1..80) {
            val p = ServerProfile(
                id = "node_$i",
                name = "Node $i",
                protocol = ProxyProtocol.VLESS,
                server = "node$i.example.com",
                port = 443,
                uuid = UUID.randomUUID().toString()
            )
            val stats = ServerStatsEntity(
                serverId = "node_$i",
                totalTests = 5,
                successfulTests = if (i <= 70) 5 else 0, // 10 nodes failed
                reliability = if (i <= 70) 1.0f else 0.0f,
                avgLatencyMs = (100 + i * 10).toLong(),
                avgDownloadSpeedBps = 100_000L,
                lastStatusSuccess = (i <= 70)
            )
            candidates.add(p to stats)
        }

        val ranked = RankingEngine.rankServers(candidates, maxCount = 50)

        assertEquals("Must select up to 50 nodes", 50, ranked.size)
        // Verify first node has lower latency / higher score than last node
        assertTrue(ranked.first().second.compositeScore >= ranked.last().second.compositeScore)
    }

    @Test
    fun testRankingFewerThan50Nodes() {
        // Only 17 nodes qualify
        val candidates = mutableListOf<Pair<ServerProfile, ServerStatsEntity>>()
        for (i in 1..17) {
            val p = ServerProfile(
                id = "node_$i",
                name = "Node $i",
                protocol = ProxyProtocol.VMESS,
                server = "node$i.example.com",
                port = 443,
                uuid = UUID.randomUUID().toString()
            )
            val stats = ServerStatsEntity(
                serverId = "node_$i",
                totalTests = 3,
                successfulTests = 3,
                reliability = 1.0f,
                avgLatencyMs = (150 + i * 5).toLong(),
                avgDownloadSpeedBps = 50_000L,
                lastStatusSuccess = true
            )
            candidates.add(p to stats)
        }

        val ranked = RankingEngine.rankServers(candidates, maxCount = 50)
        assertEquals("When fewer than 50 qualify, must select only the 17 qualifying nodes", 17, ranked.size)
    }

    @Test
    fun testSecretRedaction() {
        val rawLog = "Connecting to vless://25b583f7-92ea-4a4b-9147-380d322d713c@hk.example.com:443"
        val redacted = LogManager.redactSecrets(rawLog)

        assertFalse("UUID must be redacted from logs", redacted.contains("25b583f7-92ea-4a4b-9147-380d322d713c"))
        assertTrue("Redacted placeholder must be present", redacted.contains("[REDACTED]"))
    }
}
