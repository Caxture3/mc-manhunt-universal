package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.config.BooleanConfigKey;
import com.caxture.manhuntuniversal.config.ConfigurationService;
import com.caxture.manhuntuniversal.game.GameManager;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.Role;
import com.caxture.manhuntuniversal.game.SessionState;
import com.caxture.manhuntuniversal.game.TrackingSettingsSnapshot;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import com.caxture.manhuntuniversal.player.PendingPlayerState;
import com.caxture.manhuntuniversal.protocol.ProtocolNamespace;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CompassTrackingService {
    private static final int OFFHAND_SLOT = 40;
    private static final int PREFERRED_HOTBAR_SLOT = 8;
    private static final List<Structure> VILLAGES = List.of(
            Structure.VILLAGE_PLAINS,
            Structure.VILLAGE_DESERT,
            Structure.VILLAGE_SAVANNA,
            Structure.VILLAGE_SNOWY,
            Structure.VILLAGE_TAIGA);

    private final JavaPlugin plugin;
    private final Server server;
    private final GameManager gameManager;
    private final ConfigurationService configuration;
    private final LanguageService languages;
    private final TrackingSelectionPresenter selectionPresenter;
    private final PendingPlayerState pendingPlayers;
    private final Clock clock;
    private final org.bukkit.NamespacedKey markerKey;
    private final org.bukkit.NamespacedKey sessionKey;
    private final org.bukkit.NamespacedKey kindKey;
    private final TrackingSearchQueue searchQueue = new TrackingSearchQueue();
    private final SharedSearchCache<StructureSearchKey, PortalPoint> sharedStructures = new SharedSearchCache<>(
            512, 300_000_000_000L, 15_000_000_000L, System::nanoTime, searchQueue::invalidate);
    private final PortalSearchEngine portalEngine;
    private final PortalInvalidationBuffer pendingPortalChanges = new PortalInvalidationBuffer();
    private long searchCycle;
    // Retained through finish/start for same-match role reassignment; holds no player/world objects.
    private final StructureUnlockAnnouncements unlockAnnouncements = new StructureUnlockAnnouncements();
    private final TrackingSelectionAuthorizationRegistry selectionAuthorizations =
            new TrackingSelectionAuthorizationRegistry();
    private final Map<UUID, StaticSearchState> staticSearches = new HashMap<>();
    private final Map<UUID, PortalSearchState> portalSearches = new HashMap<>();
    private final Map<UUID, TrackingTargetId> desiredTargets = new HashMap<>();
    private final Set<UUID> actionBarVisible = new HashSet<>();
    private final Set<UUID> noSlotWarned = new HashSet<>();
    private TrackingSessionState state;
    private TrackingSettingsSnapshot settings;
    private BukkitTask searchTicker;
    private long portalVersion;
    private int searchTicks;

    public CompassTrackingService(
            JavaPlugin plugin,
            GameManager gameManager,
            ConfigurationService configuration,
            LanguageService languages,
            TrackingSelectionPresenter selectionPresenter,
            PendingPlayerState pendingPlayers,
            Clock clock
    ) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.gameManager = gameManager;
        this.configuration = configuration;
        this.languages = languages;
        this.selectionPresenter = selectionPresenter;
        this.pendingPlayers = pendingPlayers;
        this.clock = clock;
        this.portalEngine = new PortalSearchEngine((worldId, chunkX, chunkZ) -> {
            World world = server.getWorld(worldId);
            if (world == null) throw new IllegalStateException("Portal search world has unloaded: " + worldId);
            return PortalLocator.readChunk(world, chunkX, chunkZ);
        }, task -> server.getScheduler().runTaskAsynchronously(plugin, task), System::nanoTime,
                exception -> plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Portal tracking query failed", exception));
        // Keep existing bound-item identifiers independent of the plugin's display name.
        this.markerKey = new org.bukkit.NamespacedKey(ProtocolNamespace.NAMESPACE, "tracking_compass");
        this.sessionKey = new org.bukkit.NamespacedKey(ProtocolNamespace.NAMESPACE, "tracking_session");
        this.kindKey = new org.bukkit.NamespacedKey(ProtocolNamespace.NAMESPACE, "tracking_kind");
    }

    public void start(GameSession session) {
        stopSearchTicker();
        clearRuntimeState();
        state = new TrackingSessionState(session.id());
        settings = session.settings().trackingSettings();
        unlockAnnouncements.begin(session.id(), availability(session,
                session.startedAt().orElseGet(() -> Instant.now(clock))));
        for (var participant : session.participants()) {
            Player player = onlinePlayer(participant.id());
            if (player != null && !pendingPlayers.needsReset(participant.id())) {
                state.recordPosition(participant.id(), position(player.getLocation()));
            }
        }
        tick(session);
        if (gameManager.activeSession().orElse(null) != session || session.state() != SessionState.RUNNING) return;
        searchTicker = server.getScheduler().runTaskTimer(plugin, this::runSearchTick, 1L, 1L);
    }

    public List<String> playersWithoutSpace(GameSession session, List<Player> players) {
        players.forEach(this::removeMarkedCompasses);
        return players.stream()
                .filter(player -> expectedKind(session, player.getUniqueId()).isPresent())
                .filter(player -> availableSlot(player.getInventory()) < 0)
                .map(Player::getName)
                .toList();
    }

    public void tick(GameSession session) {
        if (!isTracking(session)) {
            return;
        }
        for (var participant : session.participants()) {
            Player player = onlinePlayer(participant.id());
            if (player != null && !pendingPlayers.needsReset(participant.id())) {
                state.recordPosition(participant.id(), position(player.getLocation()));
            }
        }
        Instant now = Instant.now(clock);
        TrackingAvailability availability = availability(session, now);
        List<TrackingTargetId> newlyUnlocked = unlockAnnouncements.advance(availability);
        for (var participant : session.participants()) {
            Player player = onlinePlayer(participant.id());
            if (player == null) {
                continue;
            }
            if (session.isRunnerAlive(participant.id()) && !pendingPlayers.needsReset(participant.id())) {
                for (TrackingTargetId target : newlyUnlocked) {
                    languages.send(player, "tracking.selection.unlocked",
                            Map.of("target", languages.text(player, target.languageKey())));
                }
            }
            if (session.isRunnerAlive(participant.id())
                    && !StructureUnlockPolicy.allows(state.runnerSelection(participant.id()), availability)) {
                state.setRunnerSelection(participant.id(), TrackingTargetId.NONE);
                invalidateStructureSearch(participant.id());
                languages.send(player, "tracking.selection.cleared");
            }
            TrackingCompassKind kind = expectedKind(session, participant.id()).orElse(null);
            if (!TrackingAccessPolicy.mayUse(
                    true, pendingPlayers.needsReset(participant.id()), kind != null)) {
                invalidateParticipant(participant.id());
                closeSelection(player);
                removeMarkedCompasses(player);
                clearActionBar(player);
                noSlotWarned.remove(participant.id());
                continue;
            }
            if (!ensureCompass(session, player, kind) || kind == null) {
                closeSelection(player);
                clearActionBar(player);
                continue;
            }
            selectionAuthorizations.current(participant.id()).ifPresent(authorization -> {
                if (canSelect(session, player)) {
                    selectionPresenter.update(player, session.id(), authorization.authorizationId(),
                            state.runnerSelection(participant.id()), availability);
                } else closeSelection(player);
            });
            TrackingDisplay display;
            if (kind == TrackingCompassKind.HUNTER) {
                display = hunterDisplay(session, player);
            } else {
                TrackingTargetId desired = state.runnerSelection(participant.id());
                TrackingTargetId previous = desiredTargets.put(participant.id(), desired);
                if (previous != desired) {
                    invalidateStructureSearch(participant.id());
                    desiredTargets.put(participant.id(), desired);
                }
                display = desired == TrackingTargetId.NONE
                        ? status("tracking.actionbar.runner-select-target")
                        : staticDisplay(player, desired, now);
            }
            applyDirection(player, display.direction(), display.invalidDirectionWorld());
            showActionBarIfHeld(session, player, kind, display);
        }
    }

    public void configurationChanged(BooleanConfigKey key) {
        if (key != BooleanConfigKey.RUNNER_COMPASS_ENABLED
                && key != BooleanConfigKey.HUNTER_COMPASS_ENABLED) {
            return;
        }
        GameSession session = runningSession().orElse(null);
        if (session == null) {
            return;
        }
        List<UUID> affected = key == BooleanConfigKey.RUNNER_COMPASS_ENABLED
                ? session.runnerIds() : session.hunterIds();
        affected.forEach(this::invalidateParticipant);
        if (key == BooleanConfigKey.RUNNER_COMPASS_ENABLED
                && !configuration.get(BooleanConfigKey.RUNNER_COMPASS_ENABLED)) {
            session.runnerIds().forEach(playerId -> {
                Player player = onlinePlayer(playerId);
                if (player != null) {
                    closeSelection(player);
                } else {
                    selectionAuthorizations.invalidate(playerId);
                    selectionPresenter.invalidate(playerId);
                }
            });
        }
        tick(session);
    }

    public void playerChangedWorld(Player player, Location from) {
        if (state == null) {
            return;
        }
        recordPosition(player.getUniqueId(), from);
        recordCurrentPosition(player);
        invalidateParticipant(player.getUniqueId());
        runningSession().ifPresent(this::tick);
    }

    public void playerTeleported(Player player, Location from, Location to) {
        if (state == null) {
            return;
        }
        recordPosition(player.getUniqueId(), from);
        recordPosition(player.getUniqueId(), to);
        if (from != null && from.getWorld() != null
                && to != null && to.getWorld() != null
                && TrackingPolicy.shouldInvalidateForTeleport(position(from), position(to))) {
            invalidateParticipant(player.getUniqueId());
        }
    }

    public void playerQuit(Player player) {
        recordCurrentPosition(player);
        invalidateParticipant(player.getUniqueId());
        selectionAuthorizations.invalidate(player.getUniqueId());
        selectionPresenter.invalidate(player.getUniqueId());
        clearActionBar(player);
    }

    public void recordCurrentPosition(Player player) {
        if (state != null) {
            state.recordPosition(player.getUniqueId(), position(player.getLocation()));
        }
    }

    public void playerJoined(Player player, GameSession activeSession) {
        if (activeSession == null
                || activeSession.state() != SessionState.RUNNING
                || activeSession.participant(player.getUniqueId()).isEmpty()
                || state == null
                || !state.sessionId().equals(activeSession.id())
                || pendingPlayers.needsReset(player.getUniqueId())) {
            invalidateParticipant(player.getUniqueId());
            closeSelection(player);
            removeMarkedCompasses(player);
            clearActionBar(player);
            return;
        }
        state.recordPosition(player.getUniqueId(), position(player.getLocation()));
        invalidateParticipant(player.getUniqueId());
        tick(activeSession);
    }

    public void openRunnerSelection(Player player) {
        GameSession session = runningSession().orElse(null);
        if (!canSelect(session, player)) {
            return;
        }
        TrackingSelectionAuthorizationRegistry.Authorization authorization =
                selectionAuthorizations.issue(player.getUniqueId(), session.id());
        selectionPresenter.show(player, session.id(), authorization.authorizationId(),
                state.runnerSelection(player.getUniqueId()), availability(session, Instant.now(clock)),
                selected -> applyRunnerSelection(
                        player, session.id(), authorization.authorizationId(), selected),
                () -> cancelRunnerSelection(player, session.id(), authorization.authorizationId()));
    }

    public void structureUnlockTimesChanged() {
        runningSession().ifPresent(this::tick);
    }

    public void cancelRunnerSelection(Player player, UUID sessionId, long authorizationId) {
        if (selectionAuthorizations.consume(player.getUniqueId(), sessionId, authorizationId)) {
            selectionPresenter.close(player, sessionId, authorizationId);
        }
    }

    public void finish(GameSession session) {
        stopSearchTicker();
        for (var participant : session.participants()) {
            Player player = onlinePlayer(participant.id());
            if (player != null) {
                closeSelection(player);
                removeMarkedCompasses(player);
                clearActionBar(player);
            }
        }
        clearRuntimeState();
        state = null;
        settings = null;
    }

    public void shutdown() {
        stopSearchTicker();
        unlockAnnouncements.clear();
        for (Player player : server.getOnlinePlayers()) {
            closeSelection(player);
            removeMarkedCompasses(player);
            clearActionBar(player);
        }
        clearRuntimeState();
        state = null;
        settings = null;
    }

    public void cleanupStale(Player player) {
        GameSession session = runningSession().orElse(null);
        boolean participant = session != null
                && session.participant(player.getUniqueId()).isPresent()
                && !session.isEliminatedParticipant(player.getUniqueId());
        boolean stateMatches = session != null && state != null
                && state.sessionId().equals(session.id());
        if (TrackingAccessPolicy.cleanupRequired(
                session != null,
                participant,
                stateMatches,
                pendingPlayers.needsReset(player.getUniqueId()))) {
            invalidateParticipant(player.getUniqueId());
            closeSelection(player);
            removeMarkedCompasses(player);
            clearActionBar(player);
            noSlotWarned.remove(player.getUniqueId());
        }
    }

    public void runnerEliminated(Player player) {
        invalidateParticipant(player.getUniqueId());
        closeSelection(player);
        removeMarkedCompasses(player);
        clearActionBar(player);
        noSlotWarned.remove(player.getUniqueId());
        runningSession().ifPresent(this::tick);
    }

    public boolean isMarkedCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(markerKey, PersistentDataType.BYTE);
    }

    public boolean isCurrentCompassFor(Player player, ItemStack item) {
        GameSession session = runningSession().orElse(null);
        TrackingCompassKind expected = session == null
                ? null : expectedKind(session, player.getUniqueId()).orElse(null);
        return session != null && isCurrentCompass(item, session.id(), expected);
    }

    public boolean activeParticipant(Player player) {
        GameSession session = runningSession().orElse(null);
        return session != null
                && session.participant(player.getUniqueId()).isPresent()
                && !session.isEliminatedParticipant(player.getUniqueId())
                && state != null
                && state.sessionId().equals(session.id())
                && !pendingPlayers.needsReset(player.getUniqueId());
    }

    public Optional<UUID> activeSessionId() {
        return state == null ? Optional.empty() : Optional.of(state.sessionId());
    }

    public void ensureNextTick(Player player) {
        server.getScheduler().runTask(plugin, () -> {
            GameSession session = runningSession().orElse(null);
            if (session != null
                    && session.participant(player.getUniqueId()).isPresent()
                    && !session.isEliminatedParticipant(player.getUniqueId())
                    && !pendingPlayers.needsReset(player.getUniqueId())) {
                ensureCompass(session, player,
                        expectedKind(session, player.getUniqueId()).orElse(null));
            } else {
                cleanupStale(player);
            }
        });
    }

    public void applyRunnerSelection(
            Player player,
            UUID sessionId,
            long authorizationId,
            TrackingTargetId selected
    ) {
        if (selected == null || selected != TrackingTargetId.NONE && !selected.selectableByRunner()
                || selectionAuthorizations.current(player.getUniqueId()).filter(authorization ->
                        authorization.sessionId().equals(sessionId)
                                && authorization.authorizationId() == authorizationId).isEmpty()) {
            return;
        }
        GameSession session = runningSession().orElse(null);
        if (!canSelect(session, player) || !session.id().equals(sessionId)) {
            cancelRunnerSelection(player, sessionId, authorizationId);
            return;
        }
        // A forged or stale UI submission must never consume the valid menu capability.
        if (!StructureUnlockPolicy.allows(selected, availability(session, Instant.now(clock)))) return;
        if (!selectionAuthorizations.consume(player.getUniqueId(), sessionId, authorizationId)) return;
        selectionPresenter.close(player, sessionId, authorizationId);
        TrackingTargetId result = state.setRunnerSelection(player.getUniqueId(), selected);
        invalidateStructureSearch(player.getUniqueId());
        languages.send(player, result == TrackingTargetId.NONE
                ? "tracking.selection.cleared" : "tracking.selection.changed",
                result == TrackingTargetId.NONE ? Map.of()
                        : Map.of("target", languages.text(player, result.languageKey())));
        tick(session);
    }

    private boolean canSelect(GameSession session, Player player) {
        return session != null
                && configuration.get(BooleanConfigKey.RUNNER_COMPASS_ENABLED)
                && configuration.values().runnerStructureUnlockTimes().anyEnabled()
                && !pendingPlayers.needsReset(player.getUniqueId())
                && session.isRunnerAlive(player.getUniqueId())
                && state != null
                && state.sessionId().equals(session.id())
                && isCurrentCompass(heldCompass(player), session.id(), TrackingCompassKind.RUNNER);
    }

    private TrackingDisplay hunterDisplay(GameSession session, Player hunter) {
        TrackingPosition origin = position(hunter.getLocation());
        List<TrackingPlayerCandidate> candidates = new ArrayList<>();
        for (UUID runnerId : session.livingRunnerIds()) {
            Player runner = onlinePlayer(runnerId);
            boolean online = runner != null;
            Optional<TrackingPosition> actual = online
                    ? Optional.of(position(runner.getLocation()))
                    : state.latestPosition(runnerId);
            candidates.add(new TrackingPlayerCandidate(
                    runnerId,
                    session.participant(runnerId).map(participant -> participant.name()).orElse("?"),
                    online,
                    actual,
                    state.lastPosition(runnerId, hunter.getWorld().getUID())));
        }
        HunterTrackingTarget target = TrackingPolicy.nearestRunner(origin, candidates).orElse(null);
        if (target == null) {
            return new TrackingDisplay("tracking.actionbar.runner-unavailable", Map.of(), null, null);
        }
        World actualWorld = target.player().actualPosition()
                .map(TrackingPosition::worldId).map(server::getWorld).orElse(null);
        Location direction = location(target.direction());
        Map<String, String> placeholders = target.liveSameWorld()
                ? Map.of(
                "target", target.player().name(),
                "distance", Long.toString(TrackingPolicy.roundedDisplayDistance(
                        origin, target.direction())),
                "unit", languages.text(hunter, "unit.blocks"),
                "dimension", dimensionName(hunter, actualWorld))
                : Map.of(
                "target", target.player().name(),
                "dimension", dimensionName(hunter, actualWorld));
        return new TrackingDisplay(
                target.liveSameWorld()
                        ? "tracking.actionbar.hunter-live"
                        : "tracking.actionbar.hunter-last-known",
                placeholders,
                direction,
                null);
    }

    private TrackingDisplay staticDisplay(Player player, TrackingTargetId target, Instant now) {
        World targetWorld = chooseTargetWorld(player, target.targetWorldKind().orElseThrow());
        if (targetWorld == null) {
            return new TrackingDisplay(
                    "tracking.actionbar.not-found",
                    targetPlaceholders(player, target, null),
                    null,
                    null);
        }
        StaticSearchState search = staticSearches.get(player.getUniqueId());
        Location movementReference = movementReference(player, targetWorld);
        boolean movedEnough = search != null
                && !search.searching()
                && TrackingPolicy.shouldRetarget(
                position(search.origin()), position(movementReference),
                search.searchedAt(), now, settings);
        boolean needsSearch = search == null
                || search.target() != target
                || !search.worldId().equals(targetWorld.getUID())
                || movedEnough;
        if (needsSearch) {
            Location origin = movedEnough ? movementReference : searchOrigin(player, targetWorld);
            enqueueStaticSearch(player, target, targetWorld, origin, now);
            search = staticSearches.get(player.getUniqueId());
        }
        if (search == null || (search.result() == null && search.searching())) {
            return new TrackingDisplay(
                    "tracking.actionbar.searching",
                    targetPlaceholders(player, target, targetWorld),
                    null,
                    targetWorld);
        }
        if (search.result() == null) {
            return new TrackingDisplay(
                    "tracking.actionbar.not-found",
                    targetPlaceholders(player, target, targetWorld),
                    null,
                    targetWorld);
        }
        if (player.getWorld().equals(targetWorld)) {
            return located(player, target, search.result(), targetWorld);
        }
        PortalSearchState portal = ensurePortalSearch(player, now);
        if (portal != null && portal.result() != null) {
            return new TrackingDisplay(
                    "tracking.actionbar.cross-dimension",
                    Map.of(
                            "target", languages.text(player, target.languageKey()),
                            "dimension", dimensionName(player, targetWorld)),
                    portal.result(),
                    null);
        }
        String key = portal != null && portal.searching()
                ? "tracking.actionbar.searching-portal"
                : "tracking.actionbar.no-portal";
        return new TrackingDisplay(
                key,
                targetPlaceholders(player, target, targetWorld),
                null,
                targetWorld);
    }

    private void enqueueStaticSearch(
            Player player,
            TrackingTargetId target,
            World world,
            Location origin,
            Instant now
    ) {
        TrackingSearchToken token = state.newSearch(player.getUniqueId(), target, world.getUID());
        StaticSearchState previous = staticSearches.get(player.getUniqueId());
        Location oldResult = previous != null
                && previous.target() == target
                && previous.worldId().equals(world.getUID())
                ? previous.result() : null;
        staticSearches.put(player.getUniqueId(), new StaticSearchState(
                target, world.getUID(), origin, oldResult, now, true, token));
        StructureSearchKey key = new StructureSearchKey(world.getUID(), target,
                origin.getX(), origin.getY(), origin.getZ(), searchRadiusChunks());
        var work = sharedStructures.request(player.getUniqueId(), key, result -> {
            World liveWorld = server.getWorld(key.worldId());
            if (liveWorld != null) completeStatic(token, origin,
                    result.map(point -> location(liveWorld, point)).orElse(null), Instant.now(clock));
        });
        if (work == null) return;
        TrackingSearchQueue.Request request = searchQueue.replace(
                work.id(), TrackingSearchQueue.Lane.STATIC);
        if (target == TrackingTargetId.VILLAGE) {
            VillageAccumulator accumulator = new VillageAccumulator(work, origin);
            for (Structure village : VILLAGES) {
                searchQueue.add(
                        request,
                        () -> runStructureStep(work, world, () -> locateVillagePart(world, village, accumulator)));
            }
            return;
        }
        searchQueue.add(
                request,
                () -> runStructureStep(work, world, () -> completeSingleSearch(world, target, work, origin)));
    }

    private void runStructureStep(SharedSearchCache<StructureSearchKey, PortalPoint>.Work work,
                                  World world, Runnable step) {
        if (!work.active()) return;
        if (server.getWorld(world.getUID()) != world) {
            work.fail();
            return;
        }
        try { step.run(); }
        catch (RuntimeException exception) {
            work.fail();
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Structure tracking query failed", exception);
        }
    }

    private void locateVillagePart(
            World world,
            Structure village,
            VillageAccumulator accumulator
    ) {
        if (!accumulator.work().active()) {
            return;
        }
        var result = world.locateNearestStructure(
                accumulator.origin(), village, accumulator.work().key().radiusChunks(), false);
        if (result != null) {
            Location location = result.getLocation();
            if (accumulator.best() == null
                    || horizontalDistance(accumulator.origin(), location)
                    < horizontalDistance(accumulator.origin(), accumulator.best())) {
                accumulator.best(location);
            }
        }
        accumulator.completeOne();
        if (accumulator.remaining() == 0) {
            accumulator.work().complete(Optional.ofNullable(accumulator.best()).map(CompassTrackingService::point));
        }
    }

    private void completeSingleSearch(
            World world,
            TrackingTargetId target,
            SharedSearchCache<StructureSearchKey, PortalPoint>.Work work,
            Location origin
    ) {
        if (!work.active()) {
            return;
        }
        Location result = switch (target) {
            case NETHER_FORTRESS -> locationOf(world.locateNearestStructure(
                    origin, Structure.FORTRESS, work.key().radiusChunks(), false));
            case BASTION_REMNANT -> locationOf(world.locateNearestStructure(
                    origin, Structure.BASTION_REMNANT, work.key().radiusChunks(), false));
            default -> null;
        };
        work.complete(Optional.ofNullable(result).map(CompassTrackingService::point));
    }

    private void completeStatic(
            TrackingSearchToken token,
            Location origin,
            Location result,
            Instant searchedAt
    ) {
        if (state == null || !state.isCurrent(token)) {
            return;
        }
        staticSearches.put(token.playerId(), new StaticSearchState(
                token.target(), token.worldId(), origin, result, searchedAt, false, token));
    }

    private PortalSearchState ensurePortalSearch(Player player, Instant now) {
        PortalSearchState portal = portalSearches.get(player.getUniqueId());
        TrackingPosition current = position(player.getLocation());
        boolean invalidPortal = portal != null
                && portal.result() != null
                && portal.result().getWorld() != null
                && portal.result().getWorld().isChunkLoaded(
                portal.result().getBlockX() >> 4, portal.result().getBlockZ() >> 4)
                && portal.result().getBlock().getType() != Material.NETHER_PORTAL;
        boolean needsSearch = portal == null
                || !portal.worldId().equals(player.getWorld().getUID())
                || invalidPortal
                || (!portal.searching() && TrackingPolicy.shouldRetarget(
                position(portal.origin()), current, portal.searchedAt(), now, settings));
        if (!needsSearch) {
            return portal;
        }
        long version = ++portalVersion;
        Location origin = player.getLocation().clone();
        Location oldResult = !invalidPortal && portal != null
                && portal.worldId().equals(player.getWorld().getUID())
                ? portal.result() : null;
        if (invalidPortal) portalBlocksChanged(player.getWorld(),
                portal.result().getBlockX(), portal.result().getBlockZ());
        PortalSearchState requested = new PortalSearchState(
                player.getWorld().getUID(), origin, oldResult, now, true, version);
        portalSearches.put(player.getUniqueId(), requested);
        UUID playerId = player.getUniqueId();
        UUID worldId = player.getWorld().getUID();
        portalEngine.request(playerId, new PortalSearchEngine.Query(worldId, point(origin),
                settings.searchRadiusBlocks()), result -> {
            PortalSearchState stillCurrent = portalSearches.get(playerId);
            World liveWorld = server.getWorld(worldId);
            if (liveWorld != null && stillCurrent != null && stillCurrent.version() == version) {
                Location found = result.map(value -> location(liveWorld, value)).orElse(null);
                if (found != null && liveWorld.isChunkLoaded(found.getBlockX() >> 4, found.getBlockZ() >> 4)
                        && found.getBlock().getType() != Material.NETHER_PORTAL) {
                    portalBlocksChanged(liveWorld, found.getBlockX(), found.getBlockZ());
                    found = null;
                }
                portalSearches.put(playerId, new PortalSearchState(
                        worldId, origin, found, Instant.now(clock), false, version));
            }
        });
        return portalSearches.get(playerId);
    }

    private World chooseTargetWorld(Player player, TrackingWorldKind kind) {
        if (worldKind(player.getWorld()) == kind) {
            return player.getWorld();
        }
        World recent = state.recentWorld(player.getUniqueId(), kind)
                .map(server::getWorld).orElse(null);
        if (recent != null) {
            return recent;
        }
        return server.getWorlds().stream()
                .filter(world -> worldKind(world) == kind)
                .findFirst()
                .orElse(null);
    }

    private Location searchOrigin(Player player, World targetWorld) {
        Optional<TrackingPosition> visited = state.lastPosition(
                player.getUniqueId(), targetWorld.getUID());
        if (visited.isPresent()) {
            return location(visited.orElseThrow());
        }
        TrackingWorldKind from = worldKind(player.getWorld());
        TrackingWorldKind to = worldKind(targetWorld);
        Location current = player.getLocation();
        if (from == TrackingWorldKind.OVERWORLD && to == TrackingWorldKind.NETHER) {
            return new Location(
                    targetWorld,
                    current.getX() / 8.0,
                    targetWorld.getSpawnLocation().getY(),
                    current.getZ() / 8.0);
        }
        if (from == TrackingWorldKind.NETHER && to == TrackingWorldKind.OVERWORLD) {
            return new Location(
                    targetWorld,
                    current.getX() * 8.0,
                    targetWorld.getSpawnLocation().getY(),
                    current.getZ() * 8.0);
        }
        return targetWorld.getSpawnLocation();
    }

    private Location movementReference(Player player, World targetWorld) {
        if (player.getWorld().equals(targetWorld)) {
            return player.getLocation();
        }
        TrackingWorldKind from = worldKind(player.getWorld());
        TrackingWorldKind to = worldKind(targetWorld);
        Location current = player.getLocation();
        if (from == TrackingWorldKind.OVERWORLD && to == TrackingWorldKind.NETHER) {
            return new Location(
                    targetWorld,
                    current.getX() / 8.0,
                    targetWorld.getSpawnLocation().getY(),
                    current.getZ() / 8.0);
        }
        if (from == TrackingWorldKind.NETHER && to == TrackingWorldKind.OVERWORLD) {
            return new Location(
                    targetWorld,
                    current.getX() * 8.0,
                    targetWorld.getSpawnLocation().getY(),
                    current.getZ() * 8.0);
        }
        return state.lastPosition(player.getUniqueId(), targetWorld.getUID())
                .map(this::location)
                .orElse(targetWorld.getSpawnLocation());
    }

    private TrackingDisplay located(
            Player viewer,
            TrackingTargetId target,
            Location targetLocation,
            World displayWorld
    ) {
        long distance = TrackingPolicy.roundedDisplayDistance(
                position(viewer.getLocation()), position(targetLocation));
        return new TrackingDisplay("tracking.actionbar.same-dimension", Map.of(
                "target", languages.text(viewer, target.languageKey()),
                "distance", Long.toString(distance),
                "unit", languages.text(viewer, "unit.blocks"),
                "dimension", dimensionName(viewer, displayWorld)), targetLocation, null);
    }

    private TrackingDisplay status(String key) {
        return new TrackingDisplay(key, Map.of(), null, null);
    }

    private Map<String, String> targetPlaceholders(
            Player player,
            TrackingTargetId target,
            World world
    ) {
        return Map.of(
                "target", languages.text(player, target.languageKey()),
                "dimension", dimensionName(player, world));
    }

    private String dimensionName(Player player, World world) {
        return languages.text(player, "tracking.dimension." + (world == null
                ? "unknown"
                : worldKind(world).name().toLowerCase(java.util.Locale.ROOT)));
    }

    private boolean ensureCompass(
            GameSession session,
            Player player,
            TrackingCompassKind expectedKind
    ) {
        if (session.participant(player.getUniqueId()).isEmpty() || expectedKind == null) {
            removeMarkedCompasses(player);
            noSlotWarned.remove(player.getUniqueId());
            return false;
        }
        if (pendingPlayers.needsReset(player.getUniqueId())) {
            closeSelection(player);
            removeMarkedCompasses(player);
            clearActionBar(player);
            noSlotWarned.remove(player.getUniqueId());
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack keptCompass = null;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isMarkedCompass(item)) {
                continue;
            }
            if (keptCompass == null
                    && allowedInventorySlot(slot)
                    && isCurrentCompass(item, session.id(), expectedKind)) {
                keptCompass = item;
                keptCompass.setAmount(1);
            } else {
                inventory.setItem(slot, null);
            }
        }
        if (isMarkedCompass(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (keptCompass != null) {
            refreshCompassPresentation(keptCompass, player, expectedKind);
            noSlotWarned.remove(player.getUniqueId());
            return true;
        }
        int slot = availableSlot(inventory);
        if (slot < 0) {
            if (noSlotWarned.add(player.getUniqueId())) {
                languages.send(player, "tracking.compass.no-space");
            }
            return false;
        }
        inventory.setItem(slot, createCompass(player, session.id(), expectedKind));
        noSlotWarned.remove(player.getUniqueId());
        return true;
    }

    private ItemStack createCompass(
            Player player,
            UUID sessionId,
            TrackingCompassKind kind
    ) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
                sessionKey, PersistentDataType.STRING, sessionId.toString());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.id());
        compass.setItemMeta(meta);
        refreshCompassPresentation(compass, player, kind);
        return compass;
    }

    private void refreshCompassPresentation(
            ItemStack compass,
            Player player,
            TrackingCompassKind kind
    ) {
        if (!(compass.getItemMeta() instanceof CompassMeta meta)) {
            return;
        }
        meta.itemName(languages.component(player, "tracking.compass.name", Map.of()));
        meta.lore(List.of(
                languages.component(player, kind == TrackingCompassKind.RUNNER
                        ? "tracking.compass.lore-runner-use"
                        : "tracking.compass.lore-hunter-use", Map.of()),
                languages.component(player, "tracking.compass.lore-bound", Map.of())));
        meta.setEnchantmentGlintOverride(true);
        compass.setItemMeta(meta);
    }

    private static int availableSlot(PlayerInventory inventory) {
        ItemStack preferred = inventory.getItem(PREFERRED_HOTBAR_SLOT);
        if (preferred == null || preferred.getType().isAir()) {
            return PREFERRED_HOTBAR_SLOT;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        ItemStack offhand = inventory.getItem(OFFHAND_SLOT);
        return offhand == null || offhand.getType().isAir() ? OFFHAND_SLOT : -1;
    }

    private static boolean allowedInventorySlot(int slot) {
        return (slot >= 0 && slot < 36) || slot == OFFHAND_SLOT;
    }

    private void removeMarkedCompasses(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isMarkedCompass(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (isMarkedCompass(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void showActionBarIfHeld(
            GameSession session,
            Player player,
            TrackingCompassKind kind,
            TrackingDisplay display
    ) {
        if (!isCurrentCompass(heldCompass(player), session.id(), kind)) {
            clearActionBar(player);
            return;
        }
        player.sendActionBar(languages.component(
                player, display.messageKey(), display.placeholders()));
        actionBarVisible.add(player.getUniqueId());
    }

    private ItemStack heldCompass(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isMarkedCompass(main)) {
            return main;
        }
        return player.getInventory().getItemInOffHand();
    }

    private void clearActionBar(Player player) {
        if (actionBarVisible.remove(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
        }
    }

    private void applyDirection(Player player, Location direction, World invalidDirectionWorld) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isMarkedCompass(item)) {
                continue;
            }
            CompassMeta meta = (CompassMeta) item.getItemMeta();
            Location lodestone = direction;
            if (lodestone == null
                    && invalidDirectionWorld != null
                    && !invalidDirectionWorld.equals(player.getWorld())) {
                lodestone = invalidDirectionWorld.getSpawnLocation();
            }
            if (lodestone == null && invalidDirectionWorld != null) {
                lodestone = server.getWorlds().stream()
                        .filter(world -> !world.equals(player.getWorld()))
                        .map(World::getSpawnLocation)
                        .findFirst()
                        .orElse(null);
            }
            if (lodestone == null) {
                meta.clearLodestone();
            } else {
                meta.setLodestone(lodestone);
                meta.setLodestoneTracked(false);
            }
            item.setItemMeta(meta);
        }
    }

    private boolean isCurrentCompass(
            ItemStack item,
            UUID activeSessionId,
            TrackingCompassKind expectedKind
    ) {
        if (!isMarkedCompass(item)) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        String storedSession = data.get(sessionKey, PersistentDataType.STRING);
        String storedKind = data.get(kindKey, PersistentDataType.STRING);
        return TrackingCompassIdentity.classify(
                true, storedSession, storedKind, activeSessionId, expectedKind)
                == TrackingCompassIdentity.Kind.CURRENT;
    }

    private Optional<TrackingCompassKind> expectedKind(GameSession session, UUID playerId) {
        if (session.isEliminatedParticipant(playerId)) return Optional.empty();
        Role role = session.roleOf(playerId).orElse(null);
        if (role == Role.RUNNER
                && configuration.get(BooleanConfigKey.RUNNER_COMPASS_ENABLED)
                && configuration.values().runnerStructureUnlockTimes().anyEnabled()) {
            return Optional.of(TrackingCompassKind.RUNNER);
        }
        if (role == Role.HUNTER
                && configuration.get(BooleanConfigKey.HUNTER_COMPASS_ENABLED)) {
            return Optional.of(TrackingCompassKind.HUNTER);
        }
        return Optional.empty();
    }

    private TrackingAvailability availability(GameSession session, Instant now) {
        return StructureUnlockPolicy.availability(configuration.values().runnerStructureUnlockTimes(),
                session.startedAt().orElse(now), now);
    }

    private void closeSelection(Player player) {
        selectionAuthorizations.invalidate(player.getUniqueId()).ifPresent(authorization ->
                selectionPresenter.close(
                        player,
                        authorization.sessionId(),
                        authorization.authorizationId()));
    }

    private void invalidateStructureSearch(UUID playerId) {
        // Portal lookup depends on the viewer's world/position, not the selected structure.
        if (state != null) {
            state.invalidate(playerId);
        }
        staticSearches.remove(playerId);
        desiredTargets.remove(playerId);
        sharedStructures.cancel(playerId);
    }

    private void invalidateParticipant(UUID playerId) {
        if (state != null) {
            state.invalidate(playerId);
        }
        staticSearches.remove(playerId);
        portalSearches.remove(playerId);
        desiredTargets.remove(playerId);
        sharedStructures.cancel(playerId);
        portalEngine.cancel(playerId);
    }

    /** Coalesces physics/create/break events and waits for vanilla POI updates before invalidation. */
    public void portalBlocksChanged(World world, int blockX, int blockZ) {
        if (state == null) return;
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            pendingPortalChanges.mark(world.getUID(), chunkX + x, chunkZ + z, searchCycle);
        }
    }

    public void worldUnloaded(UUID worldId) {
        Set<UUID> affected = new HashSet<>();
        staticSearches.forEach((id, search) -> { if (search.worldId().equals(worldId)) affected.add(id); });
        portalSearches.forEach((id, search) -> { if (search.worldId().equals(worldId)) affected.add(id); });
        affected.forEach(this::invalidateParticipant);
        sharedStructures.invalidateMatching(key -> key.worldId().equals(worldId));
        portalEngine.invalidateWorld(worldId);
        pendingPortalChanges.removeWorld(worldId);
    }

    private void flushPortalChanges() {
        boolean changed = false;
        for (var entry : pendingPortalChanges.drain(searchCycle).entrySet()) {
            UUID worldId = entry.getKey();
            for (UUID playerId : List.copyOf(portalSearches.keySet())) {
                if (portalSearches.get(playerId).worldId().equals(worldId)) {
                    portalSearches.remove(playerId);
                    portalEngine.cancel(playerId);
                }
            }
            portalEngine.invalidateChunks(worldId, entry.getValue());
            changed = true;
        }
        if (changed) runningSession().ifPresent(this::tick);
    }

    private void runSearchTick() {
        searchCycle++;
        flushPortalChanges();
        searchQueue.runOne();
        portalEngine.tick();
        searchTicks++;
        if (searchTicks >= 20) {
            searchTicks = 0;
            runningSession().ifPresent(this::tick);
        }
    }

    private boolean isTracking(GameSession session) {
        return state != null
                && state.sessionId().equals(session.id())
                && session.state() == SessionState.RUNNING;
    }

    private Optional<GameSession> runningSession() {
        return gameManager.activeSession()
                .filter(session -> session.state() == SessionState.RUNNING);
    }

    private void stopSearchTicker() {
        if (searchTicker != null) {
            searchTicker.cancel();
            searchTicker = null;
        }
    }

    private void clearRuntimeState() {
        sharedStructures.clear();
        portalEngine.clear();
        pendingPortalChanges.clear();
        searchCycle = 0;
        searchQueue.clear();
        staticSearches.clear();
        portalSearches.clear();
        desiredTargets.clear();
        selectionAuthorizations.clear();
        actionBarVisible.clear();
        noSlotWarned.clear();
        portalVersion = 0;
        searchTicks = 0;
    }

    private void recordPosition(UUID playerId, Location location) {
        if (location != null && location.getWorld() != null) {
            state.recordPosition(playerId, position(location));
        }
    }

    private TrackingPosition position(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new TrackingPosition(
                world.getUID(), worldKind(world),
                location.getX(), location.getY(), location.getZ());
    }

    private Location location(TrackingPosition position) {
        World world = server.getWorld(position.worldId());
        return world == null
                ? null
                : new Location(world, position.x(), position.y(), position.z());
    }

    private Player onlinePlayer(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && player.isOnline() ? player : null;
    }

    private int searchRadiusChunks() {
        return Math.ceilDiv(settings.searchRadiusBlocks(), 16);
    }

    private static TrackingWorldKind worldKind(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> TrackingWorldKind.OVERWORLD;
            case NETHER -> TrackingWorldKind.NETHER;
            case THE_END -> TrackingWorldKind.END;
            default -> TrackingWorldKind.OTHER;
        };
    }

    private static Location locationOf(org.bukkit.util.StructureSearchResult result) {
        return result == null ? null : result.getLocation();
    }

    private static PortalPoint point(Location location) {
        return new PortalPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static Location location(World world, PortalPoint point) {
        return new Location(world, point.x(), point.y(), point.z());
    }

    private static double horizontalDistance(Location first, Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return Math.hypot(x, z);
    }

    private record TrackingDisplay(
            String messageKey,
            Map<String, String> placeholders,
            Location direction,
            World invalidDirectionWorld
    ) {
    }

    private record StaticSearchState(
            TrackingTargetId target,
            UUID worldId,
            Location origin,
            Location result,
            Instant searchedAt,
            boolean searching,
            TrackingSearchToken token
    ) {
    }

    private record PortalSearchState(
            UUID worldId,
            Location origin,
            Location result,
            Instant searchedAt,
            boolean searching,
            long version
    ) {
    }

    private record StructureSearchKey(UUID worldId, TrackingTargetId target,
                                      double x, double y, double z, int radiusChunks) {}

    private static final class VillageAccumulator {
        private final SharedSearchCache<StructureSearchKey, PortalPoint>.Work work;
        private final Location origin;
        private int remaining = VILLAGES.size();
        private Location best;

        private VillageAccumulator(SharedSearchCache<StructureSearchKey, PortalPoint>.Work work, Location origin) {
            this.work = work;
            this.origin = origin;
        }

        SharedSearchCache<StructureSearchKey, PortalPoint>.Work work() {
            return work;
        }

        Location origin() {
            return origin;
        }

        int remaining() {
            return remaining;
        }

        Location best() {
            return best;
        }

        void best(Location value) {
            best = value;
        }

        void completeOne() {
            remaining--;
        }
    }
}
