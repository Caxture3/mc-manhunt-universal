# Trial Chambers Tracking Implementation Plan

> **For agentic workers:** Execute inline using superpowers:executing-plans, with one fresh readonly review at the end. Work in the user-authorized shared project; do not commit or publish automatically.

**Goal:** Add speedrunner tracking for Trial Chambers, disabled by default, with the copied companion background.

**Architecture:** Add a native target behind the protocol-3 string-ID catalog, reuse structure locating/cache/probe code and common unlock policy. Add the fourth field to the existing config group. Use a GENERIC appearance plus an explicit built-in texture resource, preserving the public SDK enum and wire format; old protocol-3 clients without that texture can use the existing color fallback.

**Tech Stack:** Shared Paper logic with two structure-reference adapters; existing generic Fabric UI, shared PNG resource; product 1.0.0 / SDK 1.1.0 / protocol 3 unchanged.

**Spec:** User requests Trial Chambers runner tracking and permits copying its Fabric background from the supplied local project. `runner-structure-tracker-unlock-time.trial_chambers=-1` by default; `0` enables immediately and positive seconds use formal startedAt. Hidden/locked states, all-disabled compass removal, unlock notification, countdown, dynamic Dialog/Fabric authorization and pagination must match existing targets. Locate in the Overworld via the existing native nearest-structure entrypoint. Add `core:trial_chambers`, counting toward the 32-target total. Do not introduce an old-protocol wire ID; legacy three-target helpers must explicitly reject/skip this dynamic-only target.

## Review focus

- Existing three-key configs gain a disabled default; atomic map/save-failure tests must include the fourth field rather than passing only due to schema rejection.
- Four hidden/visible built-ins count against the same max32 catalog; only-trial enabled still grants a runner compass, four disabled does not.
- Trial must use Overworld and its own cache/probe key, not fall through to bastion/nether logic.
- Old three-target frames/IDs stay unchanged; V3/no-mod Dialog carries the new target while legacy paths cannot bypass locks.
- Copy the PNG byte-for-byte and verify both Fabric JARs contain it; missing texture remains a usable color fallback.

## Task 1: Config and native locating

Files: config/{IntConfigKey,RunnerStructureUnlockTimes,ConfigurationValues}, tracking/{TrackingTargetId,StructureUnlockPolicy,TrackingSelectionTargetMapper,StructureUnlockAnnouncements,TrackingDialogWindow,CompassTrackingService,PaperStructureProbeSource}, two StructureChunkReferences adapters, MhuCommand map fallback, default config/lang; focused config/policy/adapter tests.

- [x] Write failing checks for disabled default, four-field persistence/migration, immediate/delayed unlock and legacy rejection; native reference-key tests for both versions.
- [x] Add the fourth default=-1 field, preserve the old three-argument internal record constructor, add native Overworld trial locating and nearby structure metadata handling; retain all budgets and cache isolation.
- [x] Run focused and existing config/structure checks. Update save-failure fixtures to actually reach persistence with the full group schema.

## Task 2: Dynamic catalog and Fabric asset

Files: tracking/CoreTrackingTargets (new shared definition factory), ExtendedTrackingController, catalog tests, shared Fabric texture and fallback/pagination tests, bilingual names/config help.

- [x] Test production core definitions: hidden trial by default, trial-only enabled, timed unlock and denied pre-unlock selection; four built-ins leave 28 extension slots.
- [x] Centralize core definitions and availability, wire the fourth target and explicit texture, copy reference PNG without editing. Preserve older themes/SDK/protocol and existing menu behavior.
- [x] Verify resource fallback and four-option pagination, asset dimensions/hash and both versions' packaging.

## Task 3: Guides and delivery

- [x] Update current config/API guides and project constraints (four built-ins), keeping README public and external language files untouched.
- [x] Fresh readonly review, address Important findings, independently build 1.21.11 and 26.3; retain product1.0.0/SDK1.1.0/protocol3.
- [x] Refresh four deployment + six SDK JARs and checksums, verify language/schema/PNG/native adapter metadata and old SDK linking, record targeted checks separately from unperformed game tests.

Ledger/logs: build/trial-chambers/. No new dependencies or automatic commits/publication.

User clarification during implementation: Fabric must show four options per page, not three. Four built-ins all visible share one equal-width page; additional extension targets page in fours. Native Dialog is aligned to at most four columns. This supersedes the inherited three-per-page rule.

Completed 2026-11-01. Both independent builds passed,639 targeted checks; readonly final review found no Critical/Important. Copied PNG verified in both Fabric JARs, compiled pagination constant4 confirmed, four product1.0.0+sixSDK1.1.0 JAR inventory and hashes refreshed. See ../../verification/trial-chambers-tracking.md; no game/runtime visual checks, commits or publication.
