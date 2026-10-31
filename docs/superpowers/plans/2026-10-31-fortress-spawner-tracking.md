# Fortress Spawner Tracking Implementation Plan

> **For agentic workers:** Use executing-plans and targeted RED/GREEN checks; version candidate readers are an independent delegated task. Preserve all previous uncommitted work.

**Goal:** When tracking a Nether fortress, prefer the nearest confirmed blaze spawner inside the selected fortress, with the current structure target as fallback.

**Architecture:** Extend existing immutable region observations with verified spawner positions. Extract vanilla MonsterThrone candidates from resident structure metadata in the two version adapters; also observe actual blaze spawner block entities in the already-loaded probe chunk. Reuse the existing two-permit no-generation async loader for candidate chunks, and verify real SPAWNER/BLAZE state on the main thread. Selection first chooses the fortress by existing logic, then its nearest confirmed spawner; it cannot borrow a spawner from another fortress. Fast display stays cache-only.

**Constraints:** Keep release 0.2.0, API/SDK 1.0.0, existing protocol and Fabric choices. No wide block scans or terrain generation. Missing/destroyed/wrong-entity/unavailable candidates must fall back; loaded known targets are revalidated by one-second maintenance without loading chunks. Clear/unload discards observations while true in-flight I/O keeps its permit until completion.

- [x] Candidate readers: verify both MonsterThrone postProcess world-coordinate mapping and snapshot immutable candidate positions.
- [x] Index grouped spawner priority, closest selection, range/world/target isolation and fallback tests.
- [x] Probe candidate chunks through bounded async work; actual block validation and destroyed-target pruning; deterministic tests.
- [x] Integrate shared source and maintenance, review, update docs, build both versions and refresh four deployment JARs/checksums. No full game test.

Ruling: Candidate coordinates alone never count as a found spawner. A missing or not-yet-generated candidate chunk is not generated to manufacture a target. Cross-dimension portal guidance remains unchanged until the fortress can be observed.

Completed: independent offline Gradle builds for 1.21.11 and 26.3 passed; 433 checks total, including 200 Paper checks per version. Candidate adapters passed a separate read-only review of mappings, world coordinates, asynchronous lifecycle and selection semantics. Four deployment JARs and the ten-entry checksum manifest were refreshed and verified. Fabric JARs, API/SDK, protocol and resources match the preceding 0.2.0 delivery. Evidence: `build/reports/fortress-spawner-artifacts.json`. No Minecraft instance or live performance test was run.
