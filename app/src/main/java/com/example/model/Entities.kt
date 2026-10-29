package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "server_profiles",
    indices = [
        Index(value = ["fingerprintHash"], unique = false),
        Index(value = ["subscriptionId"])
    ]
)
data class ServerProfile(
    @PrimaryKey
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val uuid: String, // UUID for VMess/VLESS, password for Trojan/Shadowsocks
    val encryption: String = "auto",
    val transport: TransportProtocol = TransportProtocol.TCP,
    val path: String = "",
    val hostHeader: String = "",
    val sni: String = "",
    val security: SecurityType = SecurityType.NONE,
    val fingerprint: String = "chrome",
    val alpn: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val flow: String = "",
    val subscriptionId: String? = null,
    val isFavorite: Boolean = false,
    val fingerprintHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastRefreshed: Long = 0L,
    val nodeCount: Int = 0,
    val status: String = "Idle",
    val lastError: String? = null,
    val autoRefreshMinutes: Int = 60,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "server_groups")
data class ServerGroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isSystemManaged: Boolean = false,
    val syncMode: String = "Replace", // "Replace" or "Merge"
    val nodeCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_memberships",
    primaryKeys = ["groupId", "serverId"],
    indices = [Index(value = ["serverId"]), Index(value = ["groupId"])]
)
data class GroupMembershipEntity(
    val groupId: String,
    val serverId: String,
    val rank: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "test_results",
    indices = [Index(value = ["serverId"]), Index(value = ["timestamp"])]
)
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val serverId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val latencyMs: Long,
    val dnsTimeMs: Long = 0L,
    val tcpConnectMs: Long = 0L,
    val tlsHandshakeMs: Long = 0L,
    val httpPingMs: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val stage: String = TestStage.COMPLETE.name,
    val errorMessage: String? = null
)

@Entity(tableName = "server_stats")
data class ServerStatsEntity(
    @PrimaryKey
    val serverId: String,
    val totalTests: Int = 0,
    val successfulTests: Int = 0,
    val reliability: Float = 0.0f, // 0.0 to 1.0
    val avgLatencyMs: Long = 0L,
    val minLatencyMs: Long = 0L,
    val maxLatencyMs: Long = 0L,
    val jitterMs: Long = 0L,
    val avgDownloadSpeedBps: Long = 0L,
    val compositeScore: Float = 0.0f, // 0.0 to 100.0
    val lastTestedAt: Long = 0L,
    val lastStatusSuccess: Boolean = false
)

@Entity(tableName = "connection_sessions")
data class ConnectionSessionEntity(
    @PrimaryKey
    val id: String,
    val serverId: String,
    val serverName: String,
    val protocol: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val disconnectReason: String? = null
)
