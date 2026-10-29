package com.example.model

enum class ProxyProtocol(val displayName: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks")
}

enum class TransportProtocol(val value: String) {
    TCP("tcp"),
    WS("ws"),
    GRPC("grpc"),
    HTTP("http"),
    QUIC("quic")
}

enum class SecurityType(val value: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality")
}

enum class VpnState(val displayName: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    DISCONNECTING("Disconnecting"),
    ERROR("Error")
}

enum class TestStage(val displayName: String) {
    DNS("DNS Lookup"),
    TCP("TCP Handshake"),
    TLS("TLS Handshake"),
    HTTP("HTTP Probe"),
    THROUGHPUT("Throughput Test"),
    COMPLETE("Completed"),
    FAILED("Failed")
}
