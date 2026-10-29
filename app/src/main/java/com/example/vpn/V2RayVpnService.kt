package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.model.ConnectionSessionEntity
import com.example.model.ServerProfile
import com.example.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

object VpnController {
    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _currentProfile = MutableStateFlow<ServerProfile?>(null)
    val currentProfile: StateFlow<ServerProfile?> = _currentProfile.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private val _connectionDurationSeconds = MutableStateFlow(0L)
    val connectionDurationSeconds: StateFlow<Long> = _connectionDurationSeconds.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private var durationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun updateState(state: VpnState, profile: ServerProfile? = null, error: String? = null) {
        _vpnState.value = state
        if (profile != null) _currentProfile.value = profile
        if (error != null) _lastErrorMessage.value = error

        if (state == VpnState.CONNECTED) {
            startDurationCounter()
        } else if (state == VpnState.DISCONNECTED || state == VpnState.ERROR) {
            stopDurationCounter()
        }
    }

    fun updateTraffic(stats: TrafficStats) {
        _trafficStats.value = stats
    }

    private fun startDurationCounter() {
        durationJob?.cancel()
        _connectionDurationSeconds.value = 0L
        durationJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                _connectionDurationSeconds.value = (System.currentTimeMillis() - start) / 1000L
            }
        }
    }

    private fun stopDurationCounter() {
        durationJob?.cancel()
        durationJob = null
    }

    fun startVpn(context: Context, profile: ServerProfile) {
        val intent = Intent(context, V2RayVpnService::class.java).apply {
            action = V2RayVpnService.ACTION_CONNECT
            putExtra(V2RayVpnService.EXTRA_SERVER_ID, profile.id)
            putExtra(V2RayVpnService.EXTRA_SERVER_NAME, profile.name)
            putExtra(V2RayVpnService.EXTRA_SERVER_HOST, profile.server)
            putExtra(V2RayVpnService.EXTRA_SERVER_PORT, profile.port)
            putExtra(V2RayVpnService.EXTRA_PROTOCOL, profile.protocol.name)
        }
        _currentProfile.value = profile
        _vpnState.value = VpnState.CONNECTING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpn(context: Context) {
        _vpnState.value = VpnState.DISCONNECTING
        val intent = Intent(context, V2RayVpnService::class.java).apply {
            action = V2RayVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}

class V2RayVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"

        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_PROTOCOL = "extra_protocol"

        private const val NOTIFICATION_CHANNEL_ID = "v2ray_vpn_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var packetRouter: TunPacketRouter? = null
    private var activeSessionId: String? = null
    private var serverId: String = ""
    private var serverName: String = "V2Ray Node"
    private var serverHost: String = ""
    private var serverPort: Int = 443
    private var protocol: String = "VLESS"
    private var sessionStartTime: Long = 0L

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var trafficCollectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CONNECT -> {
                serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: ""
                serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "V2Ray Node"
                serverHost = intent.getStringExtra(EXTRA_SERVER_HOST) ?: ""
                serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "VLESS"

                startVpnSession()
            }
            ACTION_DISCONNECT -> {
                stopVpnSession()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpnSession() {
        try {
            // 1. Start foreground with notification
            val notification = buildNotification(serverName, "Establishing secure connection...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // 2. Establish TUN Interface
            val builder = Builder()
                .setSession("Personal V2Ray: $serverName")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            tunInterface = builder.establish()

            if (tunInterface == null) {
                VpnController.updateState(VpnState.ERROR, error = "Failed to establish VPN interface. Authorization revoked?")
                stopSelf()
                return
            }

            sessionStartTime = System.currentTimeMillis()
            activeSessionId = UUID.randomUUID().toString()

            // 3. Initialize TunPacketRouter
            packetRouter = TunPacketRouter(this, tunInterface!!, serverHost, serverPort)
            packetRouter?.start()

            // 4. Update Notification & Controller State
            val connectedNotification = buildNotification(serverName, "Connected securely via $protocol")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, connectedNotification)

            VpnController.updateState(VpnState.CONNECTED)

            // 5. Observe traffic stats
            trafficCollectJob?.cancel()
            trafficCollectJob = serviceScope.launch {
                packetRouter?.trafficStats?.collect { stats ->
                    VpnController.updateTraffic(stats)
                }
            }

            // Record session in database
            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                db.connectionSessionDao().insertSession(
                    ConnectionSessionEntity(
                        id = activeSessionId!!,
                        serverId = serverId,
                        serverName = serverName,
                        protocol = protocol,
                        startTime = sessionStartTime
                    )
                )
            }

        } catch (e: Exception) {
            Log.e("V2RayVpnService", "Error starting VPN: ${e.message}", e)
            VpnController.updateState(VpnState.ERROR, error = e.message)
            stopSelf()
        }
    }

    private fun stopVpnSession() {
        trafficCollectJob?.cancel()
        packetRouter?.stop()
        packetRouter = null

        try {
            tunInterface?.close()
        } catch (_: Exception) {}
        tunInterface = null

        // Update session record in database
        val durationMs = System.currentTimeMillis() - sessionStartTime
        val stats = VpnController.trafficStats.value
        activeSessionId?.let { sId ->
            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val session = db.connectionSessionDao().getActiveSession()
                if (session != null && session.id == sId) {
                    db.connectionSessionDao().updateSession(
                        session.copy(
                            endTime = System.currentTimeMillis(),
                            bytesIn = stats.totalBytesIn,
                            bytesOut = stats.totalBytesOut,
                            disconnectReason = "User requested disconnect"
                        )
                    )
                }
            }
        }

        VpnController.updateState(VpnState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status of active V2Ray VPN connection"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, V2RayVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        stopVpnSession()
        super.onDestroy()
    }
}
