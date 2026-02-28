# WireWhisper Architecture

Privacy-focused network monitor for stock Android (no root).
Uses VpnService to capture all device traffic, extract metadata,
and attribute connections to apps.

## High-Level Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│  Android Device                                                 │
│                                                                 │
│  App A ──┐                                                      │
│  App B ──┼── Network traffic ──► TUN interface ◄── VpnService   │
│  App C ──┘                           │                          │
│                                      ▼                          │
│                              ┌───────────────┐                  │
│                              │  TunProcessor  │                 │
│                              └───┬───────┬───┘                  │
│                       ┌──────────┘       └──────────┐           │
│                       ▼                              ▼          │
│              ┌─────────────┐               ┌──────────────┐     │
│              │ PacketParser │               │  PacketRelay │     │
│              │ (IPv4/IPv6)  │               │ (UDP/TCP)    │     │
│              └──────┬──────┘               └──────┬───────┘     │
│                     │                             │             │
│                     ▼                             ▼             │
│              ┌─────────────┐               Protected sockets    │
│              │ FlowTracker  │               to actual servers    │
│              │ (5-tuple agg)│                                    │
│              └──┬───────┬──┘                                    │
│       ┌─────────┘       └─────────┐                             │
│       ▼                           ▼                             │
│ ┌───────────┐             ┌─────────────┐                       │
│ │UidResolver│             │ GeoResolver  │                       │
│ │UID→pkg    │             │ IP→country   │                       │
│ └─────┬─────┘             └──────┬──────┘                       │
│       └───────────┬──────────────┘                              │
│                   ▼                                             │
│           ┌──────────────┐                                      │
│           │FlowRepository│                                      │
│           │  (Room DB)   │                                      │
│           └──────┬───────┘                                      │
│                  ▼                                              │
│           ┌──────────────┐                                      │
│           │  Compose UI   │                                     │
│           │ Now│History│  │                                     │
│           │ Detail│Settngs│                                     │
│           └──────────────┘                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
com.wirewhisper/
├── WireWhisperApp.kt          Application class, manual DI
├── MainActivity.kt            Single-activity host
│
├── core/model/                Domain models
│   ├── Protocol.kt            TCP/UDP/ICMP enum
│   ├── FlowKey.kt             5-tuple connection identifier
│   ├── FlowRecord.kt          Aggregated flow stats (mutable)
│   ├── AppInfo.kt             UID → package/app name
│   └── PacketInfo.kt          Single-packet metadata + TcpFlags
│
├── packet/                    Raw packet parsing
│   ├── Ip4Header.kt           IPv4 header parser
│   ├── Ip6Header.kt           IPv6 header + extension header walking
│   ├── TcpHeader.kt           TCP header parser
│   ├── UdpHeader.kt           UDP header parser
│   └── PacketParser.kt        Top-level parser: ByteBuffer → PacketInfo
│
├── vpn/                       VPN and packet relay
│   ├── WireWhisperVpnService  VPN lifecycle, foreground service, TUN setup
│   └── TunProcessor.kt        Read loop, packet processing, UDP/TCP relay
│
├── flow/                      Flow tracking and app attribution
│   ├── FlowTracker.kt         ConcurrentHashMap<FlowKey, FlowRecord>, batched flush
│   └── UidResolver.kt         ConnectivityManager.getConnectionOwnerUid + /proc fallback
│
├── geo/                       Geographic resolution
│   └── GeoResolver.kt         Interface + InMemoryGeoResolver (online API + cache)
│
├── data/                      Persistence
│   ├── db/
│   │   ├── FlowEntity.kt      Room entity with indexes
│   │   ├── FlowDao.kt         Queries: filtered, by-app, by-country, by-time
│   │   └── AppDatabase.kt     Room database singleton
│   └── repository/
│       └── FlowRepository.kt  Interface + RoomFlowRepository + InMemoryFlowRepository
│
└── ui/                        Compose UI
    ├── navigation/             Type-safe Navigation (kotlinx.serialization routes)
    ├── now/                    Live flows screen + ViewModel
    ├── history/                Stored flows with filters + ViewModel
    ├── detail/                 Single-flow detail screen + ViewModel
    ├── settings/               Settings screen + ViewModel
    └── theme/                  Material 3 color scheme
