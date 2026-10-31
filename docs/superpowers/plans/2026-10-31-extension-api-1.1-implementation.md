# Extension API 1.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. Preserve the current workspace; do not commit earlier changes or publish automatically.

**Goal:** Deliver API/SDK 1.1.0 with extensible configuration, effects, text/presentation and dynamic tracking in the existing Paper/Fabric pair.

**Architecture:** Plugin-bound registries own providers and reversible layers. The Paper runtime resolves effective policies on the main thread and emits immutable snapshots; protocol 3 carries a bounded target catalog to a generic Fabric selector. Native locating and extension locating retain separate bounded execution paths and a shared authoritative selection gate.

**Tech Stack:** Gradle; Java 21 contracts/shared logic; Java 21/25 platform modules; Paper Adventure/dialog APIs; Fabric remap Loom for 1.21.11 and non-remap Loom for 26.3.

**Spec:** [合并设计](../specs/2026-10-31-extension-api-1.1-design.md). Read the complete spec before implementing any task.

## Global Constraints

- API/SDK **1.1.0**, protocol **3**, product version **0.2.0** unless separately requested.
- Minecraft **1.21.11** and **26.3**. Java root `com.caxture.manhuntuniversal`; compatibility channel/Mod ID/resource/PDC names stay unchanged.
- Preserve API 1.0 signatures and constructors; new interface members must not break old implementations. Preserve the current runtime rules and all earlier uncommitted work.
- Pure contracts in `api`, platform/Adventure contracts in `api-paper`; Java 21, no platform dependencies in `api`, `common`, `protocol`.
- Platform-neutral behavior in shared source; mappings and native packet/render differences only in version modules.
- No extension-specific Fabric mod. Custom textures are optional resource-pack references, with built-in fallback; no asset downloading or client code delivery over this protocol.
- No automatic edits to existing server language files; no automatic commits, pushes, publication or deployment to a running server.
- Existing portal POI 64 columns/2 ms soft budget and two async structure I/O permits remain unchanged. Extension jobs: at most 8 actually in flight, one current request per player, default timeout 10 seconds.
- Dynamic catalog: max 32 registered targets including built-ins; IDs max 128 ASCII, main/secondary labels each 64 code points, descriptions 128 code points, resource IDs 256 ASCII; message max 64 KiB; three equal-width cards per page.
- Run focused checks per task and build each Minecraft version independently for delivery; game/client runtime testing is a separate activity, not implied by compilation.

## Review Focus

1. Closing a higher-priority layer reveals the current lower layer, not its registration-time value; tested in Task 2 and Task 4.
2. A precompiled API 1.0 extension links without recompilation, but an extension's own strict version rejection cannot be overridden; tested/documented in Task 1 and Task 10.
3. A target removed while its menu is open cannot be selected by stale packets or old Dialog buttons; tested in Task 6 and Task 8.
4. Timed-out or cancelled real I/O keeps its execution permit until completion/acknowledged cancellation, and cannot commit after unregister; tested in Task 7.
5. An absent/rejected/resource-reloaded texture cannot produce an unusable selector or require another mod; tested in Task 9 and recorded as a manual runtime boundary in Task 10.

## File and Interface Map

The paths below use package base `com/caxture/manhuntuniversal/`. Existing 1.0 types remain at their paths. New public groups are `api.config`, `api.feature`, `api.text`, `api.presentation`, `api.tracking` and their platform-specific `api.paper` counterparts. Each group owns its immutable DTOs and functional contracts in separate small files; do not put registry implementations in public API modules.

Shared public helpers:

