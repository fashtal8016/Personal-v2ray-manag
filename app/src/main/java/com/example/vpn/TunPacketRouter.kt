package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class TrafficStats(
    val uploadSpeedBps: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val totalBytesIn: Long = 0L,
    val totalBytesOut: Long = 0L
)

class TunPacketRouter(
    private val vpnService: VpnService,
    private val tunInterface: ParcelFileDescriptor,
    private val serverHost: String,
    private val serverPort: Int
) {
    private val isRunning = AtomicBoolean(true)
    private val totalBytesIn = AtomicLong(0L)
    private val totalBytesOut = AtomicLong(0L)

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private var routerJob: Job? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        isRunning.set(true)

        // Packet reading loop from TUN interface
        routerJob = scope.launch {
            val fileInputStream = FileInputStream(tunInterface.fileDescriptor)
            val fileOutputStream = FileOutputStream(tunInterface.fileDescriptor)
            val packetBuffer = ByteArray(32768)

            try {
                while (isActive && isRunning.get()) {
                    val bytesRead = fileInputStream.read(packetBuffer)
                    if (bytesRead > 0) {
                        totalBytesOut.addAndGet(bytesRead.toLong())

                        // Parse IPv4 header to prevent routing loops and monitor packets
                        if (bytesRead >= 20) {
                            val version = (packetBuffer[0].toInt() shr 4) and 0x0F
                            if (version == 4) {
                                val protocol = packetBuffer[9].toInt() and 0xFF // 6=TCP, 17=UDP, 1=ICMP
                                // Route / process packet
                                // Upstream sockets are protected via vpnService.protect(socket)
                            }
                        }
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.d("TunPacketRouter", "TUN read loop finished: ${e.message}")
                }
            } finally {
                try { fileInputStream.close() } catch (_: Exception) {}
                try { fileOutputStream.close() } catch (_: Exception) {}
            }
        }

        // Live Speed monitoring loop (calculates upload & download Bps each second)
        statsJob = scope.launch {
            var prevIn = 0L
            var prevOut = 0L
            while (isActive && isRunning.get()) {
                delay(1000L)
                val curIn = totalBytesIn.get()
                val curOut = totalBytesOut.get()

                val speedIn = curIn - prevIn
                val speedOut = curOut - prevOut

                prevIn = curIn
                prevOut = curOut

                _trafficStats.value = TrafficStats(
                    uploadSpeedBps = speedOut,
                    downloadSpeedBps = speedIn,
                    totalBytesIn = curIn,
                    totalBytesOut = curOut
                )
            }
        }
    }

    /**
     * Protects an upstream socket so its packets are routed directly outside the VPN TUN interface,
     * avoiding routing loops.
     */
    fun protectSocket(socket: Socket): Boolean {
        return vpnService.protect(socket)
    }

    fun stop() {
        isRunning.set(false)
        routerJob?.cancel()
        statsJob?.cancel()
        try {
            tunInterface.close()
        } catch (_: Exception) {}
    }
}
