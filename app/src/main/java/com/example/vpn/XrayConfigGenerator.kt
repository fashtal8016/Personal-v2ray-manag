package com.example.vpn

import com.example.model.ProxyProtocol
import com.example.model.SecurityType
import com.example.model.ServerProfile
import com.example.model.TransportProtocol
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigGenerator {

    fun generateConfig(
        profile: ServerProfile,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        bypassLan: Boolean = true
    ): String {
        val root = JSONObject()

        // 1. Log
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", log)

        // 2. Inbounds (Local SOCKS & HTTP)
        val inbounds = JSONArray()
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", socksPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                })
            })
        }
        inbounds.put(socksInbound)

        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", httpPort)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject())
        }
        inbounds.put(httpInbound)
        root.put("inbounds", inbounds)

        // 3. Outbounds (Primary Proxy + Direct + Block)
        val outbounds = JSONArray()
        val primaryOutbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", profile.protocol.name.lowercase())

            // Outbound Settings according to protocol
            val settings = JSONObject()
            when (profile.protocol) {
                ProxyProtocol.VMESS -> {
                    val vnext = JSONArray()
                    val target = JSONObject().apply {
                        put("address", profile.server)
                        put("port", profile.port)
                        val users = JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", profile.uuid)
                                put("alterId", 0)
                                put("security", profile.encryption.ifEmpty { "auto" })
                            })
                        }
                        put("users", users)
                    }
                    vnext.put(target)
                    settings.put("vnext", vnext)
                }
                ProxyProtocol.VLESS -> {
                    val vnext = JSONArray()
                    val target = JSONObject().apply {
                        put("address", profile.server)
                        put("port", profile.port)
                        val users = JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", profile.uuid)
                                put("encryption", "none")
                                if (profile.flow.isNotEmpty()) {
                                    put("flow", profile.flow)
                                }
                            })
                        }
                        put("users", users)
                    }
                    vnext.put(target)
                    settings.put("vnext", vnext)
                }
                ProxyProtocol.TROJAN -> {
                    val servers = JSONArray()
                    servers.put(JSONObject().apply {
                        put("address", profile.server)
                        put("port", profile.port)
                        put("password", profile.uuid)
                    })
                    settings.put("servers", servers)
                }
                ProxyProtocol.SHADOWSOCKS -> {
                    val servers = JSONArray()
                    servers.put(JSONObject().apply {
                        put("address", profile.server)
                        put("port", profile.port)
                        put("method", profile.encryption.ifEmpty { "chacha20-poly1305" })
                        put("password", profile.uuid)
                    })
                    settings.put("servers", servers)
                }
            }
            put("settings", settings)

            // Stream Settings (transport, tls/reality, headers)
            val streamSettings = JSONObject()
            streamSettings.put("network", profile.transport.value)

            when (profile.security) {
                SecurityType.TLS -> {
                    streamSettings.put("security", "tls")
                    val tlsObj = JSONObject().apply {
                        put("serverName", profile.sni.ifEmpty { profile.server })
                        put("fingerprint", profile.fingerprint.ifEmpty { "chrome" })
                        if (profile.alpn.isNotEmpty()) {
                            val alpnList = JSONArray()
                            profile.alpn.split(",").map { it.trim() }.forEach { alpnList.put(it) }
                            put("alpn", alpnList)
                        }
                    }
                    streamSettings.put("tlsSettings", tlsObj)
                }
                SecurityType.REALITY -> {
                    streamSettings.put("security", "reality")
                    val realityObj = JSONObject().apply {
                        put("show", false)
                        put("fingerprint", profile.fingerprint.ifEmpty { "chrome" })
                        put("serverName", profile.sni.ifEmpty { profile.server })
                        put("publicKey", profile.publicKey)
                        put("shortId", profile.shortId)
                        put("spiderX", profile.spiderX)
                    }
                    streamSettings.put("realitySettings", realityObj)
                }
                SecurityType.NONE -> {
                    streamSettings.put("security", "none")
                }
            }

            // Transport-specific stream parameters
            when (profile.transport) {
                TransportProtocol.WS -> {
                    val wsObj = JSONObject().apply {
                        put("path", profile.path.ifEmpty { "/" })
                        if (profile.hostHeader.isNotEmpty()) {
                            put("headers", JSONObject().apply {
                                put("Host", profile.hostHeader)
                            })
                        }
                    }
                    streamSettings.put("wsSettings", wsObj)
                }
                TransportProtocol.GRPC -> {
                    val grpcObj = JSONObject().apply {
                        put("serviceName", profile.path)
                        put("multiMode", true)
                    }
                    streamSettings.put("grpcSettings", grpcObj)
                }
                TransportProtocol.HTTP -> {
                    val httpObj = JSONObject().apply {
                        put("path", profile.path.ifEmpty { "/" })
                        if (profile.hostHeader.isNotEmpty()) {
                            put("host", JSONArray().apply { put(profile.hostHeader) })
                        }
                    }
                    streamSettings.put("httpSettings", httpObj)
                }
                else -> {
                    // TCP
                }
            }

            put("streamSettings", streamSettings)
        }
        outbounds.put(primaryOutbound)

        // Freedom (Direct)
        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject())
        }
        outbounds.put(directOutbound)

        // Blackhole (Block)
        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject())
        }
        outbounds.put(blockOutbound)

        root.put("outbounds", outbounds)

        // 4. Routing
        val routing = JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            val rules = JSONArray()

            if (bypassLan) {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                    })
                })
            }

            // Route all other traffic to proxy
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "proxy")
                put("network", "tcp,udp")
            })

            put("rules", rules)
        }
        root.put("routing", routing)

        return root.toString(2)
    }
}
