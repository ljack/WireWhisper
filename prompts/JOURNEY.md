# WireWhisper Development Journey

How a privacy-focused Android network monitor went from idea to working app in one evening, built entirely through AI pair programming with Claude Code.

## The Idea

**"I want a Little Snitch for Android — no root, no sideloading sketchy APKs, just a clean network monitor that shows me what my apps are doing."**

Android lacks a built-in way to see which apps are phoning home and where. iOS has Screen Time network data, macOS has Little Snitch. Stock Android? Nothing. WireWhisper fills that gap using Android's VpnService API to capture all traffic without root access.

---

## Session 1: The MVP Sprint (Feb 28, ~7:00 PM)

### Prompt 1: "Build the entire app"

The first prompt was essentially the full app specification — a privacy-focused network monitor using VpnService, with packet parsing, flow tracking, app attribution, and a Compose UI. The key constraints:

- **No root** — must work on stock Android using VpnService
- **No MITM** — metadata only, never decrypt traffic
- **Manual DI** — no Hilt, keep it simple
- **Android 16+ only** — target SDK 36, leverage modern APIs

Claude scaffolded the entire project in one pass:

```
f4ed3ce Initial project scaffold: Gradle build system, Android manifest, resources
7322032 Add core domain models: Protocol, FlowKey, FlowRecord, AppInfo, PacketInfo
438cac8 Add packet parsing: IPv4/IPv6 headers, TCP/UDP, PacketParser pipeline
38b983b Add flow tracking, UID resolution, and geo resolution
734a709 Add data layer: Room database, FlowEntity, FlowDao, FlowRepository
abcf96b Add VPN service and TUN processor for traffic capture and relay
ece4a9a Add Compose UI: theme, navigation, screens, app entry points
1c4ac5d Add architecture documentation and IDE configuration
```

**37 Kotlin source files, ~4,200 lines of code.** The architecture follows a clear pipeline:

```
TUN Interface → PacketParser → FlowTracker → UidResolver → Repository → UI
```

The most complex piece is `TunProcessor.kt` (~450 lines) which handles:
- Reading raw IP packets from the TUN file descriptor
- UDP relay via NIO DatagramChannel with Selector
- TCP proxy via SocketChannel with a simplified state machine
- Protected sockets to prevent VPN routing loops

### Bug: KSP vs AGP 9.x

First build failed — KSP's source set registration conflicts with AGP 9.0.1's built-in Kotlin support. Fixed with `android.disallowKotlinSourceSets=false` in gradle.properties.

### Bug: Invalid IPv6 address

VPN wouldn't start. The TUN interface was configured with `fd00:db8:wire::1` — "wire" isn't valid hex. Changed to `fd00:db8:1::1`.

### First successful run

VPN activated, TUN interface established, packets flowing through. The basic monitoring UI showed live flows with raw IP addresses. Working, but ugly — just a flat list of connections with no app names, no hostnames, no visual hierarchy.

---

## Session 2: DNS/SNI + Little Snitch UI (Feb 28, ~8:00 PM)

### Prompt 2: "Make it look like Little Snitch"

The flat IP address list needed to become something useful. The plan:

1. **DNS parsing** — intercept DNS responses to learn which IPs map to which domains
2. **TLS SNI extraction** — read the Server Name Indication from TLS ClientHello (no decryption needed)
3. **App-grouped UI** — group connections by app, then by hostname, with traffic sparklines

Claude created a detailed 10-step implementation plan, exploring the existing codebase to find exact integration points:

- Three surgical hooks in TunProcessor: outgoing DNS queries, incoming DNS responses, first TLS data packet on port 443
- LRU cache for IP→hostname with TTL expiry
- Circular ring buffers for per-second traffic sampling
- Compose Canvas sparklines

```
4ffe581 Add DNS/SNI hostname resolution for IP-to-domain mapping
f174370 Add Little Snitch-style UI with app grouping, sparklines, hostname drill-down
```

### Bug: All flows showed "UID -1"

First deploy of the new UI showed hostnames (DNS resolution worked!) but every flow was grouped under "UID -1". The UidResolver had two methods: `resolve()` (no-op that resolved but discarded results) and `resolveAndEnrich()` (actually updates flows). TunProcessor was calling the wrong one.

### Bug: No sparklines

TrafficSampler had `if (uid < 0) return` — UID -1 flows got no sparkline data. Removed the guard so all traffic gets sampled.

### Final result

App icons, app names, expandable hostname groups, animated sparklines, DNS hostnames instead of raw IPs. Working Little Snitch-style network monitor.

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **VpnService, not root** | Works on any stock Android device |
| **Manual DI** | App has ~10 singletons. Hilt adds complexity without benefit at this scale |
| **ConcurrentHashMap for flows** | Per-key locking with `compute()` — no global lock contention |
| **500ms StateFlow debounce** | Prevents UI thrashing from high packet rates |
| **LRU hostname cache** | 4000 entries covers typical device usage; TTL respects DNS records |
| **Ring buffer sparklines** | O(1) insert, O(n) snapshot, fixed memory per UID |
| **No accompanist** | Drawable→Bitmap→ImageBitmap via `core-ktx` avoids extra dependency |
| **Blocking TUN reads** | Single IO thread, no polling/busy-wait, minimal battery impact |

## What's Next

- **Phase 2 remaining**: HTTP Host header parsing, embedded GeoIP database, export functionality
- **Phase 3**: Firewall rules engine — block by app, domain, country, port

---

## Stats

| Metric | Value |
|--------|-------|
| Source files | 37 Kotlin |
| Lines of code | ~5,000 |
| Dependencies | Room, Navigation Compose, Coroutines, Material3 |
| Build time | ~5 seconds (incremental) |
| Min SDK | 36 (Android 16) |
| Development time | ~2 hours across 2 sessions |
| Git commits | 10 |
