# Tracking control API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Allow extensions to force either role to track catalog targets or arbitrary players safely.

**Architecture:** Add a default TrackingRegistry.control entry and detached DTOs. A plugin-owned control service owns revisions and handles; shared Paper tracking resolves effective targets without replacing manual selections. A separate player observation/selector phase caches positions for display.

**Tech Stack:** Java 21 API and shared Paper, Java 21/25 version builds, existing JUnit and protocol 3.

**Spec:** ../specs/2026-11-01-tracking-control-api-design.md

## Global Constraints

- SDK 1.2.0; product 1.0.0; protocol 3. No new client mod or protocol changes.
- Existing source/binary contracts and native search budgets retained; no world access from worker threads.
- Work in the authorized checkout; no commits, publication or comprehensive game testing.

## Review Focus

- Old handles/completions after same-target replacement must not reset current tracking.
- Menu refresh and configuration changes must not overwrite an active override or destroy its manual backup.
- Eliminated targets and owners closing inside callbacks must fail safely without stale directions.
- Offline viewers/outsiders must not require loading player data or polluting persisted participant positions.
- High-frequency refresh must not multiply native structure queries or inventory repair.

### Task 1: Public contracts and owned control state

Files: api tracking DTOs/TrackingRegistry, ControlDomain/Capability; extension/tracking/TrackingControlService; corresponding tests.

Produces: TrackingControl.snapshot/apply, TrackingOverrideRequest factories, TrackingControlMutation and generation-safe ExtensionHandle.

- [x] Preserve a consumer compiled against SDK 1.1; add failing ownership/revision/replacement/lifecycle tests.
- [x] Implement DEFAULT/TARGET/DISABLED contracts and owned state with strict session/match/control revision checks; verify RED to GREEN.

### Task 2: Shared runtime and live player targets

Files: ExtendedTrackingController, CompassTrackingService, new player observation helper and focused tests.

Consumes: Task 1 control state; produces effective catalog/player/disabled routing for both roles.

- [x] Add failing tests for player observations, outsider/offline/eliminated selection, exceptions/reentry and override/manual restoration boundaries.
- [x] Integrate overrides, cancel stale queries and menus, keep manual state separate, and clear at role/elimination/end boundaries.
- [x] Sample player providers at distance cadence; keep display paths read-only and native searches in existing maintenance budgets. Verify focused tests.

### Task 3: Examples, compatibility, delivery

Files: API guides/examples, README, version catalog/service, verification record and AGENTS constraints.

- [x] Compile examples for catalog, fixed player, nearest hunter, disabled/default and safe handle replacement; check old 1.0/1.1 consumers.
- [x] Run one fresh readonly final review; resolve significant findings with regression tests.
- [x] Independently build 1.21.11 and 26.3, inspect metadata/bytecode/SDK and refresh four product jars, six SDK jars and SHA256 manifest.
- [x] Record exact verification and unperformed game runtime checks.

Completed 2026-11-01. Two independent builds and667 checks passed. Fresh readonly review found three Important issues, all reproduced and fixed; no deferred findings. Four product1.0.0 and sixSDK1.2.0 artifacts, compatibility fixtures and checksums verified. See ../../verification/tracking-control-api.md. No game/performance runtime verification or commits/publication.
