package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.client.TrackingClientPublisher;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.tracking.*;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Adapts public string-ID tracking to the existing native compass/search implementation. */
final class ExtendedTrackingController implements TrackingRegistryService.Runtime {
    private final JavaPlugin plugin;
    private final CompassTrackingService tracking;
    private final ConfigurationService configuration;
    private final LanguageService language;
    private final Supplier<Optional<MatchSnapshot>> match;
    private final Clock clock;
    final TrackingRegistryService registry;
    final TrackingControlService control;
    private final PlayerTrackingIndex players=new PlayerTrackingIndex(4096);
    private final Map<UUID,PlayerTrackingCandidate> playerTargets=new HashMap<>();
    private final TrackingLocateCoordinator coordinator;
    private final DynamicTrackingSelectionPresenter menus;
    private final Map<UUID, String> selections = new HashMap<>();
    private final Map<UUID,String> pendingRestores = new HashMap<>();
    Map<UUID,String> selectionSnapshot() { Map<UUID,String> saved=new HashMap<>(pendingRestores);saved.putAll(selections);return Map.copyOf(saved); }
    void restoreSelections(Map<UUID,String> saved) { pendingRestores.clear();pendingRestores.putAll(saved); }
    void restorePlayer(UUID player) {
        if(overridden(player))return;
        String target=pendingRestores.remove(player);
        if(target!=null&&selection(player).isEmpty())registry.choose(player,target,registry.revision());
    }
    private final Map<UUID, Boolean> visible = new HashMap<>();
    private final Map<UUID, TrackingLocateQuery> queries = new HashMap<>();
    private final Map<UUID, TrackingSnapshot> announced = new HashMap<>();
    private UUID session;
    private long version;
    private boolean scheduled;
    ExtendedTrackingController(JavaPlugin plugin, CompassTrackingService tracking, ConfigurationService configuration,
                               LanguageService language, TrackingClientPublisher clients, OwnedRegistry owned,
                               Supplier<Optional<MatchSnapshot>> match, Clock clock) {
        this.plugin = plugin; this.tracking = tracking; this.configuration = configuration; this.language = language; this.match = match; this.clock = clock;
        registry = new TrackingRegistryService(owned, this);
        control = new TrackingControlService(owned,match,tracking::extensionMayControl,
                id->registry.definition(id).isPresent(),registry::revision,this::controlChanged);
        registry.installControl(control);
        coordinator = new TrackingLocateCoordinator(owned, System::nanoTime, this::current, this::completed);
        menus = new DynamicTrackingSelectionPresenter(clients, language, player -> tracking.extensionMaySelect(player, true),
                (player, sessionId, authorization, revision, target) -> match.get().filter(value -> value.id().equals(sessionId)).isPresent()
                        && registry.choose(player.getUniqueId(), target, revision));
        for (var target : CoreTrackingTargets.ids()) {
            registry.registerCore(CoreTrackingTargets.definition(target,menuText(language.localized(target.languageKey())),
                    viewer -> coreAvailability(target,viewer)));
        }
        registry.onChange(this::changed);
        registry.labels((viewer,target,field,fallback)->{
            Player receiver=plugin.getServer().getPlayer(viewer.playerId());if(receiver==null)return fallback.resolve(viewer.locale());
            TrackingTargetId builtin=nativeTarget(target);String key=field.equals("name")&&builtin!=TrackingTargetId.NONE?builtin.languageKey():target+"."+field;
            return language.localizedValue(receiver,key,fallback);
        });
    }
    static String id(TrackingTargetId target) { return CoreTrackingTargets.id(target); }
    static TrackingTargetId nativeTarget(String target) {
        if (target == null || !target.startsWith("core:")) return TrackingTargetId.NONE;
        try { return TrackingTargetId.valueOf(target.substring(5).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { return TrackingTargetId.NONE; }
    }
    @Override public Optional<TrackingViewerContext> viewer(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId); Optional<MatchSnapshot> current = match.get();
        if (player == null || !player.isOnline() || current.isEmpty() || !current.get().participants().containsKey(playerId)) return Optional.empty();
        return Optional.of(new TrackingViewerContext(playerId, player.locale().toLanguageTag(), point(player.getLocation()),
                player.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT), current, Instant.now(clock)));
    }
    @Override public boolean maySelect(UUID player) {
        Player live = plugin.getServer().getPlayer(player); return !overridden(player) && live != null && tracking.extensionMaySelect(live, false);
    }
    @Override public boolean overridden(UUID player) { return control.binding(player).isPresent(); }
    Optional<String> effectiveTarget(UUID player) {
        var binding=control.binding(player).orElse(null);
        return binding==null?selection(player):binding.request().targetId();
    }
    boolean playerOverride(UUID player) { return control.binding(player).map(value->value.request().playerId().isPresent()||value.request().selector().isPresent()).orElse(false); }
    boolean disabled(UUID player) { return control.binding(player).map(value->value.request().mode()==TrackingOverrideMode.DISABLED).orElse(false); }
    Optional<PlayerTrackingCandidate> playerTarget(UUID player) {
        return Optional.ofNullable(playerTargets.get(player)).filter(value->match.get().map(current->{
            var participant=current.participants().get(value.playerId());return participant==null||!participant.eliminated();}).orElse(false));
    }
    void observe(Player player) { if(session!=null)players.observe(player.getUniqueId(),player.getName(),point(player.getLocation())); }
    void samplePlayers() {
        var current=match.get().filter(value->value.state()==MatchState.RUNNING).orElse(null);if(current==null)return;
        // Seed restored participants without touching offline player files. Live observations win.
        tracking.positionSnapshot().forEach((id,positions)->{var participant=current.participants().get(id);
            if(participant!=null&&!positions.isEmpty()){var value=positions.getLast();players.observe(id,participant.name(),new WorldPosition(value.worldId(),value.x(),value.y(),value.z()));}});
        Set<UUID> online=new HashSet<>();for(Player live:plugin.getServer().getOnlinePlayers()){observe(live);online.add(live.getUniqueId());}
        var candidates=players.candidates(current,online).stream().filter(value->plugin.getServer().getWorld(value.position().worldId())!=null).toList();
        for(UUID player:current.participants().keySet()) {
            if(!playerOverride(player)||!tracking.extensionMayControl(player)){playerTargets.remove(player);continue;}
            var viewer=viewer(player).orElse(null);if(viewer==null)continue;
            var resolved=control.resolvePlayer(player,new PlayerTrackingContext(viewer,candidates));
            if(resolved.isPresent())playerTargets.put(player,resolved.get());else playerTargets.remove(player);
        }
    }
    private void controlChanged(UUID player) {
        invalidatePlayer(player); playerTargets.remove(player);
        tracking.extensionSelection(player,nativeTarget(effectiveTarget(player).orElse(null)));
        // Never run inventory/provider work reentrantly inside the public mutation callback.
        if(scheduled||!plugin.isEnabled())return;scheduled=true;
        plugin.getServer().getScheduler().runTask(plugin,()->{scheduled=false;tracking.extensionRefreshNow();});
    }
    @Override public Optional<String> selection(UUID player) { return Optional.ofNullable(selections.get(player)); }
    @Override public void selection(UUID player, String target) {
        pendingRestores.remove(player);
        coordinator.cancel(player); queries.remove(player); announced.remove(player);
        if (target == null) selections.remove(player); else selections.put(player, target);
        if(!overridden(player))tracking.extensionSelection(player, nativeTarget(target));
    }
    @Override public Optional<TrackingSnapshot> snapshot(UUID player) { return tracking.extensionSnapshot(player, effectiveTarget(player)); }
    @Override public void refresh(UUID player) { TrackingLocateQuery query = queries.get(player); if (query != null) query.refresh = true; tracking.extensionRefreshRequested(player); }
    @Override public void invalidate(TrackingInvalidation scope) {
        tracking.extensionInvalidateNative(scope);
        coordinator.invalidate(request -> scope.playerId().map(request.playerId()::equals).orElse(true)
                && scope.targetId().map(request.targetId()::equals).orElse(true));
        for (UUID player : List.copyOf(queries.keySet())) if (scope.playerId().map(player::equals).orElse(true)
                && scope.targetId().map(id -> effectiveTarget(player).filter(id::equals).isPresent()).orElse(true)) {
            TrackingLocateQuery query = queries.get(player); query.pending = false; query.refresh = true;
            // Revision changes invalidate a result immediately; ordinary refresh still obeys cooldown.
            if (query.request.targetRevision() != registry.revision()) queries.remove(player);
        }
    }
    @Override public void catalogChanged() {
        control.catalogChanged();
        for (UUID player : List.copyOf(selections.keySet())) if (registry.definition(selections.get(player)).isEmpty()) selection(player, null);
        if (scheduled || !plugin.isEnabled()) return; scheduled = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> { scheduled = false; advance(TrackingChange.Source.REGISTRATION); tracking.extensionRefreshNow(); });
    }
    void netherCoordinateScaleChanged() {
        for (var entry : List.copyOf(queries.entrySet())) {
            var query = entry.getValue();
            World from = plugin.getServer().getWorld(query.request.viewer().worldId());
            World to = plugin.getServer().getWorld(query.request.origin().worldId());
            if (!com.caxture.manhuntuniversal.portal.NetherCoordinateScale.applies(from, to)) continue;
            coordinator.cancel(entry.getKey());
            queries.put(entry.getKey(), query.invalidated(++version));
            announced.remove(entry.getKey());
            tracking.extensionProjectionInvalidated(entry.getKey());
        }
    }
    void begin(GameSession game) {
        if (!game.id().equals(session)) { registry.clearMatch(); announced.clear(); }
        session = game.id(); selections.clear(); queries.clear(); visible.clear(); coordinator.clear(); advance(TrackingChange.Source.PLAYER_STATE);
    }
    void prepare(Collection<Player> players) { for (Player player : players) cacheMenu(player, TrackingChange.Source.PLAYER_STATE); }
    void advance(TrackingChange.Source source) {
        for (Player player : plugin.getServer().getOnlinePlayers()) if (viewer(player.getUniqueId()).isPresent()) {restorePlayer(player.getUniqueId());cacheMenu(player, source);}
    }
    private void cacheMenu(Player player, TrackingChange.Source source) {
        if(overridden(player.getUniqueId())){menus.close(player);return;}
        registry.menu(player.getUniqueId(), source).ifPresent(menu -> {
            visible.put(player.getUniqueId(), !menu.options().isEmpty()); menus.update(player, menu);
        });
    }
    boolean visible(UUID player) { return visible.getOrDefault(player, false); }
    void open(Player player) { viewer(player.getUniqueId()).flatMap(ignored -> registry.menu(player.getUniqueId())).ifPresent(menu -> menus.open(player, session, menu)); }
    void choose(Player player, com.caxture.manhuntuniversal.protocol.DynamicTrackingChoose choice) {
        menus.choose(player, choice.sessionId(), choice.authorizationId(), choice.directoryRevision(), choice.targetId());
    }
    void cancelMenu(Player player, UUID session, long authorization) { menus.cancel(player, session, authorization); }
    void closeMenu(Player player) { menus.close(player); }
    void invalidatePlayer(UUID player) { coordinator.cancel(player); queries.remove(player); announced.remove(player);
        Player live = plugin.getServer().getPlayer(player); if (live != null) menus.close(live); else menus.forget(player); }
    void leave(UUID player) { invalidatePlayer(player); registry.forget(player); visible.remove(player); }
    void end() { control.clear();players.clear();playerTargets.clear();coordinator.clear(); menus.clear(plugin.getServer()); queries.clear(); selections.clear(); pendingRestores.clear();visible.clear(); announced.clear(); }
    void unload(UUID world) { coordinator.invalidate(request -> request.origin().worldId().equals(world) || request.viewer().worldId().equals(world));
        queries.entrySet().removeIf(entry -> entry.getValue().request.origin().worldId().equals(world) || entry.getValue().request.viewer().worldId().equals(world)); }
    void tick() { coordinator.tick(); }
    boolean handles(UUID player) {
        String target = effectiveTarget(player).orElse(null); TrackingLocateQuery query = queries.get(player);
        return target != null && registry.customLocator(target) && (query == null || query.result == null || query.result.status() != LocateResult.Status.USE_CORE);
    }
    Optional<LocateResult> result(UUID player) { TrackingLocateQuery query = queries.get(player); return query == null ? Optional.empty() : Optional.ofNullable(query.result); }
    void maintain(Player player) {
        String target = effectiveTarget(player.getUniqueId()).orElse(null); if (target == null || !registry.customLocator(target)) return;
        var definition = registry.definition(target).orElse(null); if (definition == null) return;
        Location origin = tracking.extensionOrigin(player, definition.world()); TrackingLocateQuery old = queries.get(player.getUniqueId());
        if (origin == null) {
            var context=viewer(player.getUniqueId()).orElse(null);if(context==null||context.match().isEmpty())return;
            var request=new LocateRequest(context.match().get().id(),registry.revision(),++version,player.getUniqueId(),target,context.position(),context.position(),context.match(),tracking.extensionSettings().searchRadiusBlocks());
            TrackingLocateQuery unavailable=new TrackingLocateQuery(request,Instant.now(clock),context.position());unavailable.pending=false;unavailable.result=LocateResult.notFound("Target world is unavailable");queries.put(player.getUniqueId(),unavailable);return;
        }
        Location movement=tracking.extensionMovementReference(player,origin.getWorld());
        Instant now = Instant.now(clock); TrackingSettings settings = tracking.extensionSettings();
        boolean moved = old != null && TrackingMovementReference.moved(old.movement,point(movement),settings.retargetDistanceBlocks());
        boolean ready = old == null || old.ready(now, settings.retargetCooldownSeconds(), moved,
                old.request.viewer().worldId().equals(player.getWorld().getUID()));
        if (!ready) return;
        if(old!=null&&(moved||old.refresh))origin=movement;
        var context = viewer(player.getUniqueId()).orElse(null); if (context == null || context.match().isEmpty()) return;
        var request = new LocateRequest(context.match().get().id(), registry.revision(), ++version, player.getUniqueId(), target,
                context.position(), point(origin), context.match(), settings.searchRadiusBlocks());
        TrackingLocateQuery query = new TrackingLocateQuery(request, now,point(movement)); queries.put(player.getUniqueId(), query);
        var binding = registry.locator(target).orElse(null);
        if (binding == null || !coordinator.request(request, binding)) { queries.remove(player.getUniqueId()); return; }
    }
    private boolean current(LocateRequest request) {
        TrackingLocateQuery query = queries.get(request.playerId());
        return query != null && query.current(request) && request.targetRevision() == registry.revision()
                && effectiveTarget(request.playerId()).filter(request.targetId()::equals).isPresent()
                && match.get().filter(value -> value.id().equals(request.sessionId()) && value.state() == MatchState.RUNNING).isPresent()
                && (overridden(request.playerId())?tracking.extensionMayControl(request.playerId()):maySelect(request.playerId())) && plugin.getServer().getWorld(request.origin().worldId()) != null
                && viewer(request.playerId()).filter(value->value.position().worldId().equals(request.viewer().worldId())).isPresent()
                && (overridden(request.playerId())||registry.availability(request.targetId(), request.playerId(), TrackingChange.Source.PLAYER_STATE).selectable());
    }
    private void completed(TrackingLocateCoordinator.Completion completion) {
        TrackingLocateQuery query = queries.get(completion.request().playerId()); if (query == null) return;
        query.pending = false; query.result = completion.result();
        if (query.result.status() == LocateResult.Status.USE_CORE && nativeTarget(query.request.targetId()) == TrackingTargetId.NONE)
            query.result = LocateResult.failed("No native locator exists for this target");
        tracking.extensionDisplayNow(); locationChanged(completion.request().playerId());
    }
    void locationChanged(UUID player) {
        snapshot(player).ifPresent(value -> {
            TrackingSnapshot old = announced.put(player, value);
            if (old == null || !old.targetId().equals(value.targetId()) || !old.destination().equals(value.destination())
                    || !old.guidance().equals(value.guidance()) || old.status() != value.status())
                fire(new TrackingChange(TrackingChange.Kind.LOCATION, player, value.targetId().orElse(""), old == null ? Optional.empty() : old.targetId(),
                        Optional.empty(), Optional.empty(), TrackingChange.Source.NATURAL));
        });
    }
    String name(UUID player, String target) {
        TrackingTargetId builtin = nativeTarget(target);
        Player recipient = plugin.getServer().getPlayer(player);
        if (builtin != TrackingTargetId.NONE && recipient != null) return language.text(recipient, builtin.languageKey());
        return registry.definition(target).map(definition -> recipient==null?definition.name().resolve("en_us")
                :language.localizedValue(recipient,target+".name",definition.name())).orElse(target);
    }
    private void changed(TrackingChange change) {
        Player player = plugin.getServer().getPlayer(change.playerId());
        if (player != null && maySelect(change.playerId())) {
            if (change.kind() == TrackingChange.Kind.SELECTION) language.send(player,
                    change.targetId().isEmpty() ? "tracking.selection.cleared" : "tracking.selection.changed", Map.of("target", name(change.playerId(), change.targetId())));
            if (change.kind() == TrackingChange.Kind.AVAILABILITY && change.before().filter(value -> value.remainingMillis() > 0).isPresent()
                    && change.after().filter(TargetAvailability::selectable).isPresent())
                language.send(player, "tracking.selection.unlocked", Map.of("target", name(change.playerId(), change.targetId())));
        }
        fire(change);
    }
    private void fire(TrackingChange change) {
        org.bukkit.event.Event event = switch (change.kind()) {
            case SELECTION -> new com.caxture.manhuntuniversal.api.paper.event.TrackingSelectionChangedEvent(change);
            case AVAILABILITY -> new com.caxture.manhuntuniversal.api.paper.event.TrackingAvailabilityChangedEvent(change);
            case LOCATION -> new com.caxture.manhuntuniversal.api.paper.event.TrackingTargetChangedEvent(change, snapshot(change.playerId()));
        };
        plugin.getServer().getPluginManager().callEvent(event);
    }
    private TargetAvailability coreAvailability(TrackingTargetId target, TrackingViewerContext viewer) {
        return CoreTrackingTargets.availability(target,configuration.get(BooleanConfigKey.RUNNER_COMPASS_ENABLED),configuration::get,viewer);
    }
    static WorldPosition point(Location location) { return new WorldPosition(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()); }
    private static LocalizedText menuText(LocalizedText text){
        java.util.function.Function<String,String> bounded=value->{String clean=new String(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);
            return clean.codePointCount(0,clean.length())>64?clean.substring(0,clean.offsetByCodePoints(0,64)):clean;};
        Map<String,String> translations=new HashMap<>();text.translations().forEach((key,value)->translations.put(key,bounded.apply(value)));
        return new LocalizedText(bounded.apply(text.fallback()),translations);
    }
    private static double distance(WorldPosition first, WorldPosition second) {
        return Math.sqrt(Math.pow(first.x() - second.x(), 2) + Math.pow(first.y() - second.y(), 2) + Math.pow(first.z() - second.z(), 2));
    }

}
