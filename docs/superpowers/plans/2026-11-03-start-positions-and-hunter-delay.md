# Start positions and hunter delay

User-requested implementation, with confirmed defaults and choices. Execute inline using executing-plans/TDD; one fresh readonly final review. Product1.0.0/API1.2.0/protocol3 unchanged; Java21 pure API and shared Paper21/25, no Fabric changes. Existing checkout authorized, no commits/publication.

## Design

- hunter-start-wait-seconds integer0..2147483647/default0, NEXT_MATCH. Only reset-enabled games apply delay. Deadline is formal startedAt+frozen wait; all current living hunters share it, disconnect does not pause, restart downtime does. Changing role clears/adds restrictions only until original deadline; no new timer, reset or teleport.
- player-start-pos is one NEXT_MATCH config root, hunter/runner each holding mode world-spawn(default)/player-spawn/custom and ordered custom location list. Commands use player-start-pos.hunter|runner MODE; player-start-pos.ROLE.add-custom|remove-custom X Y Z. Store world UUID/name captured from adding player's world (console primary world). Only absolute finite numbers. Reject duplicate world+XYZ; command removal matches executor world+XYZ, UI removes exact stored entry. Suggested add commands retain trailing space; hunterunter typo corrected to hunter.
- World-spawn is server first-world spawn; player-spawn uses valid personal bed/anchor (world preserved), otherwise primary world spawn. Custom coordinates are exact, not random or safety-relocated. Validate loaded world and legal coordinates/height before start; empty selected custom lists reject before any session/player reset. Both modes persist lists when switching.
- Resolve/freeze initial destinations per role in stable participant UUID order when countdown starts; modulo custom list length, surplus entries ignored. Offline countdown participants keep their reserved destination and use it on deferred reset, not newly edited config. Existing atomic teleport/rollback before destructive reset retained. Revalidate world/coordinates at activation.
- Waiting hunters get blindness and location freeze (rotation permitted), no bound tracking compass or compass actionbar/menu. They can chat/view inventories; movement/teleport/combat/block/item use/drop/inventory mutation/mount and damage are blocked. Resume countdown actionbar through existing presentation channel: 开始行动：{seconds}; ceil seconds, then normal compass provision, clean removal of owned blindness/restrictions on finish/stop/elimination/re-role.
- Existing snapshot lifecycle is extended with frozen startup settings/destinations and wait anchors; old snapshots migrate with wait0 and legacy primary-world defaults. Runtime restart resumes remaining delay using shifted session time without redoing reset or start events. Invalid state remains preserved under existing checkpoint gate.

## Tasks

1. [x] Config/data/commands: strict startup DTOs, atomic structured config, NEXT_MATCH API registry including typed mode leaves; custom chat renderer+commands with permission/lock/revision rechecks; bilingual texts and targeted RED→GREEN tests.
2. [x] Startup/persistence: preflight/frozen destination plan, role-order cycling, personal spawn fallback, deferred reset reuse, checkpoint migration and tests. Do not widen serialized public API or expose platform types.
3. [x] Delay/runtime: owned wait state, blanket event restrictions, compass/presentation gate, lifecycle/role/reconnect/restart behavior and targeted tests.
4. [x] Fresh readonly review; address Important findings with regression evidence. Independent two-version builds, four deployment/sixSDK jars+checksums, public docs and explicit runtime test limits.

## Review focus

Session failure before reset must retain choices and not consume player resources. No config edit may change frozen destinations or deadline. Missing worlds, stale removal buttons, config overlays and save failure must not partially mutate lists. Offline pending-reset and old snapshots must not silently reassign destinations or repeat startup. Compass providers/role changes cannot bypass the wait gate; cleanup must remain unconditional even if extension callbacks fail. No actual game tests or measured performance claims.

Completed2026-11-03. Both independent builds passed721checks. One fresh readonly review,2Important findings reproduced/fixed, none deferred. Four product1.0.0+sixSDK1.2.0 artifacts/hashes and171language keys verified. See ../../verification/start-setup.md. No game/runtime/performance tests, commits or publication.