```

## Module Boundaries (Future Gradle Modules)

The package structure is designed for easy extraction into Gradle modules:

| Future Module | Current Package        | Dependencies |
|---------------|------------------------|--------------|
| `:core`       | `core.model`           | (none)       |
| `:packet`     | `packet`               | `:core`      |
| `:vpn`        | `vpn`                  | `:core`, `:packet`, `:flow` |
| `:flow`       | `flow`                 | `:core`, `:data` |
| `:geo`        | `geo`                  | `:core`      |
| `:data`       | `data`                 | `:core`      |
| `:ui`         | `ui`                   | `:core`, `:flow`, `:data` |
| `:rules`      | (Phase 3)              | `:core`, `:packet` |

## Battery & Performance Strategy

### Packet Processing
- **No per-packet DB writes**: FlowTracker accumulates in ConcurrentHashMap
- **Batch flush**: Timed-out flows flushed to Room every 30s cleanup cycle
- **Session flush**: All active flows persisted when VPN stops

### UI Updates
- **StateFlow with debounce**: FlowTracker snapshots active flows at most every 500ms
- **LazyColumn with stable keys**: FlowKey.hashCode() as list key prevents unnecessary recomposition
- **collectAsStateWithLifecycle**: Stops collection when UI not visible

### VPN Service
- **Foreground service**: START_STICKY for continuous mode
- **Blocking TUN reads**: Single IO thread, no polling/busy-wait
- **Protected sockets**: Relay channels excluded from VPN routing to prevent loops

### Memory
- Active flows: ~200 bytes per flow × typical 50-500 active flows = ~100 KB
- UDP channels: NIO DatagramChannels are lightweight
- TCP sessions: One SocketChannel per active TCP connection
- GeoIP cache: LRU cache of 2000 entries

## UID Resolution

### Primary: ConnectivityManager.getConnectionOwnerUid() (API 29+)
- Works because our app IS the active VPN
- Most reliable method on stock Android
- Parameters: protocol (TCP/UDP), local InetSocketAddress, remote InetSocketAddress

### Fallback: /proc/net/tcp6, /proc/net/udp6
- Parses kernel socket table matching local port → UID
- Subject to timing issues (connection may vanish before read)
- IPv6 files also contain IPv4-mapped entries (::ffff:x.x.x.x)

### Known Limitations
- **Short-lived connections**: May close before UID lookup completes
- **Shared UIDs**: android:sharedUserId causes multiple packages per UID
- **System processes**: UID 0 (root), 1000 (system) lack meaningful package names
- **QUIC**: UDP-based, very short-lived socket table entries
- **Multi-user profiles**: UIDs are offset by userId × 100000

## GeoIP Resolution

### MVP: InMemoryGeoResolver
- Built-in classification: loopback, link-local, private ranges → "LAN"
- Optional online lookup via ip-api.com (user toggle, privacy concern)
- LRU cache of 2000 entries

### Production Upgrade Path
- Embed MaxMind GeoLite2 Country database (~5 MB .mmdb file)
- Use `com.maxmind.geoip2:geoip2` library
- Zero network requests, fully on-device
- Update DB monthly via app update or background download

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Required for protected relay sockets |
| `FOREGROUND_SERVICE` | Keep VPN service running |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for API 34+ with specialUse type |
| `POST_NOTIFICATIONS` | Show foreground service notification (API 33+) |
| `QUERY_ALL_PACKAGES` | Resolve UID → package name for any installed app |
| `BIND_VPN_SERVICE` (service permission) | System-only binding to our VPN service |

VPN consent is requested at runtime via `VpnService.prepare()`.

## Known Android Limitations

1. **Single VPN**: Only one VPN can be active. WireWhisper conflicts with
   commercial VPNs, other firewalls, and work profiles with always-on VPN.

2. **VPN icon**: System shows a persistent key icon in the status bar.
   Users may find this confusing.

3. **Battery usage attribution**: Android may attribute battery usage
   of all apps' network traffic to WireWhisper since it proxies everything.

4. **TCP complexity**: User-space TCP relay must handle state machine,
   sequence numbers, retransmission (simplified in MVP). Production-quality
   relay requires significant hardening.

5. **DNS-over-HTTPS/TLS**: Apps using private DNS bypass our DNS interception.
   We still see the IP-level flows but can't extract hostnames.

6. **IPv6 privacy extensions**: Temporary IPv6 addresses rotate, so the same
   device may appear with different source addresses over time.

7. **Always-on VPN**: If another app is configured as always-on VPN in system
   settings, our VPN cannot start.

8. **Restricted /proc access**: Future Android versions may further restrict
   /proc/net access, making the UID fallback path unreliable.

## Phase 2 Backlog (Prioritized)

1. **DNS hostname caching**: Intercept DNS responses (port 53) to build
   IP → hostname mapping. Essential for meaningful UI display.

2. **TLS SNI extraction**: Parse TLS ClientHello to extract server name
   indication. No MITM required, just read the unencrypted handshake.

3. **HTTP header parsing**: For plaintext HTTP only (port 80), extract
   Host, User-Agent, Content-Type. No MITM.

4. **Embedded GeoIP database**: Replace online lookup with MaxMind GeoLite2.

5. **Debug session mode**: Time-limited capture (e.g., 5 minutes) with
   automatic stop and summary report.

6. **Per-app traffic stats dashboard**: Aggregate bytes/connections per app,
   sortable by total traffic.

7. **Export functionality**: Export flow logs as CSV/JSON for analysis.

8. **Better TCP relay**: Handle retransmission, window scaling, SACK,
   TCP Fast Open. Consider using lwIP for production quality.

9. **QUIC/UDP attribution**: Improve UID resolution timing for short-lived
   UDP connections.

10. **IPv6 edge cases**: Handle IPv6 extension header chains more robustly,
    handle dual-stack happy eyeballs.

## Phase 3 Backlog (Firewall)

1. **Rules engine**: `data class Rule(action: Action, matcher: Matcher)`
   where matchers can be: ByApp, ByCountry, ByPort, ByProtocol, ByDomain.

2. **Packet decision point**: Insert between PacketParser and PacketRelay.
   Check rules, drop or allow. Return ICMP unreachable for blocked TCP.

3. **Rule persistence**: Room entity for rules, import/export.

4. **Rule UI**: List of rules with drag-to-reorder priority, quick toggles.

5. **Per-country blocking**: "Block all traffic to RU/CN" as a first-class
   feature. Requires reliable GeoIP.

6. **Notification on new app**: Alert when a newly-installed app makes
   its first network connection.
