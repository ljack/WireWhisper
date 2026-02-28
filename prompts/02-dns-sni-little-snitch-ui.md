# Prompt 2: DNS/SNI Hostname Resolution + Little Snitch-style UI

**Session**: 2 (Feb 28, 2026 ~8:00 PM)
**Commits**: 4ffe581 through f174370 (2 commits)

## Prompt Summary

The MVP showed live flows but with raw IP addresses in a flat list. Transform the NowScreen into a Little Snitch-style grouped view:

1. Parse DNS responses to resolve IPs to hostnames
2. Extract TLS SNI from ClientHello for HTTPS connections
3. Group flows by App → Hostname with traffic sparklines
4. Expandable app rows with animated hostname drill-down

## Planning Phase

Claude explored the existing codebase to identify integration points:

- `TunProcessor.processPacket()` — hook for outgoing DNS queries
- `TunProcessor.udpResponseLoop()` — hook for incoming DNS responses
- `TunProcessor.relayTcp()` ESTABLISHED branch — hook for TLS SNI on first data packet
- `FlowTracker.enrichFlowGeo()` — existing enrichment mechanism to reuse
- `FlowRecord.dnsHostname` — field already existed, just needed population

## Implementation (10 steps)

### New Files (6)

1. **DnsParser.kt** — DNS query/response parsing with RFC 1035 label pointer compression, A/AAAA/CNAME extraction, CNAME chain walking
2. **TlsParser.kt** — TLS ClientHello SNI extraction, walks record header → handshake → skip session/cipher/compression → find SNI extension (type 0x0000)
3. **HostnameResolver.kt** — LRU cache (4000 entries) with TTL, retroactive enrichment of active flows when new DNS mapping discovered
4. **TrafficSampler.kt** — Per-UID circular ring buffer (30 seconds), synchronized add/snapshot for sparkline data
5. **NowUiState.kt** — UI models: AppGroupUiModel, HostnameGroupUiModel
6. **TrafficSparkline.kt** — Compose Canvas sparkline, 90x24dp, alpha-scaled bars

### Modified Files (6)

1. **TunProcessor.kt** — Added hostnameResolver parameter, three pipeline hooks (DNS query, DNS response, TLS SNI), hostname cache lookup for new flows
2. **FlowTracker.kt** — Added trafficSampler property, enrichFlowsByAddress() for retroactive hostname enrichment, sparkline recording in onPacket()
3. **WireWhisperApp.kt** — Added trafficSampler and hostnameResolver lazy singletons
4. **WireWhisperVpnService.kt** — Passes hostnameResolver to TunProcessor
5. **NowViewModel.kt** — Rewrote to group flows by UID→hostname, 1s tick for sparkline, expand/collapse state, icon/name caching
6. **NowScreen.kt** — Rewrote to app-grouped layout: app icon + name + destinations + sparkline + chevron, expandable hostname rows

## Bugs Fixed After Deploy

1. **UID -1 everywhere**: `TunProcessor` called `uidResolver.resolve()` (no-op) instead of `resolveAndEnrich()`. The resolve() method found UIDs but never told FlowTracker.
2. **No sparklines**: `TrafficSampler.recordTraffic()` had `if (uid < 0) return` guard that blocked all UID -1 flows from being sampled. Removed the guard.

## Result

Working Little Snitch-style network monitor: app icons, real domain names (graph.facebook.com, www.linkedin.com, etc.), expandable groups, animated sparklines, proper app attribution for most flows.
