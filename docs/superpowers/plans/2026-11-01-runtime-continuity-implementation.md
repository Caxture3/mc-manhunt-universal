# Runtime Continuity Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. The user authorized changes in this shared workspace; do not create commits, publish, or split the combined worktree.

**Goal:** Suppress piglin brutes optionally, retain lobby choices across matches, and restore active games with paused offline time.

**Architecture:** Existing game and player services remain authoritative. Add validated pure session snapshots, a separate YAML/atomic checkpoint store, and coordinator suspend/resume orchestration. Recreate all tasks, tokens and world-derived caches rather than serializing callbacks or platform objects.

**Tech Stack:** Java 21/25, shared Paper implementation, existing YAML and AtomicFileWriter; unchanged Fabric protocol 3/API 1.1.0/product 0.2.0.

**Spec:** [Confirmed scope and design](../specs/2026-11-01-runtime-continuity-design.md).

## Constraints and review focus

- Existing inventory/world persistence remains the server's responsibility; RUNNING restore must never execute a start reset.
- Quit still releases lobby choices, while role snapshots remain fixed for the active match.
- Keep eliminated-player restoration debt and offline pending resets, including when no active game remains.
- Configuration shrinking during play must not clear choices until idle; no mid-game capacity-cleared.
- Invalid snapshots must not partially mutate runtime state or silently resurrect a settled match; canceled async work cannot overwrite a restored session.

## Task 1: Optional piglin-brute suppression

Files: config BooleanConfigKey/CoreConfigurationBindings/config.yml/bilingual lang, optimization/PiglinBruteSuppressionListener, CoreFeatureIds, ManhuntUniversalPlugin and ActiveGameCoordinator hooks; matching listener tests.

- [x] Add focused failing tests for default/stage/toggle, spawn cancel, existing purge and chunk-load purge, no uncancellation.
- [x] Add default-false config/localized name and RUNNING feature policy; wire activation/recovery/config change purge and entity events.
- [x] Run focused tests; preserve API enum/interface compatibility.

## Task 2: Keep lobby reservations between matches

Files: GameManager.clearSettled, ActiveGameCoordinator.cleanup, selection/identity tests and docs.

- [x] Change settlement tests to require retained explicit choices and unchanged insertion order; verify quit/forced-random still clears.
- [x] Remove match-end clearing; after clearing active session trim current runner capacity and restore lobby previews. Random assignment must not become an explicit choice.
- [x] Run selection/config/identity targeted checks.

## Task 3: Validated state and atomic checkpoints

Files: game/SessionSnapshot, GameSession/GameManager restore adapters; persistence/RuntimeCheckpoint and RuntimeCheckpointStore; PendingPlayerState/RunnerSpectatorService/IdentityDisplayService snapshot adapters; tests for data/codec/debt/failure.

- [x] Add failing round-trip, malformed/duplicate roster/roles/elimination/time, paused clock, countdown and atomic-failure/tombstone tests.
- [x] Implement immutable values with complete validation before installing the restored session; keep UUID/revision, saved core settings and all cleanup debt.
- [x] Implement bounded/versioned YAML with existing AtomicFileWriter; no Bukkit object serialization, callbacks or locate cache persistence.
- [x] Run targeted tests and verify invalid input leaves the current state untouched.

## Task 4: Suspend/resume lifecycle and delivery

Files: ActiveGameCoordinator/GameStartCoordinator, persistence/RuntimePersistenceService, plugin enable/disable, config match-value restore, tracking selection restore, bilingual messages, README and continuity guide.

- [x] Add tests for elapsed-time pause, pending reset/death mode restoration, lock preservation, settlement invalidating the checkpoint and no second reset on RUNNING resume.
- [x] Restore next tick after plugin initialization; reschedule COUNTDOWN activation or RUNNING tracking/identity/end probe. Serialize checkpoint periodically and before shutdown cleanup; preserve restoration debt after cleanup. Do not replay a fresh start event for an existing running session.
- [x] Document ordinary restart scope, corruption/failure behavior, extension-owned persistence and stopped-time policy.
- [x] Independently build both Minecraft versions, refresh four JARs/checksums, retain six API 1.1 SDKs; inspect config/language/bytecode metadata and record unperformed game runtime tests.

No automatic commit or publication. Track task evidence in build/api-1.1-work/continuity-progress.md.

Completed 2026-11-01. Both independent full builds pass, 573 total targeted checks, ten JARs and checksums refreshed. Runtime-independent coverage includes paused snapshots, atomic failures, debts and pending-restore tombstone gates. No actual restart/game test performed; see ../../verification/runtime-continuity.md. No commits or publication.