- `api/ExtensionHandle.java`: `boolean active()`, idempotent main-thread `void close()`; subregistrations only.
- `api/ExtensionResult.java`: generic immutable `ExtensionResult<T>(OperationStatus status, Optional<T> value, String message)`; do not change `OperationResult`'s match-oriented record constructor.
- New entry methods on `ManhuntUniversalApi`: `ConfigurationRegistry configuration()`, `FeatureRegistry features()`, `TextRegistry texts()`, `TrackingRegistry tracking()` with capability-gated default unsupported behavior. Paper entry adds `PresentationRegistry presentation()` and covariant Paper text access.
- `ConfigurationRegistry`: `list()`, `find(String key)`, typed `register(owner, definition)`, `update(owner, key, value, expectedConfigRevision)`, `lock(owner, key, priority, reason)`, `override(owner, key, valueProvider, priority)`. Return snapshots or `ExtensionResult`/handles, never internal config objects.
- `FeatureRegistry`: `list()`, `policy(owner, featureId, priority, FeaturePolicy)`. Policy returns `DEFAULT` or `SKIP_CORE`; context is immutable and actor may be absent.
- `TextRegistry`: `register(owner, definition)`, `override(owner, key, priority, TextProvider)`. Paper counterpart handles Adventure components and receiver context without making the pure interface depend on Paper.
- `PresentationRegistry`: `intercept(owner, PresentationSelector, priority, PresentationProvider)`, `appendResult(owner, ResultSection)`, `refresh(RefreshRequest)`; request IDs/channels and privacy are explicit.
- `TrackingRegistry`: `list()`, `register(owner, TrackingTargetDefinition)`, `replaceLocator(owner, targetId, priority, TrackingLocator)`, `snapshot(UUID playerId)`, `select(TrackingMutation, String targetId)`, `clear(TrackingMutation)`, `refresh(TrackingMutation)` and `invalidate(owner, TrackingInvalidation)`.
- `TrackingLocator`: `CompletionStage<LocateResult> locate(LocateRequest request, CancellationToken cancellation)`. Request/result contain pure values only. Built-in delegation is explicit and bypasses the replacement layer to avoid recursion.

`owner` is always the existing opaque `ExtensionRegistration`; signatures use that type, not a string or caller-created token. Add explicit DTOs for all named requests/snapshots/policies, copying collections defensively. Use the spec for their fields and validation; preserve old DTOs intact. New error detail can live in new result data without extending the set of statuses unexpectedly returned by old API methods.

### Task 1: Compatible contracts and owned subregistrations

**Files:** Modify `api/src/main/java/com/caxture/manhuntuniversal/api/{ManhuntUniversalApi,Capability}.java`, `api-paper/src/main/java/com/caxture/manhuntuniversal/api/paper/PaperManhuntUniversalApi.java`; create the new contract groups above; modify `paper/shared/src/main/java/com/caxture/manhuntuniversal/extension/{ExtensionRegistry,PaperExtensionService}.java`; create `extension/OwnedRegistry.java`. Test `api/.../Api11CompatibilityTest.java` and `paper/shared/src/test/.../extension/OwnedRegistryTest.java`.

**Produces:** New entry interfaces, immutable data and plugin-owned resource lifecycle. Registry service implementations can be introduced in their owning tasks; do not advertise capabilities before they are wired.

- [x] Compile a small consumer against the existing 1.0.0 SDK before modifying it; preserve that compiled fixture for a binary linking test. Assert current constructors/methods still link on the future implementation without recompiling the fixture.
- [x] Add failing tests for independent child close, parent close/owner disable closing all children once, forged owner rejection, wrong thread, conflicting priorities and reentrant mutation.
- [x] Run `:api:test :api-paper:test :paper-1.21.11:test --tests '*OwnedRegistryTest'` as separate commands where the test filter would otherwise affect other modules; observe expected missing-contract/test failures.
- [x] Implement the contract groups and owned registry lifecycle. Keep existing control-domain ownership unchanged, use default entry methods for binary compatibility, and keep new capabilities unadvertised until integration.
- [x] Rerun focused tests and the binary fixture. Record the checkpoint without committing unrelated work.

### Task 2: Structured configuration and effective-value transitions

**Files:** Create `extension/config/{ConfigurationRegistryService,ConfigurationLayers,CoreConfigurationBindings}.java`; modify `config/{ConfigurationService,ConfigurationValues}.java`, `command/{MhuCommand,CommandArguments}.java`, `ManhuntUniversalPlugin.java`; tests `extension/config/ConfigurationRegistryTest.java`, `config/ConfigurationTransitionTest.java` and relevant command tests.

**Consumes:** Task 1 ownership/results and the spec's typed definition/storage contracts.
**Produces:** A single config mutation/effective-value gateway used by commands, clicks, API and overrides.

- [x] Add failing tests for group/leaf lookup and interactive suggestions; parent locks affecting leaves; whole-map validation failure preserving every old value; save failure preserving reservations; revision conflict.
- [x] Add tests for an override hiding a changed saved value, closing it revealing that new value, and an observer throwing after successful persistence still reporting a committed update.
- [x] Run `:paper-1.21.11:test --tests '*ConfigurationRegistryTest' --tests '*ConfigurationTransitionTest'` and observe RED.
- [x] Implement immutable typed definitions including group schemas, atomic storage delegates and effective/next-match snapshots. Register every existing boolean/int/decimal/map setting, preserving ranges and timing.
- [x] Route config commands, completion, localized list rows and stale clickable buttons through this gateway. Effective transitions dispatch existing reservation, hide/force-random, unlock and display-refresh effects after successful commit only.
- [x] Rerun focused tests and existing configuration/selection command tests; confirm no runtime resource or config migration rewrites existing language files.

