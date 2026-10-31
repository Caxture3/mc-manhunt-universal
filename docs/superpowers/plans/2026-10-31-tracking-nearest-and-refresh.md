# Tracking Nearest Targets and Refresh Implementation Plan

> **For agentic workers:** Use executing-plans and targeted RED/GREEN checks. Config/command and version reference readers are independent delegated tasks. Preserve all prior uncommitted portal/blaze work.

**Goal:** Correct stale/distant structure guidance while retaining bounded search work, and add `tracking-distance-refresh-seconds=1.0` with minimum 0.1 seconds.

**Architecture:** Keep native locate queries and the existing movement/cooldown policy. Maintain a bounded immutable index of observed native points and actual generated structure pieces. Probe only loaded chunks near current same-world viewers; if structure-start data is missing, pre-read already-generated referenced start chunks with Paper async loading, globally at most two at a time and no generation. Read public structure bounds only after all references are loaded, preventing hidden synchronous loads. Each display chooses the nearest known piece/point from current position, so old per-player cache cannot override a confirmed closer structure. Split one-second maintenance from configurable display-only updates.

**Spec:** User screenshot shows a runner inside a fortress while tracking a point 719 blocks away. Code confirms movement AND cooldown retains old targets and native locate reports a reference point rather than nearest geometry; no real server profile or screenshot coordinates were supplied. Public CraftWorld.getStructures synchronously resolves starts, so it cannot be called blindly on a loaded viewing chunk.

**Constraints:** 1.21.11/26.3, Java 21/25, shared platform logic; no Fabric/protocol/SDK changes. Exact current structure region detection must not invoke broad locate or generate chunks. New interval applies immediately; finite decimal 0.1–60.0 seconds rounded up to 20TPS ticks. Fast updates cannot ensure inventory, query structures, update Dialog callbacks or call portal search. Preserve existing authorization, role hiding, elimination and session guards.

- [x] Decimal configuration, atomic persistence, bilingual commands/validation and tick rounding (delegated).
- [x] Read only loaded chunk structure references in each version adapter (delegated).
- [x] Bounded nearby-structure index/probes, two async start loads, stale/unload/clear protection; nearest-region/cache and deterministic source tests.
- [x] Integrate nearest corrected targets, current same-world origin and separate display/maintenance cadence; regression tests for 0.1s without extra search work.
- [x] Fresh review, independent builds, dist/metadata/checksums and docs. No full game test or performance claim.

Ruling: Two referenced start chunks may be asynchronously loaded to obtain already-generated structure metadata. This does not enlarge native search radius or generate terrain; it avoids blocking I/O inside getStructures. Portal POI search still follows its existing no-block-chunk-loading rules.

Ruling: Geometry-based distance is to the closest known structure piece; inside a piece it is 0. Unobserved/unloaded structures still use native locate, so no claim of instantaneous globally exact geometry across the entire configured radius is made.

Additional user requests: fix old `/rmh` usage fallback and release all four platform JARs as 0.2.0. API/SDK stays independently versioned 1.0.0. Old external usage templates with matching placeholders were overriding current built-ins; five usage keys now reject exact legacy root tokens, including formatting/namespace variations, without changing external YAML or valid customized `/mhu` text.

Review fixes: long refresh intervals keep ActionBar text alive using cached displays; only configured refresh recomputes the distance. Cancelling subscribers does not release I/O permits: discarded real loads remain counted until their underlying future finishes. RED/GREEN tests cover both issues, current-fortress geometry vs the 719-block stale target, negative chunk coordinates, world/target isolation and later lifecycle results. Geometry comparisons use primitive arithmetic rather than allocating a position for every piece. Final read-only review found no remaining important issue.

Verification: independent 1.21.11 and 26.3 `build` passed. 409 checks total, Paper 188 each, zero failures/errors/skips. Dual-language 130 keys/placeholders match; 18 top-level config entries / 20 leaves. Four JAR metadata versions are 0.2.0 with correct Java targets, commands, new classes and default config. Assets, protocol classes and SDK/API classes are unchanged. Four release JARs copied to `dist`, previous four SNAPSHOT deployment copies replaced, ten checksums refreshed. Report: `build/reports/release-0.2.0-artifacts.json`.

No Minecraft instance or live performance sampling was run. The screenshot scenario has a deterministic geometry/cache regression, not a claim of actual server reproduction.
