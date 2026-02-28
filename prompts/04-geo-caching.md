# Prompt 4: Offline IP Geolocation with Room Cache

**Session**: 4 (Mar 1, 2026 ~12:00 AM)
**Commit**: 7708f7f

## Context

IP geolocation was hitting the ip-api.com API for every unique destination IP. This was slow, rate-limited, and leaked user browsing data to a third party. Needed an offline solution.

## Implementation

### Offline Binary Database

Created `OfflineGeoLookup` — a binary-search IP range database loaded from a compact asset file. Each entry maps an IP range to a country code. The database is generated at build time by a Gradle script (`download-geoip.gradle.kts`) that downloads and converts public GeoIP data.

### Three-Tier Cache Architecture

`InMemoryGeoResolver` replaced the old API-based `GeoResolver`:

1. **In-memory ConcurrentHashMap** — fastest, covers recently seen IPs
2. **Room database (`geo_cache` table)** — survives app restarts, 30-day TTL
3. **Offline binary database** — fallback for never-seen IPs, no network needed

### Periodic Refresh

`GeoDbRefreshWorker` (WorkManager) updates the binary database every 30 days on Wi-Fi when battery isn't low.

### New Files

- `OfflineGeoLookup.kt` — Binary search lookup with asset/file loading
- `GeoDbRefreshWorker.kt` — WorkManager periodic update worker
- `GeoCacheEntity.kt` / `GeoCacheDao.kt` — Room persistence for geo results
- `InMemoryGeoResolver.kt` — Three-tier cache coordinator
- `download-geoip.gradle.kts` — Build-time database generation
- DB migration v1 → v2 for `geo_cache` table

## Result

Zero-latency country resolution for all IPs. No network calls during normal operation. Country flags appear instantly in the Now screen's country grouping mode.
