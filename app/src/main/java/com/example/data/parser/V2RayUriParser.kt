package com.example.data.parser

import android.net.Uri
import android.util.Base64
import com.example.model.ProxyProtocol
import com.example.model.SecurityType
import com.example.model.ServerProfile
import com.example.model.TransportProtocol
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

object FingerprintGenerator {
    fun generate(
        protocol: ProxyProtocol,
        server: String,
        port: Int,
        uuid: String,
        transport: TransportProtocol,
        security: SecurityType,
        sni: String,
        path: String,
        flow: String,
        publicKey: String = ""
    ): String {
        val raw = "${protocol.name.lowercase()}://${server.lowercase()}:$port:$uuid:" +
                "${transport.value}:${security.value}:${sni.lowercase()}:$path:$flow:$publicKey"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

object V2RayUriParser {

    /**
     * Decodes subscription content which is usually Base64-encoded or plaintext list of URIs.
     */
    fun parseSubscriptionContent(content: String, subscriptionId: String? = null): List<ServerProfile> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        val decodedText = tryDecodeBase64(trimmed) ?: trimmed
        val lines = decodedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

        val profiles = mutableListOf<ServerProfile>()
        for (line in lines) {
            val profile = parseUri(line, subscriptionId)
            if (profile != null) {
                profiles.add(profile)
            }
        }
        return profiles
    }

    /**
     * Tries to decode standard or URL-safe Base64 string.
     */
    fun tryDecodeBase64(input: String): String? {
        val clean = input.replace("\r", "").replace("\n", "").trim()
        val flags = Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE
        return try {
            // Fix missing padding if needed
            val padded = when (clean.length % 4) {
                2 -> "$clean=="
                3 -> "$clean="
                else -> clean
            }
            val bytes = Base64.decode(padded, flags)
            val decoded = String(bytes, Charsets.UTF_8)
            // If the decoded text contains common scheme prefixes, it's valid base64
            if (decoded.contains("vmess://") || decoded.contains("vless://") ||
                decoded.contains("trojan://") || decoded.contains("ss://")
            ) {
                decoded
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseUri(uriStr: String, subscriptionId: String? = null): ServerProfile? {
        val str = uriStr.trim()
        return when {
            str.startsWith("vmess://", ignoreCase = true) -> parseVmess(str, subscriptionId)
            str.startsWith("vless://", ignoreCase = true) -> parseVless(str, subscriptionId)
            str.startsWith("trojan://", ignoreCase = true) -> parseTrojan(str, subscriptionId)
            str.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(str, subscriptionId)
            else -> null
        }
    }

    private fun parseVmess(uriStr: String, subscriptionId: String?): ServerProfile? {
        return try {
            val b64 = uriStr.substringAfter("vmess://", "").trim()
            val jsonStr = decodeBase64String(b64) ?: return null
            val json = JSONObject(jsonStr)

            val name = json.optString("ps", "VMess Node").trim()
            val server = json.optString("add", "").trim()
            val port = json.optInt("port", 443)
            val uuid = json.optString("id", "").trim()
            val encryption = json.optString("scy", "auto").ifEmpty { "auto" }
            val netStr = json.optString("net", "tcp").lowercase()
            val transport = when (netStr) {
                "ws" -> TransportProtocol.WS
                "grpc" -> TransportProtocol.GRPC
                "http", "h2" -> TransportProtocol.HTTP
                "quic" -> TransportProtocol.QUIC
                else -> TransportProtocol.TCP
            }
            val path = json.optString("path", "")
            val host = json.optString("host", "")
            val tlsStr = json.optString("tls", "").lowercase()
            val security = if (tlsStr == "tls") SecurityType.TLS else SecurityType.NONE
            val sni = json.optString("sni", host)
            val alpn = json.optString("alpn", "")
            val fp = json.optString("fp", "chrome")

            if (server.isEmpty() || uuid.isEmpty() || port <= 0) return null

            val fpHash = FingerprintGenerator.generate(
                protocol = ProxyProtocol.VMESS,
                server = server,
                port = port,
                uuid = uuid,
                transport = transport,
                security = security,
                sni = sni,
                path = path,
                flow = ""
            )

            ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "$server:$port" },
                protocol = ProxyProtocol.VMESS,
                server = server,
                port = port,
                uuid = uuid,
                encryption = encryption,
                transport = transport,
                path = path,
                hostHeader = host,
                sni = sni,
                security = security,
                fingerprint = fp.ifEmpty { "chrome" },
                alpn = alpn,
                subscriptionId = subscriptionId,
                fingerprintHash = fpHash
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVless(uriStr: String, subscriptionId: String?): ServerProfile? {
        return try {
            val uri = Uri.parse(uriStr)
            val uuid = uri.userInfo?.trim() ?: return null
            val server = uri.host?.trim() ?: return null
            val port = if (uri.port != -1) uri.port else 443
            val name = decodeUrlComponent(uri.fragment ?: "$server:$port")

            val typeStr = uri.getQueryParameter("type")?.lowercase() ?: "tcp"
            val transport = when (typeStr) {
                "ws" -> TransportProtocol.WS
                "grpc" -> TransportProtocol.GRPC
                "http", "h2" -> TransportProtocol.HTTP
                "quic" -> TransportProtocol.QUIC
                else -> TransportProtocol.TCP
            }

            val securityStr = uri.getQueryParameter("security")?.lowercase() ?: "none"
            val security = when (securityStr) {
                "tls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            }

            val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
            val host = uri.getQueryParameter("host") ?: ""
            val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""
            val alpn = uri.getQueryParameter("alpn") ?: ""
            val fp = uri.getQueryParameter("fp") ?: "chrome"
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val spx = uri.getQueryParameter("spx") ?: ""
            val flow = uri.getQueryParameter("flow") ?: ""
            val encryption = uri.getQueryParameter("encryption") ?: "none"

            if (server.isEmpty() || uuid.isEmpty()) return null

            val fpHash = FingerprintGenerator.generate(
                protocol = ProxyProtocol.VLESS,
                server = server,
                port = port,
                uuid = uuid,
                transport = transport,
                security = security,
                sni = sni,
                path = path,
                flow = flow,
                publicKey = pbk
            )

            ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "$server:$port" },
                protocol = ProxyProtocol.VLESS,
                server = server,
                port = port,
                uuid = uuid,
                encryption = encryption,
                transport = transport,
                path = path,
                hostHeader = host,
                sni = sni,
                security = security,
                fingerprint = fp.ifEmpty { "chrome" },
                alpn = alpn,
                publicKey = pbk,
                shortId = sid,
                spiderX = spx,
                flow = flow,
                subscriptionId = subscriptionId,
                fingerprintHash = fpHash
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojan(uriStr: String, subscriptionId: String?): ServerProfile? {
        return try {
            val uri = Uri.parse(uriStr)
            val password = uri.userInfo?.trim() ?: return null
            val server = uri.host?.trim() ?: return null
            val port = if (uri.port != -1) uri.port else 443
            val name = decodeUrlComponent(uri.fragment ?: "$server:$port")

            val typeStr = uri.getQueryParameter("type")?.lowercase() ?: "tcp"
            val transport = when (typeStr) {
                "ws" -> TransportProtocol.WS
                "grpc" -> TransportProtocol.GRPC
                else -> TransportProtocol.TCP
            }

            val securityStr = uri.getQueryParameter("security")?.lowercase() ?: "tls"
            val security = when (securityStr) {
                "reality" -> SecurityType.REALITY
                "none" -> SecurityType.NONE
                else -> SecurityType.TLS
            }

            val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
            val host = uri.getQueryParameter("host") ?: ""
            val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""
            val alpn = uri.getQueryParameter("alpn") ?: ""
            val fp = uri.getQueryParameter("fp") ?: "chrome"
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val flow = uri.getQueryParameter("flow") ?: ""

            if (server.isEmpty() || password.isEmpty()) return null

            val fpHash = FingerprintGenerator.generate(
                protocol = ProxyProtocol.TROJAN,
                server = server,
                port = port,
                uuid = password,
                transport = transport,
                security = security,
                sni = sni,
                path = path,
                flow = flow,
                publicKey = pbk
            )

            ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "$server:$port" },
                protocol = ProxyProtocol.TROJAN,
                server = server,
                port = port,
                uuid = password,
                encryption = "none",
                transport = transport,
                path = path,
                hostHeader = host,
                sni = sni,
                security = security,
                fingerprint = fp.ifEmpty { "chrome" },
                alpn = alpn,
                publicKey = pbk,
                shortId = sid,
                flow = flow,
                subscriptionId = subscriptionId,
                fingerprintHash = fpHash
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(uriStr: String, subscriptionId: String?): ServerProfile? {
        return try {
            val uri = Uri.parse(uriStr)
            val fragmentName = decodeUrlComponent(uri.fragment ?: "")

            // Case 1: ss://BASE64(method:password@host:port)#name
            // Case 2: ss://BASE64(method:password)@host:port#name
            val schemePart = uriStr.substringAfter("ss://", "").substringBefore("#")
            var method = "chacha20-poly1305"
            var password = ""
            var server = ""
            var port = 8388

            if (schemePart.contains("@")) {
                val userPart = schemePart.substringBefore("@")
                val hostPart = schemePart.substringAfter("@")
                val decodedUserInfo = decodeBase64String(userPart) ?: userPart
                if (decodedUserInfo.contains(":")) {
                    method = decodedUserInfo.substringBefore(":")
                    password = decodedUserInfo.substringAfter(":")
                } else {
                    password = decodedUserInfo
                }
                server = hostPart.substringBefore(":")
                port = hostPart.substringAfter(":", "8388").toIntOrNull() ?: 8388
            } else {
                val decoded = decodeBase64String(schemePart)
                if (decoded != null && decoded.contains("@")) {
                    val userPart = decoded.substringBefore("@")
                    val hostPart = decoded.substringAfter("@")
                    if (userPart.contains(":")) {
                        method = userPart.substringBefore(":")
                        password = userPart.substringAfter(":")
                    } else {
                        password = userPart
                    }
                    server = hostPart.substringBefore(":")
                    port = hostPart.substringAfter(":", "8388").toIntOrNull() ?: 8388
                }
            }

            if (server.isEmpty() || password.isEmpty()) return null

            val name = fragmentName.ifEmpty { "$server:$port" }
            val fpHash = FingerprintGenerator.generate(
                protocol = ProxyProtocol.SHADOWSOCKS,
                server = server,
                port = port,
                uuid = password,
                transport = TransportProtocol.TCP,
                security = SecurityType.NONE,
                sni = "",
                path = "",
                flow = ""
            )

            ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.SHADOWSOCKS,
                server = server,
                port = port,
                uuid = password,
                encryption = method,
                transport = TransportProtocol.TCP,
                subscriptionId = subscriptionId,
                fingerprintHash = fpHash
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts a ServerProfile back to a shareable V2Ray/Xray URI.
     */
    fun toUri(profile: ServerProfile): String {
        return when (profile.protocol) {
            ProxyProtocol.VMESS -> {
                val json = JSONObject().apply {
                    put("v", "2")
                    put("ps", profile.name)
                    put("add", profile.server)
                    put("port", profile.port)
                    put("id", profile.uuid)
                    put("aid", 0)
                    put("scy", profile.encryption)
                    put("net", profile.transport.value)
                    put("type", "none")
                    put("host", profile.hostHeader)
                    put("path", profile.path)
                    put("tls", if (profile.security == SecurityType.TLS) "tls" else "")
                    put("sni", profile.sni)
                    put("alpn", profile.alpn)
                    put("fp", profile.fingerprint)
                }
                val b64 = Base64.encodeToString(
                    json.toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                "vmess://$b64"
            }
            ProxyProtocol.VLESS -> {
                val query = mutableListOf<String>()
                query.add("type=${profile.transport.value}")
                query.add("security=${profile.security.value}")
                if (profile.sni.isNotEmpty()) query.add("sni=${encodeUrlComponent(profile.sni)}")
                if (profile.hostHeader.isNotEmpty()) query.add("host=${encodeUrlComponent(profile.hostHeader)}")
                if (profile.path.isNotEmpty()) query.add("path=${encodeUrlComponent(profile.path)}")
                if (profile.fingerprint.isNotEmpty()) query.add("fp=${profile.fingerprint}")
                if (profile.publicKey.isNotEmpty()) query.add("pbk=${profile.publicKey}")
                if (profile.shortId.isNotEmpty()) query.add("sid=${profile.shortId}")
                if (profile.spiderX.isNotEmpty()) query.add("spx=${encodeUrlComponent(profile.spiderX)}")
                if (profile.flow.isNotEmpty()) query.add("flow=${profile.flow}")
                if (profile.alpn.isNotEmpty()) query.add("alpn=${encodeUrlComponent(profile.alpn)}")

                val qStr = if (query.isNotEmpty()) "?${query.joinToString("&")}" else ""
                "vless://${profile.uuid}@${profile.server}:${profile.port}$qStr#${encodeUrlComponent(profile.name)}"
            }
            ProxyProtocol.TROJAN -> {
                val query = mutableListOf<String>()
                query.add("type=${profile.transport.value}")
                query.add("security=${profile.security.value}")
                if (profile.sni.isNotEmpty()) query.add("sni=${encodeUrlComponent(profile.sni)}")
                if (profile.path.isNotEmpty()) query.add("path=${encodeUrlComponent(profile.path)}")
                if (profile.fingerprint.isNotEmpty()) query.add("fp=${profile.fingerprint}")
                if (profile.alpn.isNotEmpty()) query.add("alpn=${encodeUrlComponent(profile.alpn)}")
                val qStr = if (query.isNotEmpty()) "?${query.joinToString("&")}" else ""
                "trojan://${profile.uuid}@${profile.server}:${profile.port}$qStr#${encodeUrlComponent(profile.name)}"
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val userinfo = Base64.encodeToString(
                    "${profile.encryption}:${profile.uuid}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                "ss://$userinfo@${profile.server}:${profile.port}#${encodeUrlComponent(profile.name)}"
            }
        }
    }

    private fun decodeBase64String(str: String): String? {
        val clean = str.replace("\r", "").replace("\n", "").trim()
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return try {
            val bytes = Base64.decode(padded, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUrlComponent(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (_: UnsupportedEncodingException) {
            str
        }
    }

    private fun encodeUrlComponent(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: UnsupportedEncodingException) {
            str
        }
    }
}
