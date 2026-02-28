# Prompt 1: MVP Scaffold

**Session**: 1 (Feb 28, 2026 ~7:00 PM)
**Commits**: f4ed3ce through 1c4ac5d (8 commits)

## Prompt Summary

Build a privacy-focused network monitor for stock Android (no root) using VpnService. The app should:

- Capture all device traffic via a TUN interface
- Parse IPv4/IPv6, TCP/UDP headers to extract metadata
- Aggregate packets into flow records (5-tuple keyed)
- Attribute connections to apps via UID resolution
- Persist flows to Room database
- Display live flows, history, and flow details in Compose UI
- Use manual DI, target SDK 36, Material3 theme

## Technical Constraints

- AGP 9.0.1 with Kotlin 2.0.21
- Room 2.7.1 with KSP annotation processing
- Navigation Compose 2.9.0 with type-safe routes
- No Hilt, no third-party networking libraries
- Foreground service for continuous monitoring

## What Was Built

### Core Models (5 files)
Protocol enum, FlowKey 5-tuple, FlowRecord with mutable counters, AppInfo for UID mapping, PacketInfo with TCP flag helpers.

### Packet Parsing (5 files)
IPv4/IPv6 header parsers, TCP/UDP header parsers, orchestrating PacketParser that takes raw ByteBuffer and produces PacketInfo.

### Flow Tracking (3 files)
FlowTracker with ConcurrentHashMap and debounced StateFlow, UidResolver with ConnectivityManager + /proc fallback, GeoResolver interface.

### Data Layer (4 files)
Room database with FlowEntity, FlowDao with reactive queries, FlowRepository interface with Room and in-memory implementations.

### VPN Engine (2 files)
WireWhisperVpnService for TUN interface lifecycle, TunProcessor (~450 lines) handling UDP relay via DatagramChannel and TCP proxy via SocketChannel.

### UI Layer (11 files)
Material3 theme, bottom navigation, NowScreen with VPN control FAB, HistoryScreen with filters, FlowDetailScreen, SettingsScreen.

### Documentation (1 file)
ARCHITECTURE.md with pipeline diagram, package structure, battery strategy, known limitations, Phase 2/3 backlogs.

## Bugs Fixed During Build

1. **KSP + AGP 9.x conflict**: Added `android.disallowKotlinSourceSets=false` to gradle.properties
2. **Invalid IPv6 address**: Changed `fd00:db8:wire::1` to `fd00:db8:1::1` in VPN TUN configuration
