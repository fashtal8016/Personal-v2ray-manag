package com.example.data.testing

import com.example.data.local.AppDatabase
import com.example.data.parser.V2RayUriParser
import com.example.model.GroupMembershipEntity
import com.example.model.ServerGroupEntity
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.TestResultEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class SyncProgress(
    val isRunning: Boolean = false,
    val stage: String = "Idle",
    val subscriptionsCount: Int = 0,
    val subscriptionsProcessed: Int = 0,
    val totalNodesFound: Int = 0,
    val nodesTested: Int = 0,
    val nodesPassed: Int = 0,
    val nodesFailed: Int = 0,
    val mainSubSelectedCount: Int = 0,
    val lastError: String? = null
)

class MainSubManager(
    private val database: AppDatabase,
    private val tester: NetworkTester = NetworkTester()
) {
    companion object {
        const val MAIN_SUB_GROUP_ID = "main_sub"
        const val MAIN_SUB_GROUP_NAME = "main sub"
        const val MAX_MAIN_SUB_NODES = 50
    }

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private var currentJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Executes the complete end-to-end refresh & ranking pipeline for "main sub".
     */
    suspend fun executeMainSubSync(
        concurrencyLimit: Int = 8,
        includeThroughput: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        _syncProgress.value = SyncProgress(
            isRunning = true,
            stage = "Reading subscriptions..."
        )

        try {
            val enabledSubs = database.subscriptionDao().getEnabledSubscriptions()
            _syncProgress.value = _syncProgress.value.copy(
                subscriptionsCount = enabledSubs.size,
                stage = "Fetching subscription feeds..."
            )

            val allParsedProfiles = mutableListOf<ServerProfile>()

            for ((index, sub) in enabledSubs.withIndex()) {
                _syncProgress.value = _syncProgress.value.copy(
                    subscriptionsProcessed = index + 1,
                    stage = "Fetching: ${sub.name}"
                )

                try {
                    val request = Request.Builder()
                        .url(sub.url)
                        .header("User-Agent", "v2rayng/1.8.12 (Android)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val parsed = V2RayUriParser.parseSubscriptionContent(body, sub.id)
                        allParsedProfiles.addAll(parsed)

                        // Update subscription stats
                        database.subscriptionDao().updateSubscription(
                            sub.copy(
                                lastRefreshed = System.currentTimeMillis(),
                                nodeCount = parsed.size,
                                status = "Success",
                                lastError = null
                            )
                        )
                    } else {
                        database.subscriptionDao().updateSubscription(
                            sub.copy(
                                status = "HTTP ${response.code}",
                                lastError = "HTTP ${response.code}: ${response.message}"
                            )
                        )
                    }
                } catch (e: Exception) {
                    database.subscriptionDao().updateSubscription(
                        sub.copy(
                            status = "Error",
                            lastError = e.message ?: "Connection failure"
                        )
                    )
                }
            }

            // Also include any manually added or existing profiles in database
            val existingProfiles = database.serverProfileDao().getAllProfiles()
            // We can combine freshly fetched profiles with deduplication
            val mergedCandidates = mutableMapOf<String, ServerProfile>()

            for (p in allParsedProfiles) {
                // Deduplicate by stable fingerprint hash
                if (!mergedCandidates.containsKey(p.fingerprintHash)) {
                    mergedCandidates[p.fingerprintHash] = p
                }
            }

            // Save freshly imported profiles to database
            if (mergedCandidates.isNotEmpty()) {
                database.serverProfileDao().insertProfiles(mergedCandidates.values.toList())
            }

            // Now retrieve full candidate pool for testing
            val allCandidates = mergedCandidates.values.toList().ifEmpty {
                // If subscriptions failed, use existing database profiles as fallback
                database.serverProfileDao().getProfilesBySubscriptionSync("") // or all
            }

            _syncProgress.value = _syncProgress.value.copy(
                totalNodesFound = allCandidates.size,
                stage = "Testing candidate nodes (0/${allCandidates.size})..."
            )

            if (allCandidates.isEmpty()) {
                _syncProgress.value = _syncProgress.value.copy(
                    isRunning = false,
                    stage = "No nodes available to test",
                    lastError = "No valid nodes found in enabled subscriptions."
                )
                return@withContext false
            }

            // Test candidate nodes with bounded concurrency
            val semaphore = Semaphore(concurrencyLimit)
            val testedCandidates = mutableListOf<Pair<ServerProfile, ServerStatsEntity>>()
            var passedCount = 0
            var failedCount = 0
            var testedCount = 0

            val coroutineJobs = mutableListOf<Job>()
            val syncScope = CoroutineScope(Dispatchers.IO)

            for (candidate in allCandidates) {
                val job = syncScope.launch {
                    semaphore.withPermit {
                        val testResult = tester.testServer(candidate, includeThroughput = false)
                        val history = database.testResultDao().getRecentResults(candidate.id, 5)
                        val stats = RankingEngine.calculateStats(candidate.id, history, testResult)

                        // Save test result and stats
                        database.testResultDao().insertResult(
                            TestResultEntity(
                                serverId = candidate.id,
                                success = testResult.success,
                                latencyMs = testResult.totalLatencyMs,
                                dnsTimeMs = testResult.dnsTimeMs,
                                tcpConnectMs = testResult.tcpConnectMs,
                                tlsHandshakeMs = testResult.tlsHandshakeMs,
                                httpPingMs = testResult.httpPingMs,
                                downloadSpeedBps = testResult.downloadSpeedBps,
                                stage = testResult.stage.name,
                                errorMessage = testResult.errorMessage
                            )
                        )
                        database.serverStatsDao().insertOrUpdate(stats)

                        synchronized(testedCandidates) {
                            testedCount++
                            if (testResult.success) {
                                passedCount++
                                testedCandidates.add(candidate to stats)
                            } else {
                                failedCount++
                            }
                            _syncProgress.value = _syncProgress.value.copy(
                                nodesTested = testedCount,
                                nodesPassed = passedCount,
                                nodesFailed = failedCount,
                                stage = "Testing nodes ($testedCount/${allCandidates.size})..."
                            )
                        }
                    }
                }
                coroutineJobs.add(job)
            }

            // Await all testing jobs
            coroutineJobs.forEach { it.join() }

            // Throughput test on the fastest candidates if enabled
            if (includeThroughput && testedCandidates.isNotEmpty()) {
                val topFastCandidates = testedCandidates.sortedBy { it.second.avgLatencyMs }.take(15)
                for (item in topFastCandidates) {
                    val speedResult = tester.testServer(item.first, includeThroughput = true)
                    if (speedResult.downloadSpeedBps > 0) {
                        val updatedStats = item.second.copy(
                            avgDownloadSpeedBps = speedResult.downloadSpeedBps
                        )
                        database.serverStatsDao().insertOrUpdate(updatedStats)
                    }
                }
            }

            // Rank eligible servers & select top 50
            _syncProgress.value = _syncProgress.value.copy(stage = "Ranking and selecting top 50...")
            val rankedTop50 = RankingEngine.rankServers(testedCandidates, maxCount = MAX_MAIN_SUB_NODES)

            if (rankedTop50.isNotEmpty()) {
                // Update stats with composite scores
                database.serverStatsDao().insertOrUpdateAll(rankedTop50.map { it.second })

                // Replace memberships in "main sub" atomically
                val memberships = rankedTop50.mapIndexed { index, pair ->
                    GroupMembershipEntity(
                        groupId = MAIN_SUB_GROUP_ID,
                        serverId = pair.first.id,
                        rank = index + 1
                    )
                }

                database.serverGroupDao().replaceGroupMemberships(
                    groupId = MAIN_SUB_GROUP_ID,
                    memberships = memberships,
                    nodeCount = memberships.size,
                    syncTime = System.currentTimeMillis()
                )

                _syncProgress.value = _syncProgress.value.copy(
                    isRunning = false,
                    stage = "Synchronization complete! Selected ${rankedTop50.size} best nodes into 'main sub'.",
                    mainSubSelectedCount = rankedTop50.size
                )
                true
            } else {
                // If none qualify, retain previous main sub group!
                _syncProgress.value = _syncProgress.value.copy(
                    isRunning = false,
                    stage = "No candidates passed connectivity test. Preserved previous 'main sub'.",
                    lastError = "All nodes failed connectivity tests."
                )
                false
            }
        } catch (e: Exception) {
            _syncProgress.value = _syncProgress.value.copy(
                isRunning = false,
                stage = "Sync error: ${e.message}",
                lastError = e.message
            )
            false
        }
    }

    fun cancelSync() {
        currentJob?.cancel()
        _syncProgress.value = _syncProgress.value.copy(
            isRunning = false,
            stage = "Cancelled"
        )
    }
}
