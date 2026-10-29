package com.example.data.testing

import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.TestResultEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object RankingEngine {

    /**
     * Calculates / updates ServerStatsEntity given historical test results and current test.
     */
    fun calculateStats(
        serverId: String,
        history: List<TestResultEntity>,
        newResult: DetailedTestResult? = null
    ): ServerStatsEntity {
        val allResults = if (newResult != null) {
            val converted = TestResultEntity(
                serverId = serverId,
                timestamp = System.currentTimeMillis(),
                success = newResult.success,
                latencyMs = newResult.totalLatencyMs,
                dnsTimeMs = newResult.dnsTimeMs,
                tcpConnectMs = newResult.tcpConnectMs,
                tlsHandshakeMs = newResult.tlsHandshakeMs,
                httpPingMs = newResult.httpPingMs,
                downloadSpeedBps = newResult.downloadSpeedBps,
                stage = newResult.stage.name,
                errorMessage = newResult.errorMessage
            )
            listOf(converted) + history
        } else {
            history
        }

        if (allResults.isEmpty()) {
            return ServerStatsEntity(serverId = serverId)
        }

        val totalTests = allResults.size
        val successResults = allResults.filter { it.success }
        val successfulTests = successResults.size
        val reliability = if (totalTests > 0) successfulTests.toFloat() / totalTests.toFloat() else 0.0f

        val latencies = successResults.map { it.latencyMs }.filter { it > 0 }
        val avgLatency = if (latencies.isNotEmpty()) latencies.average().toLong() else 0L
        val minLatency = if (latencies.isNotEmpty()) latencies.minOrNull() ?: 0L else 0L
        val maxLatency = if (latencies.isNotEmpty()) latencies.maxOrNull() ?: 0L else 0L

        // Jitter: mean absolute difference between consecutive latency measurements
        val jitter = if (latencies.size > 1) {
            var diffSum = 0L
            for (i in 1 until latencies.size) {
                diffSum += abs(latencies[i] - latencies[i - 1])
            }
            diffSum / (latencies.size - 1)
        } else {
            0L
        }

        val speeds = successResults.map { it.downloadSpeedBps }.filter { it > 0 }
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average().toLong() else 0L
        val lastSuccess = allResults.firstOrNull()?.success ?: false

        return ServerStatsEntity(
            serverId = serverId,
            totalTests = totalTests,
            successfulTests = successfulTests,
            reliability = reliability,
            avgLatencyMs = avgLatency,
            minLatencyMs = minLatency,
            maxLatencyMs = maxLatency,
            jitterMs = jitter,
            avgDownloadSpeedBps = avgSpeed,
            compositeScore = 0f, // will be normalized during ranking
            lastTestedAt = allResults.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
            lastStatusSuccess = lastSuccess
        )
    }

    /**
     * Ranks candidate servers based on the exact formula:
     * - Reliability: 40%
     * - Latency: 25% (inverted: lower is better)
     * - Download throughput: 25% (higher is better)
     * - Latency consistency / jitter: 10% (lower is better)
     *
     * Returns the sorted list of servers with updated composite scores.
     * Selects up to [maxCount] (default 50) qualifying nodes.
     */
    fun rankServers(
        candidates: List<Pair<ServerProfile, ServerStatsEntity>>,
        maxCount: Int = 50,
        minReliabilityThreshold: Float = 0.2f
    ): List<Pair<ServerProfile, ServerStatsEntity>> {
        // Exclude nodes that failed recent tests or fail minimum reliability
        val eligible = candidates.filter { (_, stats) ->
            stats.successfulTests > 0 &&
                    stats.lastStatusSuccess &&
                    stats.avgLatencyMs > 0 &&
                    stats.reliability >= minReliabilityThreshold
        }

        if (eligible.isEmpty()) return emptyList()

        val minLatency = eligible.minOfOrNull { it.second.avgLatencyMs } ?: 1L
        val maxLatency = max(minLatency + 1L, eligible.maxOfOrNull { it.second.avgLatencyMs } ?: 5000L)

        val minSpeed = eligible.minOfOrNull { it.second.avgDownloadSpeedBps } ?: 0L
        val maxSpeed = max(minSpeed + 1L, eligible.maxOfOrNull { it.second.avgDownloadSpeedBps } ?: 1_000_000L)

        val maxJitter = max(1L, eligible.maxOfOrNull { it.second.jitterMs } ?: 100L)

        val scored = eligible.map { (profile, stats) ->
            // 1. Reliability score: 0 to 40 points
            val reliabilityScore = stats.reliability * 40f

            // 2. Latency score: 0 to 25 points (normalized inverted)
            val latencyNorm = ((maxLatency - stats.avgLatencyMs).toFloat() / (maxLatency - minLatency).toFloat())
                .coerceIn(0f, 1f)
            val latencyScore = latencyNorm * 25f

            // 3. Throughput score: 0 to 25 points
            val speedNorm = if (maxSpeed > minSpeed) {
                ((stats.avgDownloadSpeedBps - minSpeed).toFloat() / (maxSpeed - minSpeed).toFloat())
                    .coerceIn(0f, 1f)
            } else 0.5f
            val throughputScore = speedNorm * 25f

            // 4. Jitter score: 0 to 10 points (lower jitter is better)
            val jitterNorm = (1f - (stats.jitterMs.toFloat() / maxJitter.toFloat())).coerceIn(0f, 1f)
            val jitterScore = jitterNorm * 10f

            var totalScore = reliabilityScore + latencyScore + throughputScore + jitterScore

            // Penalty for high latency > 2000ms
            if (stats.avgLatencyMs > 2000L) {
                totalScore = max(0f, totalScore - 15f)
            }

            val updatedStats = stats.copy(compositeScore = totalScore)
            profile to updatedStats
        }

        // Sort descending by compositeScore, then ascending by avgLatencyMs, then alphabetically by name
        val sorted = scored.sortedWith(
            compareByDescending<Pair<ServerProfile, ServerStatsEntity>> { it.second.compositeScore }
                .thenBy { it.second.avgLatencyMs }
                .thenBy { it.first.name }
        )

        // Select up to maxCount (50). If fewer qualify, select only qualifying nodes!
        return sorted.take(maxCount)
    }
}
