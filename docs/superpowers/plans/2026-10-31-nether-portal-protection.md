# Nether Portal Protection Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans and targeted RED/GREEN checks; preserve the current workspace and do not commit or push.

**Goal:** Add `prevent-nether-portal-destruction=false`, protecting Nether portal bodies and necessary obsidian frame blocks from vanilla player/environment destruction during RUNNING matches.

**Architecture:** A shared constant-size local protection predicate checks dimension, material and portal plane. A shared listener intercepts player actions, replacements, physics, fluids, entities, pistons and explosions before changes commit. Read the current configuration for each event; use the existing config persistence, completion, localized clickable list and language fallback.

**Tech Stack:** Paper shared Java source, Java 21 / Java 25 targets, Adventure localization, JUnit proxy fixtures. No Fabric, protocol or SDK contract change.

**Spec:** User explicitly confirmed default false; RUNNING only; all vanilla players/environment including creative, OP and nonparticipants. Admin commands, WorldEdit and other plugins directly setting blocks are outside the required scope.

## Constraints and review focus

- Nether environment only; idle/countdown/settlement and disabled config permit changes immediately.
- Protect portal blocks and required frame edges, not ordinary obsidian, optional corners or obsidian adjacent perpendicular to the portal plane.
- A boundary neighbour must not force-load a chunk; conservative protection is acceptable where an unloaded neighbour cannot be checked.
- Cancel replacement based on the original block state, including multi-place. Filter explosion lists without cancelling unrelated damage.
- Cover buckets/dispenser replacement and piston destination/head as well as direct break and physics destruction; keep portal creation/travel unaffected.
- No per-tick world scans, permanent portal snapshots or restoration loop; external language files remain untouched.

## Tasks

- [x] Configuration enum/defaults, bilingual labels and denial text; default/missing/invalid/atomic save tests.
- [x] `NetherPortalProtection` predicate and `NetherPortalProtectionListener`; geometry, live gate and actual event cancellation tests, including normal obsidian and other dimensions.
- [x] Register listener, update public docs and project constraints; review event paths against both versions.
- [x] Independent two-version builds; check JAR metadata, bytecode, unchanged client/API, refresh dist and checksums. Record no runtime game test.

Ruling: Piston retract events with moved blocks provide their movement direction. Use that direction for moved-block destinations, and piston block data facing for extension head checks.

Review correction: Native dispenser bucket placement uses its facing block. Item toss fallback and droppers use velocity, not absolute destination coordinates; the listener now restricts placement protection to dispensers facing portal blocks. The fallback regression was observed failing and then passing. Frame geometry, original multi-place state and automatic portal creation were reviewed against both versions' APIs/source. Final review found no remaining important issue.

Validation with the subsequent blaze-looting change: independent 1.21.11 and 26.3 builds passed, 359 total checks (Paper 163 each), zero failures/errors/skips. Dual-language 128 keys/placeholders match; 17 top-level configuration entries / 19 leaves. Four deployment JARs and ten checksums refreshed; Fabric JARs and API/SDK classes unchanged. Report: `build/reports/portal-protection-blaze-looting-artifacts.json`. No game instance was run.
