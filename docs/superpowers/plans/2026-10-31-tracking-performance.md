# Tracking Performance Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for the shared engine and integration; independent cursor and version adapters may use superpowers:dispatching-parallel-agents. Preserve all pre-existing uncommitted changes.

**Goal:** Reduce repeated structure queries and split portal POI searches into bounded main-thread reads with immutable coordinate filtering off-thread, preserving existing radius and nearest-target semantics.

**Architecture:** A world/chunk POI snapshot cache serves a lazy concentric-ring portal search. Identical requests share work; nearby requests share per-column snapshots. Each global tick has a column-count and soft time budget. Only immutable coordinates cross the worker boundary, and main-thread polling commits results through existing per-player session/version checks. Static structure queries retain native semantics with an exact-input shared cache and in-flight coalescing.

**Tech Stack:** Java 21 shared Paper code; Java 21 / Java 25 version adapters; existing Bukkit scheduler; JUnit. No Fabric/protocol/API contract change.

**Spec:** User request to implement the shared caching, duplicate request merging, batched POI query and safe asynchronous filtering proposed in `docs/tracking-performance.md`.

## Global Constraints

- Minecraft 1.21.11 and 26.3 independently build; no full game test required.
- Main thread owns all world/NMS reads and callbacks. Async inputs contain no World/Player/Location/NMS references.
- Continue finding existing POIs in unloaded chunks, never force-load/generate actual block chunks for the query.
- Original configured radius, integer BlockPos origin, spherical POI filtering and 3D nearest distance retained. No early return of an arbitrary portal.
- World unload, portal changes, player/session invalidation and plugin shutdown must not publish stale results or retain world/player references in caches.
- Existing API 1.1 plan remains saved only. External language files unchanged.

## Review Focus

- Negative chunk coordinates and radius edges: no missing or duplicate columns; lower bound cannot falsely prune a nearer portal.
- Shared work owner cancellation: other subscribers still receive their result; latest request wins.
- Cached negative snapshots followed by a new/broken portal: event invalidation and bounded TTL permit rediscovery.
- Async result arriving after cancel/clear/unload/reassignment: must not modify new state.
- Count/time budget exhaustion and failures: searches yield fairly and either complete or cancel, never remain stuck searching.

## Tasks

- [x] 1. Pure `PortalPoint`, lazy `PortalSearchCursor`, and two `PortalLocator.readChunk` adapters; verify mapped runtime method signatures and geometric tests.
- [x] 2. `SharedSearchCache` with positive/negative TTL, bounded LRU, exact key coalescing, subscriber cancellation and stale completion checks; RED/GREEN targeted tests.
- [x] 3. `PortalSearchEngine` with shared column snapshots, fair batching, soft tick budget, immutable async nearest filtering, cancellation and invalidation; deterministic fake executor/reader/clock tests including brute-force nearest and unloaded snapshots.
- [x] 4. Integrate shared static work and portal engine in `CompassTrackingService`; invalidate POI snapshots for portal changes/unload through a small listener; preserve display and session guards.
- [x] 5. Fresh review, independent two-version builds, metadata/bytecode/language and dist checksums; update public performance documentation and record absence of runtime profiling.

Ruling: Continue in the current workspace because this change extends the preceding authorized features. Do not move or reset existing work; no commit or push requested.

Ruling: The column-count/time limit is soft: a single synchronous POI-column read may exceed the budget on slow storage. This implementation prevents one task from enumerating the entire radius, but cannot promise zero lag from individual I/O.

Ruling: Native structure locate remains synchronous and indivisible; sharing and caching reduce duplicate calls. Replacing structure generation algorithms or adding a smaller search radius is outside this optimization.

Ruling: Both versions use isolated, signature-verified NMS reflection for single-column POI reads, because the 26.3 public radius API cannot bound a read to one column. Shared code contains no version checks or NMS types.

Review corrections: debounce invalidation independently for each column until two ticks after its last change. Reject ordinary obsidian without a neighbouring portal; unloaded border neighbours remain conservative without loading block chunks. Added RED/GREEN regressions for both corrections and explicit LinkageError cleanup.

Validation: Independent `build -PmcVersion=1.21.11` and `build -PmcVersion=26.3` passed. Common 3, protocol 10, API 6, API-Paper 4, Paper 137 each, Fabric 5 each: 307 checks, zero failure/error/skip. Includes 120 randomized cursor coverage/lower-bound cases and brute-force nearest comparisons, true worker-thread execution, cancellation, TTL and soft/global budgets. Final read-only review found no remaining important issue.

Deployment: Four JARs verified and copied to `dist`, ten SHA-256 entries refreshed. Java targets, commands, new classes and absence of Bukkit references in the async calculation classes verified. Fabric JARs byte-identical to the previous delivery, API/SDK 1.0.0 classes unchanged. Report: `build/reports/tracking-performance-artifacts.json`.

No game instance or runtime performance profile was run. Single-column cold POI I/O and indivisible native structure locate can still block; no TPS improvement ratio or hard latency bound claimed.
