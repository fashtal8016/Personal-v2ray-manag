package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val tag: String,
    val message: String
)

object LogManager {
    private const val MAX_LOGS = 200
    private val logs = mutableListOf<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)

    private fun log(level: String, tag: String, message: String) {
        val sanitized = redactSecrets(message)
        val entry = LogEntry(level = level, tag = tag, message = sanitized)
        synchronized(logs) {
            logs.add(0, entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(logs.size - 1)
            }
            _logsFlow.value = logs.toList()
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
            _logsFlow.value = emptyList()
        }
    }

    /**
     * Redacts UUIDs, long hexadecimal tokens, passwords, and user-info components.
     */
    fun redactSecrets(input: String): String {
        return input
            // Redact UUIDs: e.g. 8-4-4-4-12 hex chars
            .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "••••••••-••••-••••-••••-••••••••••••")
            // Redact URI userinfo credentials: e.g. vless://<secret>@host
            .replace(Regex("(vless|trojan|vmess|ss)://([^@]+)@"), "$1://[REDACTED]@")
            // Redact password parameters
            .replace(Regex("""("password"|"id"|"uuid"|"pbk"|"sid")\s*:\s*"[^"]+""""), """$1:"[REDACTED]"""")
    }
}

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
            else -> String.format(Locale.US, "%.1f KB/s", kb)
        }
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0) return "Never"
        val diff = System.currentTimeMillis() - millis
        if (diff < 60_000) return "Just now"
        if (diff < 3600_000) return "${diff / 60_000}m ago"
        if (diff < 86400_000) return "${diff / 3600_000}h ago"
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }
}