### Task 3: Core feature policies

**Files:** Create `extension/feature/{FeatureRegistryService,CoreFeatureIds}.java`; modify listeners in `optimization/{OreFortuneListener,BlazeRodLootingListener,PiglinBarterListener,HunterEndAccessListener}.java`, `listener/{BlazeSpawnerProtectionListener,NetherPortalProtectionListener}.java`, tracking access gate and plugin wiring. Tests `extension/feature/FeaturePolicyTest.java` plus current listener tests.

**Consumes:** Tasks 1–2 handles and effective config; **produces:** stable feature IDs and one policy decision per operation before the corresponding core effect.

- [x] Test DEFAULT/SKIP_CORE for each registered feature, absent actor/environment operations, RUNNING boundary, higher-priority policy and exception fallback to core.
- [x] Test that a custom drop listener and SKIP_CORE yield one custom result, and that skipping core protection does not uncancel another plugin's event.
- [x] Observe RED with focused listener/policy tests, implement the gate at each existing effect boundary, then run those checks GREEN.
- [x] Verify close/disable restores current effective core behavior and never blocks cleanup/restoration.

### Task 4: Text layers and unified presentation

**Files:** Create `extension/text/TextRegistryService.java`, `extension/presentation/{PresentationRegistryService,PresentationDispatcher,ResultSections}.java`; modify `i18n/LanguageService.java`, `presentation/{VanillaMatchPresentation,ResultReportComposer,PresentationSoundCue}.java`, `command/TeamSelectionPresenter.java` and direct output sites discovered by `rg`. Tests `extension/text/TextRegistryTest.java`, `extension/presentation/PresentationRegistryTest.java`.

**Consumes:** Ownership/config effective transitions; **produces:** stable output IDs/channels, filtered contexts, structured result snapshots and scoped refresh.

- [x] Test extension→external→built-in text fallback, prefix switch, missing placeholders, lower layer restoration and per-recipient localization.
- [x] Test result append/order/replace/cancel without changing winner or cleanup; hide-role filtering before ordinary provider callbacks, with explicit authorized visibility ownership handled separately.
- [x] Test that refreshing tracking text never invokes a locator and repeated scoped refreshes coalesce; test cancelled output still permits closing an existing screen and restoring a player's state.
- [x] Observe RED, implement dispatch over all core output categories, and retain legacy string parsing plus Adventure rich output in api-paper.
- [x] Run focused tests; audit remaining `sendMessage`, `showTitle`, Actionbar/sound, item presentation and menu-open calls for a deliberate dispatcher route or a documented mandatory-cleanup exemption.

### Task 5: Per-observer identity presentation

**Files:** Modify `presentation/IdentityDisplayService.java`; create shared `presentation/{ObserverIdentityPresentation,ObserverIdentityState}.java` and version-specific `paper/{1.21.11,26.3}/src/main/java/com/caxture/manhuntuniversal/presentation/ObserverIdentityPackets.java`; tests `presentation/ObserverIdentityTest.java` and existing lobby identity tests.

**Consumes:** Task 4 observer/target contexts and authorized visibility control; **produces:** independent Tab/Nametag output with two native adapters.

- [x] Test two observers receiving distinct names/colors/visibility/sort for one target, real player name retained, sidebar untouched and default hidden behavior preserved.
- [x] Test role changes, reconnect, hide toggle, unregister, stale cached packet and adapter failure; no failure path may broadcast hidden role text.
- [x] Observe RED and verify packet constructor/member signatures separately against each version's local mappings before implementation.
- [x] Implement main-thread snapshots, version adapters and restoration without invoking extensions on packet/network threads or adding a protocol dependency.
- [x] Run targeted checks and compile both Paper modules. Record that actual client packet behavior still requires a runtime check.

### Task 6: Dynamic target catalog and authoritative availability

