package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.parser.V2RayUriParser
import com.example.data.repository.ServerRepository
import com.example.data.repository.SubscriptionRepository
import com.example.data.testing.DetailedTestResult
import com.example.data.testing.MainSubManager
import com.example.data.testing.NetworkTester
import com.example.data.testing.RankingEngine
import com.example.data.testing.SyncProgress
import com.example.model.GroupMembershipEntity
import com.example.model.ProxyProtocol
import com.example.model.ServerGroupEntity
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.SubscriptionEntity
import com.example.model.TestResultEntity
import com.example.model.VpnState
import com.example.util.LogManager
import com.example.vpn.TrafficStats
import com.example.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ServerFilter {
    ALL,
    MAIN_SUB,
    WORKING,
    FAVORITES,
    UNTESTED,
    FAILED
}

enum class ServerSort {
    SCORE,
    LATENCY,
    SPEED,
    NAME
}

enum class AppTab(val title: String) {
    DASHBOARD("Dashboard"),
    SERVERS("Servers"),
    SUBSCRIPTIONS("Subscriptions"),
    TESTING("Testing"),
    SETTINGS("Settings")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val serverRepo = ServerRepository(database)
    private val subRepo = SubscriptionRepository(database)
    private val tester = NetworkTester()
    val mainSubManager = MainSubManager(database, tester)

    // UI Navigation State
    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // Server Selection & VPN State
    val vpnState: StateFlow<VpnState> = VpnController.vpnState
    val currentProfile: StateFlow<ServerProfile?> = VpnController.currentProfile
    val trafficStats: StateFlow<TrafficStats> = VpnController.trafficStats
    val durationSeconds: StateFlow<Long> = VpnController.connectionDurationSeconds
    val lastError: StateFlow<String?> = VpnController.lastErrorMessage

    // Database Observables
    val allServers: StateFlow<List<ServerProfile>> = serverRepo.allProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val mainSubServers: StateFlow<List<ServerProfile>> = serverRepo.mainSubProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSubscriptions: StateFlow<List<SubscriptionEntity>> = subRepo.allSubscriptions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allStats: StateFlow<List<ServerStatsEntity>> = serverRepo.allStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val syncProgress: StateFlow<SyncProgress> = mainSubManager.syncProgress

    // Filter & Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(ServerFilter.ALL)
    val activeFilter: StateFlow<ServerFilter> = _activeFilter.asStateFlow()

    private val _activeSort = MutableStateFlow(ServerSort.SCORE)
    val activeSort: StateFlow<ServerSort> = _activeSort.asStateFlow()

    // Dialog & Sheet States
    private val _inspectingProfile = MutableStateFlow<ServerProfile?>(null)
    val inspectingProfile: StateFlow<ServerProfile?> = _inspectingProfile.asStateFlow()

    private val _testingServerId = MutableStateFlow<String?>(null)
    val testingServerId: StateFlow<String?> = _testingServerId.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilter(filter: ServerFilter) { _activeFilter.value = filter }
    fun setSort(sort: ServerSort) { _activeSort.value = sort }
    fun setInspectingProfile(profile: ServerProfile?) { _inspectingProfile.value = profile }

    // Combined Filter / Query / Sort Parameters
    private val filterCriteria = combine(_searchQuery, _activeFilter, _activeSort) { query, filter, sort ->
        Triple(query, filter, sort)
    }

