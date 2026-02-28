# WireWhisper Development Journey

How a privacy-focused Android network monitor went from idea to working firewall tool across five sessions, built entirely through AI pair programming with Claude Code.

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

Claude scaffolded the entire project in one pass: **37 Kotlin source files, ~4,200 lines of code.** The architecture follows a clear pipeline:

```
TUN Interface → PacketParser → FlowTracker → UidResolver → Repository → UI
```

The most complex piece is `TunProcessor.kt` which handles raw IP packet reading, UDP relay via NIO DatagramChannel with Selector, and TCP proxy via SocketChannel with a simplified state machine.

---

## Session 2: DNS/SNI + Little Snitch UI (Feb 28, ~8:00 PM)

### Prompt 2: "Make it look like Little Snitch"

Transformed the flat IP address list into a grouped network monitor:

1. **DNS parsing** — intercept DNS responses to map IPs to domains
2. **TLS SNI extraction** — read Server Name Indication from TLS ClientHello
3. **App-grouped UI** — app icons, hostnames, traffic sparklines, expandable rows

Result: Working Little Snitch-style monitor with real domain names, app attribution, and animated sparklines.

---

## Session 3: TCP Relay Fixes (Feb 28, ~11:00 PM)

### Prompt 3: "Chrome is hanging"

Five critical TCP relay bugs surfaced during real-world testing:

1. **TCP checksum** — mandatory for TUN packets, unlike UDP where checksum=0 is valid
2. **SYN sequence extraction** — wasn't reading client's initial sequence number from raw packet
3. **SYN-ACK advance** — SYN consumes 1 byte of sequence space
4. **Async writes** — blocking writes were stalling the TUN read loop
5. **IPv6 header length** — operator precedence bug in bitwise expression

Also added ~120 unit tests across 13 test files.

---

## Session 4: Offline Geolocation (Mar 1, ~12:00 AM)

### Prompt 4: "Stop leaking browsing data to ip-api.com"

Replaced the online API with a three-tier offline system:

1. **In-memory ConcurrentHashMap** — fastest, covers recently seen IPs
2. **Room database** — survives app restarts, 30-day TTL
3. **Offline binary database** — binary-search IP range lookup from asset file

Zero-latency country resolution with no network calls during normal operation.

---

## Session 5: Now Screen Features (Mar 1)

### Prompt 5: "Traffic graph, blocking, activity sort"

Three features that move WireWhisper from passive monitor to actionable firewall:

**Traffic Graph Bottom Sheet** — Tap any sparkline to see a bidirectional 60-second bar chart (pink=sent above, cyan=received below center line). Auto-refreshes every second.

**App & Hostname Blocking** — Green checkmark / red X toggle on each app and hostname row. Backed by Room persistence, loaded at startup. `BlockingEngine` provides O(1) in-memory lookups. Blocked packets are silently dropped in `TunProcessor.processPacket()`.

**Activity-Based Sorting** — Most recently active apps float to top. Sorting pauses during user interaction (scrolling, tapping) and resumes after 3 seconds idle. `animateItem()` provides smooth reordering. Toggle between "Recent" and "Bytes" sort modes.

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **VpnService, not root** | Works on any stock Android device |
| **Manual DI** | App has ~15 singletons. Hilt adds complexity without benefit at this scale |
| **ConcurrentHashMap for flows** | Per-key locking with `compute()` — no global lock contention |
| **500ms StateFlow debounce** | Prevents UI thrashing from high packet rates |
| **LRU hostname cache** | 4000 entries covers typical device usage; TTL respects DNS records |
| **Ring buffer sparklines** | O(1) insert, O(n) snapshot, fixed memory per UID |
| **Three-tier geo cache** | Memory → Room → offline binary DB; zero latency, zero network |
| **In-memory blocking sets** | O(1) hot-path check; Room for persistence; StateFlows for reactive UI |
| **Blocking TUN reads** | Single IO thread, no polling/busy-wait, minimal battery impact |

## Project Stats

| Metric | Value |
|--------|-------|
| Source files | ~50 Kotlin |
| Lines of code | ~7,500 |
| Test files | 14 |
| Unit tests | ~170 |
| Dependencies | Room, Navigation Compose, Coroutines, Material3, WorkManager |
| Min SDK | 36 (Android 16) |
| Development sessions | 5 |
| Git commits | 11 |

## Phase Status

- **Phase 1 (MVP)**: Complete — VPN capture, packet parsing, flow tracking, app attribution
- **Phase 2 (Analysis)**: Complete — DNS/SNI hostnames, offline geo, traffic charts, country grouping
- **Phase 3 (Firewall)**: In progress — app/hostname blocking implemented, rule persistence working
