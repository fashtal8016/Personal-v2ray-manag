# Personal V2Ray Manager

A high-performance native Android VPN application built with Kotlin, Jetpack Compose, Android `VpnService`, and verified Xray-core protocol compatibility.

## Key Features

- **Multi-Subscription Management**: Add, edit, enable/disable, and delete multiple V2Ray/Xray subscription sources. Safe failure handling retains previous configurations if a refresh fails.
- **Universal Protocol Parser**: Supports `VMess` (Base64 JSON), `VLESS` (including REALITY, uTLS fingerprints, and XTLS Vision flow), `Trojan`, and `Shadowsocks` (SIP002 standard format), as well as Base64-encoded subscription feeds.
- **Deduplication Engine**: Uses deterministic SHA-256 fingerprinting on node configurations (protocol, server, port, credentials, transport, security, SNI, path) to deduplicate redundant nodes across multiple subscriptions while preserving user favorites and historical stats.
- **Multi-Stage Network Testing Engine**:
  - DNS resolution time measurement
  - Direct TCP connection handshake latency
  - TLS handshake and SNI validation
  - Real HTTP latency probing
  - Download throughput transfer rate measurement (Bps)
  - No synthetic or fabricated test data
- **Deterministic Ranking System**:
  - Reliability: 40%
  - Latency: 25% (normalized inverted)
  - Download Throughput: 25% (normalized)
  - Latency Consistency / Jitter: 10% (lower variation is better)
- **Automatic "main sub" Group**:
  - Application-managed persistent group named `"main sub"`.
  - Automatically selects and maintains the **top 50** eligible working configurations.
  - If fewer than 50 qualify, only qualifying nodes are selected (never padded with failed or untested nodes).
  - Atomic database transactions ensure safe membership updates without wiping valid configurations on failure.
- **Native Android VpnService & TUN Engine**:
  - Full Android system VPN integration via `VpnService`.
  - Protects upstream sockets (`protect(socket)`) to prevent routing loops.
  - Live session uptime timer and real-time upload/download traffic rate meters.
  - Foreground service with notification and one-tap disconnect.
  - Full Xray-core compliant JSON configuration generator and inspector.
- **Privacy & Security**:
  - Secret redaction for UUIDs, passwords, and tokens in connection logs and diagnostics.
  - English-only user interface.
  - Dark-mode cybernetic Material 3 interface.

## Architecture

```
com.example/
├── MainActivity.kt               // Entry activity with Compose content & edge-to-edge
├── model/
│   ├── Enums.kt                  // ProxyProtocol, TransportProtocol, SecurityType, VpnState
│   └── Entities.kt               // Room entities: ServerProfile, Subscription, Group, Stats, Session
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt        // Room database singleton with type converters
│   │   └── Daos.kt               // DAOs for reactive Flow queries and atomic transactions
│   ├── parser/
│   │   └── V2RayUriParser.kt     // VMess, VLESS, Trojan, SS, and Base64 subscription parser
│   ├── testing/
│   │   ├── NetworkTester.kt      // Real multi-stage network latency & throughput probe
│   │   ├── RankingEngine.kt      // 4-factor scoring algorithm & top-50 selection
│   │   └── MainSubManager.kt     // End-to-end sync pipeline for 'main sub'
│   └── repository/
│       └── Repositories.kt       // ServerRepository and SubscriptionRepository
├── vpn/
│   ├── V2RayVpnService.kt        // Native Android VpnService & Foreground notification
│   ├── VpnController.kt          // Central singleton controlling VPN lifecycle & live stats
│   ├── TunPacketRouter.kt        // TUN interface loop, socket protection, traffic counters
│   └── XrayConfigGenerator.kt    // Compliant Xray-core JSON configuration generator
├── ui/
│   ├── MainViewModel.kt          // UI state, server filtering, sorting, and action dispatch
│   ├── MainScreen.kt             // Top-level scaffold with M3 NavigationBar
│   ├── components/               // Badges, latency chips, and server item cards
│   ├── screens/                  // Dashboard, Servers, Subscriptions, Testing, Settings, Inspector
│   └── theme/                    // Modern cybernetic Material 3 theme & typography
└── util/
    ├── FormatUtils.kt            // Bytes, speed, duration, and timestamp formatting
    └── LogManager.kt             // Redacted logging and diagnostics
```

## Supported Protocols & Transports

| Protocol | Transports | Security | Additional Features |
|---|---|---|---|
| **VLESS** | TCP, WebSocket, gRPC, HTTP | None, TLS, REALITY | XTLS Vision (`flow=xtls-rprx-vision`), uTLS fingerprints (`fp=chrome/firefox/safari`), SNI, ALPN |
| **VMess** | TCP, WebSocket, gRPC, HTTP | None, TLS | Base64 JSON format, host header, path, cipher auto/aes-128-gcm/chacha20-poly1305 |
| **Trojan** | TCP, WebSocket, gRPC | TLS, REALITY | SNI, ALPN, path, uTLS fingerprints |
| **Shadowsocks** | TCP | AEAD ciphers | SIP002 URI format, chacha20-poly1305, aes-256-gcm |

## Building and Testing

To compile the Android app:
```bash
gradle assembleDebug
```

To run unit and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```