    // Combined Filtered Server List
    val filteredServers: StateFlow<List<ServerProfile>> = combine(
        allServers,
        mainSubServers,
        allStats,
        filterCriteria
    ) { all, mainSub, statsList, (query, filter, sort) ->
        val statsMap = statsList.associateBy { it.serverId }

        val baseList = when (filter) {
            ServerFilter.ALL -> all
            ServerFilter.MAIN_SUB -> mainSub
            ServerFilter.WORKING -> all.filter {
                val s = statsMap[it.id]
                s != null && s.lastStatusSuccess && s.avgLatencyMs > 0
            }
            ServerFilter.FAVORITES -> all.filter { it.isFavorite }
            ServerFilter.UNTESTED -> all.filter { statsMap[it.id]?.totalTests ?: 0 == 0 }
            ServerFilter.FAILED -> all.filter {
                val s = statsMap[it.id]
                s != null && s.totalTests > 0 && !s.lastStatusSuccess
            }
        }

        // Search text matching
        val searched = if (query.isBlank()) {
            baseList
        } else {
            val q = query.trim().lowercase()
            baseList.filter {
                it.name.lowercase().contains(q) ||
                        it.server.lowercase().contains(q) ||
                        it.protocol.displayName.lowercase().contains(q)
            }
        }

        // Sort
        when (sort) {
            ServerSort.SCORE -> searched.sortedByDescending { statsMap[it.id]?.compositeScore ?: -1f }
            ServerSort.LATENCY -> searched.sortedWith(
                compareBy {
                    val lat = statsMap[it.id]?.avgLatencyMs ?: 0L
                    if (lat <= 0) 999999L else lat
                }
            )
            ServerSort.SPEED -> searched.sortedByDescending { statsMap[it.id]?.avgDownloadSpeedBps ?: 0L }
            ServerSort.NAME -> searched.sortedBy { it.name.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // VPN Connection Actions
    fun requestConnect(context: Context, profile: ServerProfile, onPrepareNeeded: (Intent) -> Unit) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            onPrepareNeeded(prepareIntent)
        } else {
            LogManager.i("VPN", "Connecting to ${profile.name} (${profile.protocol.displayName})")
            VpnController.startVpn(context, profile)
        }
    }

    fun disconnect(context: Context) {
        LogManager.i("VPN", "Disconnecting session")
        VpnController.stopVpn(context)
    }

    fun connectBestServer(context: Context, onPrepareNeeded: (Intent) -> Unit) {
        val best = mainSubServers.value.firstOrNull() ?: allServers.value.firstOrNull()
        if (best != null) {
            requestConnect(context, best, onPrepareNeeded)
        }
    }

    // Ping Test Single Server
    fun testIndividualServer(profile: ServerProfile) {
        viewModelScope.launch {
            _testingServerId.value = profile.id
            LogManager.i("Test", "Testing node: ${profile.name} (${profile.server}:${profile.port})")
            val result = tester.testServer(profile, includeThroughput = true)
            val history = database.testResultDao().getRecentResults(profile.id, 5)
            val stats = RankingEngine.calculateStats(profile.id, history, result)

            database.testResultDao().insertResult(
                TestResultEntity(
                    serverId = profile.id,
                    success = result.success,
                    latencyMs = result.totalLatencyMs,
                    dnsTimeMs = result.dnsTimeMs,
                    tcpConnectMs = result.tcpConnectMs,
                    tlsHandshakeMs = result.tlsHandshakeMs,
                    httpPingMs = result.httpPingMs,
                    downloadSpeedBps = result.downloadSpeedBps,
                    stage = result.stage.name,
                    errorMessage = result.errorMessage
                )
            )
            database.serverStatsDao().insertOrUpdate(stats)
            _testingServerId.value = null

            if (result.success) {
                LogManager.i("Test", "Node ${profile.name} OK: latency=${result.totalLatencyMs}ms")
            } else {
                LogManager.w("Test", "Node ${profile.name} failed at ${result.stage.name}: ${result.errorMessage}")
            }
        }
    }

    // Batch Test & Main Sub Sync
    fun startMainSubSync() {
        viewModelScope.launch {
            LogManager.i("Sync", "Starting full subscription refresh & 'main sub' top-50 selection")
            mainSubManager.executeMainSubSync()
        }
    }

    fun cancelSync() {
        mainSubManager.cancelSync()
    }

    // Server Profile Actions
    fun toggleFavorite(profile: ServerProfile) {
        viewModelScope.launch {
            serverRepo.setFavorite(profile.id, !profile.isFavorite)
        }
    }

    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            serverRepo.deleteProfile(profile.id)
        }
    }

    fun importConfig(uriStr: String): Boolean {
        val profile = V2RayUriParser.parseUri(uriStr) ?: return false
        viewModelScope.launch {
            serverRepo.insertProfile(profile)
            LogManager.i("Import", "Imported node: ${profile.name} (${profile.protocol.displayName})")
            testIndividualServer(profile)
        }
        return true
    }

    // Subscription Actions
    fun addSubscription(name: String, url: String) {
        viewModelScope.launch {
            LogManager.i("Subscription", "Adding subscription: $name")
            subRepo.addSubscription(name, url)
        }
    }

    fun refreshSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            LogManager.i("Subscription", "Refreshing subscription: ${sub.name}")
            subRepo.refreshSubscription(sub.id)
        }
    }

    fun toggleSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            subRepo.toggleEnabled(sub.id, !sub.enabled)
        }
    }

    fun deleteSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            LogManager.i("Subscription", "Deleting subscription: ${sub.name}")
            subRepo.deleteSubscription(sub.id)
        }
    }
}
