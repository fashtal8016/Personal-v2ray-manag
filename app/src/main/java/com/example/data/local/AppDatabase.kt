package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.model.ConnectionSessionEntity
import com.example.model.GroupMembershipEntity
import com.example.model.ProxyProtocol
import com.example.model.SecurityType
import com.example.model.ServerGroupEntity
import com.example.model.ServerProfile
import com.example.model.ServerStatsEntity
import com.example.model.SubscriptionEntity
import com.example.model.TestResultEntity
import com.example.model.TransportProtocol

class AppTypeConverters {
    @TypeConverter
    fun fromProxyProtocol(value: ProxyProtocol?): String = value?.name ?: ProxyProtocol.VMESS.name

    @TypeConverter
    fun toProxyProtocol(value: String?): ProxyProtocol =
        value?.let { runCatching { ProxyProtocol.valueOf(it) }.getOrNull() } ?: ProxyProtocol.VMESS

    @TypeConverter
    fun fromTransportProtocol(value: TransportProtocol?): String =
        value?.name ?: TransportProtocol.TCP.name

    @TypeConverter
    fun toTransportProtocol(value: String?): TransportProtocol =
        value?.let { runCatching { TransportProtocol.valueOf(it) }.getOrNull() } ?: TransportProtocol.TCP

    @TypeConverter
    fun fromSecurityType(value: SecurityType?): String = value?.name ?: SecurityType.NONE.name

    @TypeConverter
    fun toSecurityType(value: String?): SecurityType =
        value?.let { runCatching { SecurityType.valueOf(it) }.getOrNull() } ?: SecurityType.NONE
}

@Database(
    entities = [
        ServerProfile::class,
        SubscriptionEntity::class,
        ServerGroupEntity::class,
        GroupMembershipEntity::class,
        TestResultEntity::class,
        ServerStatsEntity::class,
        ConnectionSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun serverGroupDao(): ServerGroupDao
    abstract fun testResultDao(): TestResultDao
    abstract fun serverStatsDao(): ServerStatsDao
    abstract fun connectionSessionDao(): ConnectionSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "v2ray_manager.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
