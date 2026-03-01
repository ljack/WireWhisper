# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore env vars: KEYSTORE_BASE64, KEY_ALIAS, KEY_PASSWORD, KEYSTORE_PASSWORD)
./gradlew assembleRelease

# Run all unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.wirewhisper.firewall.BlockingEngineTest"

# Run a single test method
./gradlew testDebugUnitTest --tests "com.wirewhisper.firewall.BlockingEngineTest.isBlocked returns false for null packageName"

# Download GeoIP database (auto-runs on preBuild if missing)
./gradlew downloadGeoDb
```

No custom lint or formatting tools are configured. Lint is disabled for release builds due to an AGP 9.0.1 crash.

## Architecture Overview

Single `app` module, package-based modularity under `com.wirewhisper`. No Hilt — manual DI via `WireWhisperApp` Application class with lazy singletons.

### Packet Pipeline (hot path)

```
App traffic → TUN fd → TunProcessor.readLoop()
  → PacketParser.parse() → PacketInfo
  → FlowTracker.onPacket() (ConcurrentHashMap, 500ms debounced StateFlow)
  → UidResolver.resolveAndEnrich() (ConnectivityManager primary, /proc/net fallback)
  → GeoResolver (LRU cache → heuristics → offline binary DB → Room cache → ipwho.is)
  → BlockingEngine.isBlocked() (O(1) concurrent set lookups)
  → UDP relay (DatagramChannel + Selector) or TCP relay (SocketChannel proxy with full state machine)
```

### Key Packages

- **`vpn/`** — `WireWhisperVpnService` (lifecycle, foreground service) and `TunProcessor` (~800 lines, most complex file: read loop, UDP/TCP relay, packet construction, checksum computation)
- **`packet/`** — Zero-copy parsers for IPv4, IPv6, TCP, UDP headers + DNS query/response and TLS ClientHello SNI extraction
- **`flow/`** — `FlowTracker` (aggregation + batch flush), `TrafficSampler` (ring buffer for sparklines), `UidResolver`, `HostnameResolver`
- **`geo/`** — `GeoResolver` interface + `InMemoryGeoResolver` (5-tier lookup), `OfflineGeoLookup` (binary search on custom GEO1 format), `GeoDbRefreshWorker`
- **`firewall/`** — `BlockingEngine` (in-memory sets backed by Room, supports app/hostname/country blocking)
- **`data/`** — Room database (`AppDatabase` v4), entities, DAOs, repository pattern
- **`ui/`** — Jetpack Compose with Material 3. Screens: now (live flows), history, detail, settings. Type-safe navigation via kotlinx.serialization routes. Shared utilities in `ui/util/` (e.g., `CountryUtils.kt`).

### Critical Implementation Details

- **TCP checksum is mandatory**: Must compute pseudo-header + segment checksum for TUN-injected packets. See `computeTcpChecksum()` in TunProcessor.kt. UDP checksum=0 is valid for IPv4.
- **SYN sequence handling**: Read client SYN seq from raw packet, set `theirSeqNum = clientSeq + 1`, then `ourSeqNum++` after SYN-ACK (SYN consumes 1 byte of sequence space).
- **Async TCP writes**: Per-session `Channel<ByteArray>` write queue + writer coroutine to avoid blocking the read loop.
- **FlowTracker debounce**: 500ms snapshot debounce means tests need `Thread.sleep(501)` or equivalent delay before asserting on `activeFlows`.
- **GeoIP binary DB**: Custom GEO1 format built at compile time from DB-IP CSV. Downloaded by `app/buildscripts/download-geoip.gradle.kts`, stored in `app/src/main/assets/geoip-country.bin`.
- **BlockingEngine country blocking**: Uses `resolveCountrySync()` (cache + offline DB only, no network) so even the first packet to a blocked country gets dropped.

### Test Configuration

- `unitTests.isReturnDefaultValues = true` in build.gradle.kts — stubs `android.util.Log` and similar for JVM tests
- 15 test files covering packet parsers, DNS/TLS, flow tracking, blocking engine, traffic sampling
- Test fakes (e.g., `FakeBlockRuleDao`, `InMemoryFlowRepository`) are defined inline in test files

### Tech Stack

- AGP 9.0.1 / Gradle 9.2 / Kotlin 2.0.21 / Compose BOM 2025.05.00
- Target & Min SDK 36 (Android 16)
- Room 2.7.1 with KSP 2.0.21-1.0.28
- Navigation Compose 2.9.0 (type-safe with kotlinx.serialization)
- Java 17 source/target compatibility

### AGP 9 / Gradle 9 Notes

- **No `applicationVariants`** — the old variant API (`android.applicationVariants.all { }`) is removed. Use `androidComponents.onVariants { }` for new variant API, but `outputFileName` is not available there. APK renaming is handled in CI instead.
- **No `archivesBaseName`** — removed in Gradle 9. Don't use `project.setProperty("archivesBaseName", ...)`.
- **No `BuildConfig` by default** — must opt in with `buildFeatures { buildConfig = true }` if needed.
- **Lint crash** — `checkReleaseBuilds = false` is required due to an AGP 9.0.1 crash on build script analysis.
