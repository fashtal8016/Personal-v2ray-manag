package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.model.ConnectionSessionEntity
import com.example.model.GroupMembershipEntity
import com.example.model.ServerGroupEntity
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.SubscriptionEntity
import com.example.model.TestResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    fun getProfileById(id: String): Flow<ServerProfile?>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getProfileByIdSync(id: String): ServerProfile?

    @Query("SELECT * FROM server_profiles WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteProfiles(): Flow<List<ServerProfile>>

    @Query("""
        SELECT sp.* FROM server_profiles sp
        INNER JOIN group_memberships gm ON sp.id = gm.serverId
        WHERE gm.groupId = 'main_sub'
        ORDER BY gm.rank ASC
    """)
    fun getMainSubProfiles(): Flow<List<ServerProfile>>

    @Query("""
        SELECT sp.* FROM server_profiles sp
        INNER JOIN group_memberships gm ON sp.id = gm.serverId
        WHERE gm.groupId = :groupId
        ORDER BY gm.rank ASC
    """)
    fun getProfilesByGroup(groupId: String): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE subscriptionId = :subId")
    suspend fun getProfilesBySubscriptionSync(subId: String): List<ServerProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ServerProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ServerProfile)

    @Update
    suspend fun updateProfile(profile: ServerProfile)

    @Query("UPDATE server_profiles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("DELETE FROM server_profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: String)

    @Query("DELETE FROM server_profiles")
    suspend fun deleteAll()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE enabled = 1")
    suspend fun getEnabledSubscriptions(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(sub: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(sub: SubscriptionEntity)

    @Query("UPDATE subscriptions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)
}

@Dao
interface ServerGroupDao {
    @Query("SELECT * FROM server_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<ServerGroupEntity>>

    @Query("SELECT * FROM server_groups WHERE id = :id")
    suspend fun getGroupById(id: String): ServerGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ServerGroupEntity)

    @Query("SELECT * FROM group_memberships WHERE groupId = :groupId ORDER BY rank ASC")
    fun getMemberships(groupId: String): Flow<List<GroupMembershipEntity>>

    @Query("SELECT * FROM group_memberships WHERE groupId = :groupId ORDER BY rank ASC")
    suspend fun getMembershipsSync(groupId: String): List<GroupMembershipEntity>

    @Query("DELETE FROM group_memberships WHERE groupId = :groupId")
    suspend fun clearGroupMemberships(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberships(memberships: List<GroupMembershipEntity>)

    @Transaction
    suspend fun replaceGroupMemberships(
        groupId: String,
        memberships: List<GroupMembershipEntity>,
        nodeCount: Int,
        syncTime: Long
    ) {
        clearGroupMemberships(groupId)
        insertMemberships(memberships)
        val existingGroup = getGroupById(groupId)
        if (existingGroup != null) {
            insertGroup(existingGroup.copy(nodeCount = nodeCount, lastSyncTime = syncTime))
        } else {
            insertGroup(
                ServerGroupEntity(
                    id = groupId,
                    name = if (groupId == "main_sub") "main sub" else groupId,
                    isSystemManaged = (groupId == "main_sub"),
                    syncMode = "Replace",
                    nodeCount = nodeCount,
                    lastSyncTime = syncTime
                )
            )
        }
    }
}

@Dao
interface TestResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: TestResultEntity)

    @Query("SELECT * FROM test_results WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentResults(serverId: String, limit: Int = 10): List<TestResultEntity>

    @Query("DELETE FROM test_results WHERE serverId = :serverId")
    suspend fun deleteResultsForServer(serverId: String)

    @Query("DELETE FROM test_results")
    suspend fun clearAll()
}

@Dao
interface ServerStatsDao {
    @Query("SELECT * FROM server_stats")
    fun getAllStats(): Flow<List<ServerStatsEntity>>

    @Query("SELECT * FROM server_stats WHERE serverId = :serverId")
    fun getStatsForServer(serverId: String): Flow<ServerStatsEntity?>

    @Query("SELECT * FROM server_stats WHERE serverId = :serverId")
    suspend fun getStatsForServerSync(serverId: String): ServerStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: ServerStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(statsList: List<ServerStatsEntity>)

    @Query("DELETE FROM server_stats WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)
}

@Dao
interface ConnectionSessionDao {
    @Query("SELECT * FROM connection_sessions ORDER BY startTime DESC LIMIT 50")
    fun getRecentSessions(): Flow<List<ConnectionSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConnectionSessionEntity)

    @Update
    suspend fun updateSession(session: ConnectionSessionEntity)

    @Query("SELECT * FROM connection_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): ConnectionSessionEntity?
}