**Files:** Create `extension/tracking/{TrackingRegistryService,TrackingTargetCatalog,TrackingAvailabilityService}.java`; modify `tracking/{TrackingSessionState,TrackingTargetId,TrackingSelectionPresenter,TrackingDialogWindow,PaperDialogTrackingSelectionPresenter,StructureUnlockAnnouncements,CompassTrackingService}.java`; tests `extension/tracking/TrackingCatalogTest.java`, `tracking/DynamicTrackingSelectionTest.java`.

**Consumes:** Config/text/feature/presentation services; **produces:** stable string target IDs, registry revision, recipient menu snapshots, selection authorization and public tracking events.

- [x] Test new `example:checkpoint`, duplicate/invalid IDs, max 32 targets including three core entries, registered target removal and immutable definitions.
- [x] Test per-player task lock, disabled/hidden options, timed unlock/config/extension source events without per-second event spam, selection changes and role/elimination checks.
- [x] Test an old Dialog callback after target removal or registry revision change cannot select it; all-disabled targets remove the runner compass while hunter default tracking remains unchanged.
- [x] Observe RED; wrap built-ins in catalog definitions, convert internal selection state to string IDs through a central adapter, and keep old internal enums only where useful to native algorithms.
- [x] Use the same resolved menu snapshot for Dialog and Fabric routing. Add events in api-paper and immutable queries in api; run current unlock and role-change tests along with focused new tests.

### Task 7: Bounded custom locating and snapshots

**Files:** Create `extension/tracking/{TrackingLocateCoordinator,TrackingRequestState,CoreTrackingLocators}.java`; modify `tracking/CompassTrackingService.java` and integration with existing structure/portal engines; tests `extension/tracking/TrackingLocateCoordinatorTest.java`.

**Consumes:** Task 6 catalog/authorization; **produces:** main-thread provider entry, async completion polling, logical/guidance snapshots, scoped invalidation and optional exact-key sharing.

- [x] Test an immediate checkpoint result, explicit replacement/delegation of fortress locating and unchanged nearest-blaze-spawner fallback.
- [x] Test logical destination vs cross-dimension portal guidance; distance-only refresh does not call providers.
- [x] Test 8 actual in-flight jobs, 10-second timeout using an injected clock, queued work, one current request per player, complete-key sharing only, cancellation of one subscriber retaining others, and disposal not releasing real I/O permits early.
- [x] Test provider exception/null result, failed world lookup, logout, elimination, world unload, new session, registry/policy revision change and owner disable before completion; stale results never commit.
- [x] Observe RED; implement immutable requests/results, cancellation signaling, main-thread polling and per-provider failure reporting. Do not run Bukkit reads on an executor or block the main thread on a future.
- [x] Run focused coordinator tests and existing POI/probe/index tests; preserve the native 64-column/2 ms soft budget and two-load permits separately.

### Task 8: Protocol 3 and server routing

**Files:** Modify `protocol/.../{ProtocolConstants,ProtocolMessage,ProtocolCodec}.java`; create `protocol/.../{TrackingTargetDescriptor,DynamicTrackingOpen,DynamicTrackingUpdate,DynamicTrackingChoose}.java`; retain hello and close framing; modify `paper/shared/src/main/java/com/caxture/manhuntuniversal/client/{TrackingClientPublisher,PaperTrackingClientPublisher}.java` and `tracking/FabricAwareTrackingSelectionPresenter.java`. Tests `protocol/.../DynamicTrackingCodecTest.java`, Paper `client/DynamicTrackingRoutingTest.java`.

**Consumes:** Task 6 menu snapshots and authoritative choice handler; **produces:** bounded data-only V3 messages and compatibility-safe fallback.

- [x] Test round trips, exact hello bytes remaining unchanged, versions 2/3 mismatch, error direction, duplicate IDs, malformed UTF-8, truncated length fields, 32/33 options, Unicode codepoint limits and 64 KiB payload boundaries.
- [x] Test the server never sends dynamic messages before a V3 handshake, and old/no-mod clients get the same dynamic target list via Dialog.
- [x] Test stale authorization/directory revision, unknown or newly disabled ID, fabricated unlocked client state and owner unregister cannot execute selection.
- [x] Observe RED; implement length-prefixed bounded UTF-8 reads with allocation checks, complete snapshot updates and existing session/authorization protection. Use strict parsing rather than silently truncating lists.
- [x] Run protocol and routing tests; preserve the original bootstrap handshake shape and namespace, with no attempt to push V3 menus to V2 clients.

### Task 9: Generic Fabric selector and resource fallback

