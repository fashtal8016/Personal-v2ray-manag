package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.parser.V2RayUriParser
import com.example.model.GroupMembershipEntity
import com.example.model.ServerGroupEntity
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class ServerRepository(private val database: AppDatabase) {

    val allProfiles: Flow<List<ServerProfile>> = database.serverProfileDao().getAllProfiles()
    val mainSubProfiles: Flow<List<ServerProfile>> = database.serverProfileDao().getMainSubProfiles()
    val favoriteProfiles: Flow<List<ServerProfile>> = database.serverProfileDao().getFavoriteProfiles()
    val allStats: Flow<List<ServerStatsEntity>> = database.serverStatsDao().getAllStats()

    fun getProfileById(id: String): Flow<ServerProfile?> = database.serverProfileDao().getProfileById(id)

    suspend fun insertProfile(profile: ServerProfile) = withContext(Dispatchers.IO) {
        database.serverProfileDao().insertProfile(profile)
    }

    suspend fun updateProfile(profile: ServerProfile) = withContext(Dispatchers.IO) {
        database.serverProfileDao().updateProfile(profile)
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        database.serverProfileDao().setFavorite(id, isFavorite)
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        database.serverProfileDao().deleteProfile(id)
        database.testResultDao().deleteResultsForServer(id)
        database.serverStatsDao().deleteForServer(id)
    }

    suspend fun clearAllProfiles() = withContext(Dispatchers.IO) {
        database.serverProfileDao().deleteAll()
    }
}

class SubscriptionRepository(private val database: AppDatabase) {

    val allSubscriptions: Flow<List<SubscriptionEntity>> = database.subscriptionDao().getAllSubscriptions()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun addSubscription(name: String, url: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val sub = SubscriptionEntity(
            id = id,
            name = name.trim().ifEmpty { "Subscription" },
            url = url.trim(),
            enabled = true,
            status = "Added",
            createdAt = System.currentTimeMillis()
        )
        database.subscriptionDao().insertSubscription(sub)
        // Automatically fetch and parse nodes
        refreshSubscription(id)
        id
    }

    suspend fun toggleEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        database.subscriptionDao().setEnabled(id, enabled)
    }

    suspend fun deleteSubscription(id: String) = withContext(Dispatchers.IO) {
        database.subscriptionDao().deleteSubscription(id)
        database.serverProfileDao().deleteBySubscription(id)
    }

    suspend fun refreshSubscription(id: String): Boolean = withContext(Dispatchers.IO) {
        val sub = database.subscriptionDao().getSubscriptionById(id) ?: return@withContext false
        database.subscriptionDao().updateSubscription(sub.copy(status = "Updating..."))

        try {
            val req = Request.Builder()
                .url(sub.url)
                .header("User-Agent", "v2rayng/1.8.12")
                .build()

            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val content = res.body?.string() ?: ""
                val profiles = V2RayUriParser.parseSubscriptionContent(content, sub.id)

                // Preserve existing favorites if re-importing
                val existing = database.serverProfileDao().getProfilesBySubscriptionSync(sub.id)
                val favMap = existing.associate { it.fingerprintHash to it.isFavorite }

                val updatedProfiles = profiles.map { p ->
                    if (favMap[p.fingerprintHash] == true) p.copy(isFavorite = true) else p
                }

                // Delete old nodes from this sub and insert fresh
                database.serverProfileDao().deleteBySubscription(sub.id)
                database.serverProfileDao().insertProfiles(updatedProfiles)

                database.subscriptionDao().updateSubscription(
                    sub.copy(
                        lastRefreshed = System.currentTimeMillis(),
                        nodeCount = updatedProfiles.size,
                        status = "Success",
                        lastError = null
                    )
                )
                true
            } else {
                database.subscriptionDao().updateSubscription(
                    sub.copy(
                        status = "HTTP ${res.code}",
                        lastError = "HTTP ${res.code}: ${res.message}"
                    )
                )
                false
            }
        } catch (e: Exception) {
            database.subscriptionDao().updateSubscription(
                sub.copy(
                    status = "Error",
                    lastError = e.message ?: "Failed to connect to subscription host"
                )
            )
            false
        }
    }
}
