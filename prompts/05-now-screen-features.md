# Prompt 5: Now Screen Features — Traffic Graph, Blocking, Activity Sort

**Session**: 5 (Mar 1, 2026)
**Commits**: f256710, e97fe13

## Context

Three features to move WireWhisper from passive monitoring toward an actionable firewall tool:

1. **Traffic Graph Bottom Sheet** — tap sparkline to see bidirectional 60-second traffic chart
2. **App & Hostname Blocking** — toggle blocking per-app or per-hostname
3. **Activity-Based Sorting** — most recently active apps float to top with animated reordering

## Feature 1: Traffic Graph Bottom Sheet

### Data Layer

Split `TrafficRingBuffer` from single `LongArray` into separate `sentData`/`recvData` arrays. Added `TrafficSample(sent, received)` data class. Increased window from 30s to 60s. Updated `recordTraffic()` to accept `outgoing` parameter. Added `getAppDirectionalSamples()` for bidirectional data.

### UI

- **`TrafficChart.kt`** — Full-width Canvas (~200dp) with center line. Sent bars (pink/magenta) above center, received bars (cyan) below. Dynamic alpha based on magnitude. X-axis labels at 10s intervals, Y-axis max labels.
- **`TrafficBottomSheet.kt`** — ModalBottomSheet with app name header, sent/received totals, and the chart. Uses `skipPartiallyExpanded = true`.
- **Sparkline tap** — `TrafficSparkline` wrapped with `Modifier.clickable` that intercepts before card click. ViewModel holds `trafficDetail` StateFlow, refreshes data every 1s tick while bottom sheet is visible.

## Feature 2: App & Hostname Blocking

### Database

- `BlockRuleEntity` with `packageName` (always required, stable across reboots) and optional `hostname` (null = app-level block)
- `BlockRuleDao` with reactive `Flow<List>` + synchronous startup load
- DB migration v2 → v3 creating `block_rules` table with indexes

### Engine

`BlockingEngine` — hot-path `isBlocked(packageName, hostname)` uses `ConcurrentHashMap.newKeySet()` for O(1) lookups. App block overrides hostname rules. Mutations update Room + in-memory sets, emit StateFlows. Loaded at app startup via `loadRules()`.

### Pipeline Integration

`TunProcessor.processPacket()` checks `blockingEngine.isBlocked()` after enrichment, before relay. Blocked packets are silently dropped. Added `FlowTracker.getFlow(key)` for the blocking lookup.

### UI

Green checkmark (allowed) / red X (blocked) `IconButton` on each app row and hostname row. App-level block applies `alpha(0.5f)` to content. Hostname block toggle disabled when parent app is blocked.

## Feature 3: Activity-Based Sorting with Animations

### Sort Logic

Added `SortMode` enum (RECENT_ACTIVITY, TOTAL_BYTES). Default is RECENT_ACTIVITY which sorts by `lastActivityTime = uidFlows.maxOf { it.lastSeen }`, falling back to totalBytes.

### Pause/Resume

Sorting pauses when user is scrolling (`LaunchedEffect` on `listState.isScrollInProgress`) or interacting (expansion toggles). Current order is frozen into `_frozenOrder`. After 3 seconds idle, sorting resumes and frozen order clears.

### Animation

`Modifier.animateItem()` on each `items` block in LazyColumn. Requires stable `key = { it.uid }` which was already present. Sort mode toggle shown as `SingleChoiceSegmentedButtonRow` with "Recent" / "Bytes" options.

## New Files

| File | Lines | Purpose |
|------|-------|---------|
| `TrafficChart.kt` | ~100 | Bidirectional bar chart Canvas |
| `TrafficBottomSheet.kt` | ~80 | ModalBottomSheet wrapper |
| `BlockRuleEntity.kt` | ~15 | Room entity |
| `BlockRuleDao.kt` | ~30 | Room DAO |
| `BlockingEngine.kt` | ~90 | In-memory blocking with Room persistence |
| `BlockingEngineTest.kt` | ~120 | 9 unit tests |

## Test Results

- Build: clean `assembleDebug`
- Tests: 170/173 pass (3 pre-existing FlowTracker debounce failures)
- New tests: BlockingEngineTest (9), updated TrafficRingBufferTest (10), updated TrafficSamplerTest (10)