**Files:** Modify shared `fabric/shared/src/main/java/com/caxture/manhuntuniversal/fabric/ui/{CompassSelectorOption,CompassSelectorLayout,CompassSelectorCommit}.java`; create `SelectorPageState.java` and `SelectorResourceStyle.java`; modify both versions' `fabric/{version}/src/client/java/com/caxture/manhuntuniversal/fabric/{network/ClientProtocolController,network/MainChannelPayload,ui/CompassTrackingSelectionScreen}.java`; add generic fallback resource and localized paging labels. Tests shared `DynamicCompassSelectorTest.java` and `SelectorResourceFallbackTest.java`.

**Consumes:** Protocol V3 descriptor lists; **produces:** one installed core mod capable of future server-defined targets, no addon code needed.

- [x] Test zero/one/three/four/32 options, ordering ties by ID, equal page widths, page correction after removal, long labels, tiny window hit testing and no click outside visible cards.
- [x] Test lock countdown, doubled center font policy, disabled hover/cursor, update preserving valid selection, Esc/close and stale revision selection handling.
- [x] Test texture present/absent/rejected/reloaded fallback via an injected resource resolver; texture miss must not prevent target selection. Keep resource pack acceptance outside mod control.
- [x] Observe RED for pure shared tests; implement generic descriptors/paging and both version render/input adapters. Main channel codecs must accept the same bounded payload limit as protocol 3.
- [x] Build Fabric 1.21.11 through remap Loom and Fabric 26.3 through non-remap Loom independently; record remaining visual/runtime checks rather than claiming screenshot or game verification.

### Task 10: Wire capabilities, guides and release verification

**Files:** Modify `ManhuntUniversalPlugin.java`, `extension/PaperExtensionService.java`, `gradle/libs.versions.toml`, `docs/extension-api.md`, `docs/{lobby-config,tracking-performance,structure-tracker-unlock}.md`, `README.md`, `AGENTS.md`, built-in language resources and API module READMEs. Add portable SDK examples for config groups, feature takeover, text/result, observer display and dynamic checkpoint target.

**Consumes:** Tasks 1–9; **produces:** a complete advertised API 1.1.0 and two matched Paper/Fabric delivery pairs.

- [x] Add integration checks through the public service for a plugin registering config+target+text, opening both presentation routes, then disabling and restoring the current core behavior. Ensure no earlier feature disappears or remains double-active.
- [x] Wire dependencies without cycles, advertise only implemented capabilities, update SDK version to 1.1.0, preserve existing control registrations and maintain all cleanup paths even when extension callbacks throw.
- [x] Update examples to compatible version ranges/capability detection; compile examples against the SDK. Document storage ownership, config effective timing, privacy, thread rules, async cancellation, fallback, limits and resource-pack creation/hosting responsibility.
- [x] Run `.\gradlew.bat build '-PmcVersion=1.21.11' --offline --console=plain` and independently `.\gradlew.bat build '-PmcVersion=26.3' --offline --console=plain`; use online resolution only if a required cached dependency is absent.
- [x] Inspect JAR metadata, class versions, protocol version/IDs, entry classes, language key/placeholder consistency, default configuration, bundled API classes and absence of platform references in pure API. Verify the 1.0 binary fixture again against final 1.1 artifacts.
- [x] Refresh four deployment JARs and six SDK JARs at version 1.1.0, remove obsolete SDK deployment copies only after verified replacement, write/verify checksum manifest and a machine-readable build report.
- [x] Run `git diff --check`; review implementation against every spec section. Record build/test evidence separately from unperformed game, resource-pack and packet runtime checks. Do not automatically publish or commit the combined workspace.

## Execution Order and Handoff

Implement Tasks 1→2→3→4→5→6→7→8→9→10 in this session. Each task's output is a reviewable boundary, but capability advertising and the SDK version change wait until full wiring. If the work spans turns, preserve checked task state and resume without treating intermediate code as a released API.

Completed on 2026-11-01. API/SDK 1.1.0 and protocol 3 are implemented; product version remains 0.2.0. Both independently selected builds passed (545 targeted checks), compile-checked examples and 1.0 binary linking passed, and ten delivery artifacts/checksums were refreshed. See ../../verification/api-1.1.md for evidence and the explicit unperformed game/resource-pack/packet runtime checks. No automatic commits or publication. File names in the tasks are organizational guidance; implementations use equivalent consolidated services where that avoids duplicated state.
