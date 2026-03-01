# Simplify Targets

## High Priority

1. **TunProcessor.kt:711** — Fix IP checksum operator precedence: `and 0xFF shl 8` → `(and 0xFF) shl 8`
2. **FlowTracker.kt:108-124** — Remove dead `.also{}` block in `enrichFlow` — `copy()` already copies `var` fields
3. **NowUiState.kt:7** — Delete dead `ScreenMode` enum (never referenced)
4. **GeoDbRefreshWorker.kt:58** — Check `renameTo()` return value, fallback to copy if rename fails
5. **Unify `formatBytes`** — Three variants exist (NowScreen.kt:737, FlowDetailScreen.kt:176, TrafficChart.kt:175 `formatBytesShort`). Extract to `ui/util/FormatUtils.kt` with a single implementation. Use decimal precision (e.g. `"%.1f KB"`) consistently.

## Medium Priority

6. **TunProcessor.kt:72,167** — `geoResolvingIps` never pruned. Add `finally { geoResolvingIps.remove(dstAddr) }` in the geo-resolve coroutine
7. **HostnameResolver.kt:35-55** — CNAME chain walk is dead code (always overwritten by `queryName`). Remove `cnameMap` and the chain-walking loop
8. **UidResolver.kt:60-69** — Delete dead `resolve()` method
9. **FlowDao.kt:18-28** — Delete 4 unused single-filter queries (`getFlowsByApp`, `getFlowsByCountry`, `getFlowsByProtocol`, `getFlowsSince`)
10. **BlockRuleDao.kt:16-23** — Delete 3 unused point-lookup queries (`getAppRule`, `getHostnameRule`, `getCountryRule`)
11. **GeoCacheDao.kt:17** — `evictOlderThan()` is never called. Add a call in `GeoDbRefreshWorker.doWork()` to evict entries older than 30 days
12. **SettingsScreen.kt:73** — Fix `_geoEnabled` init: `MutableStateFlow(app.geoResolver.onlineLookupEnabled)` instead of hardcoded `false`
13. **GeoResolver.kt:46** — Fix KDoc: change `ip-api.com` reference to `ipwho.is`
14. **FlowDetailScreen.kt:91** — Wrap `SimpleDateFormat` in `remember {}`
15. **HostnameResolver.kt:66-69** — `onDnsQuery` parses DNS and discards result. Remove the parse call (keep the method signature if callers exist, just make it a no-op or delete entirely)
16. **UidResolver.kt:137** — Move `"\\s+".toRegex()` to a companion object constant

## Low Priority (skip for now)

- BlockingEngine TOCTOU race in toggle methods
- NowViewModel tick causing full rebuild every second
- hashCode() as nav ID
- Traffic color constants extraction
- String resources
- FIN sequence handling
- FlowRecord data class var fields
