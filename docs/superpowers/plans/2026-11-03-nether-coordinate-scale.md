# Configurable Nether Coordinate Scale Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, with regression checks and one fresh readonly final review.

**Goal:** Deliver the user-approved nether-coordinate-scale plan: integer1..2147483647/default8, global/all entities, immediate atomic config, Paper portal entry events and shared compass projection.

**Architecture:** One shared coordinate helper supplies horizontal projection/clamping; a shared listener adjusts native portal search inputs. Existing configuration registration remains authoritative. Native and custom tracking cancel cross-dimension requests on effective changes without clearing valid shared locations or bypassing cooldown/concurrency budgets.

**Tech Stack:** Shared Paper Java21/25, existing YAML/Adventure/API1.2/protocol3; Fabric unchanged.

## Global constraints

Product1.0.0/API1.2.0/protocol3 unchanged. No world dimension changes, new client mod, migration of portals, external language rewriting, commits or publication. Default8 keeps vanilla portal events untouched; only NORMAL↔NETHER portal causes handled. Preserve destination world/Y/rotation and native search/create parameters, cancellation and later event listeners.

## Review focus

- Coordinate multiplication at integer maximum and negative positions must clamp safely to native borders.
- Default8, cancelled/End/command events and nonparticipants must retain intended behavior.
- Effective config overrides and save failures must notify tracking only after authoritative changes.
- Old native/custom query completions must not commit after scale changes; cooldown and real-work permits remain intact.
- Frozen checkpoint key sets must remain compatible with previous releases.

### Task 1: Configuration and portal mapping

- [x] RED: strict integer bounds, persistence failure, registry immediate/override/frozen snapshot compatibility; both portal directions/player/entity/default8/cancellation/metadata/borders.
- [x] GREEN: IntConfigKey and immediate binding; effective change callback; shared portal helper/listener and bilingual/config resources, registration.

### Task 2: Tracking projection and stale work

- [x] RED: replace fixed8 projected origins/reference, affected request cancellation with cooldown and stale tokens; preserve genuine positions/caches.
- [x] GREEN: use shared helper for cross-dimension searches; cancel native subscriptions and custom work while retaining invalidated placeholders until scheduled refresh.

### Task 3: Review, docs and artifacts

- [x] Fresh readonly review and targeted fixes; update public docs/manual language notes.
- [x] Independent1.21.11/26.3 builds, SDK/metadata/language/bytecode/checksum validation and four deploy+six SDK artifacts.
- [x] Record evidence and explicit unperformed game tests.

Completed2026-11-03: fresh readonly review found one Important custom server-size bound issue, reproduced and fixed; no deferred items. Both final independent builds passed691checks. Four product1.0.0 + sixSDK1.2.0 JARs and hashes verified. See ../../verification/nether-coordinate-scale.md; actual game/runtime/performance checks not run. No commits or publication.
