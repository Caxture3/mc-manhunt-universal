# Role Debug Commands Implementation Plan

> **For agentic workers:** Execute inline using superpowers:executing-plans. User authorized this shared project; preserve the current workspace and do not commit or publish automatically.

**Goal:** Add `/mhu debug role-reshuffle` and `/mhu debug role-set <player> <runner|hunter>`, rename management permission, deliver product 1.0.0.

**Architecture:** Adapt the reference debug handler's routing, fixed-roster name lookup, usage and completion. Keep role validation, atomic commit, Paper events and synchronization shared with the existing API. Core admin commands may override an extension's role choice, as existing core start/stop commands do, without claiming or releasing its registration/domain.

**Tech Stack:** Shared Paper Java with existing API/Adventure/i18n; both game versions; SDK 1.1.0 and protocol 3 unchanged.

**Spec (confirmed user scope):** User replaced the initial single-runner reference command with `role-set <player> <runner|hunter>`, changing only that participant. User explicitly chose new permission `manhuntuniversal.admin` with no old-node fallback, and product version 1.0.0. Target names are case-insensitive fixed participants and may be offline. RUNNING only; eliminated roles cannot change and online unready players retain existing protection. Both teams must retain an uneliminated member. Random reshuffle preserves current uneliminated runner count and must change at least one role. Repeating an existing role replays the normal private identity display without modifying roles/history or clearing tracking. Hide-role mode must not expose the target's new role in command feedback. Lobby selections and configured runner count remain unchanged.

## Review focus

- Event callbacks can cancel or make the match/revision/player availability stale; debug must revalidate exactly like API changes.
- A reshuffle with no alternative eligible team must fail without loops or partial changes; a random draw equal to the old team must be corrected to a different valid team.
- Core debug must work while an extension owns ROLES without releasing that owner, and callback reentry must remain BUSY.
- Permissions must be checked at execution, completion and boolean clicks; old permission alone must not authorize anything.
- Name parsing must never select an unrelated online player, and hidden-role feedback must not reveal assignments.

## Task 1: Shared role commit and debug operations

Files: extension/PaperExtensionService, ExtensionRuntime, GameExtensionHooks; runtime/ActiveGameCoordinator; extension/PaperExtensionServiceTest.

- [x] Add failing cases for core single-player set, offline target, last-team refusal, eliminated/unready target, same-role reveal only, guaranteed changed reshuffle/no alternative, ownership preservation and cancellation/reentry/stale state.
- [x] Extract common role commit and match checks from API, retaining all existing API behavior. Debug uses `core:debug` ChangeSource, fixed session/revision and existing runtime synchronization; no forged extension token.
- [x] Reuse existing random selection; if the result is unchanged, swap one eligible runner/hunter instead of retrying indefinitely. Run shared API and focused debug checks.

## Task 2: Command, permission and language integration

Files: command/DebugCommandHandler (new), MhuCommand, shared plugin.yml and language files; command tests; project constraints/guides.

- [x] Add failing command checks for denied/old-only permission, inactive/countdown game, syntax, unknown/eliminated/unready target, case-insensitive offline names, role argument and contextual completion.
- [x] Adapt reference routing/participant lookup, wire command and completion, localize both languages, retain generic feedback under hide mode. Route role changes through Task 1 hooks.
- [x] Change the only management node to `manhuntuniversal.admin`, including metadata, all gates/clicks/docs; no legacy alias. Show debug in root help.

## Task 3: Version, review and delivery

Files: gradle.properties, README, current guides, docs/verification/role-debug-commands.md, dist artifacts/checksums.

- [x] Set product 1.0.0, keep SDK 1.1.0. Update current deployment names and explain manual permission migration without exposing local reference paths in README.
- [x] One fresh readonly final review, fix Important findings with targeted checks, independently build 1.21.11 and 26.3.
- [x] Verify metadata/permission/language/bytecode/binary compatibility; update four 1.0.0 deployment JARs, six SDK JARs and SHA-256, remove superseded deployment copies after replacement checks. Record tests separately from unperformed game checks. No commits/publication.

Completed 2026-11-01. Two independent builds passed, 619 checks; fresh readonly review found no Critical/Important. Four product1.0.0 + sixSDK1.1.0 JARs and SHA-256 inventory verified. No game runtime tests, commits or publication. See ../../verification/role-debug-commands.md.
