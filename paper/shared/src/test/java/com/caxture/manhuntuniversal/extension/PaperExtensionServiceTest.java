package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.api.feature.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.api.text.TextContext;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import com.caxture.manhuntuniversal.extension.config.ConfigurationRegistryService;
import com.caxture.manhuntuniversal.extension.tracking.TrackingRegistryService;
import com.caxture.manhuntuniversal.client.DynamicTrackingMessages;
import com.caxture.manhuntuniversal.api.paper.event.*;
import com.caxture.manhuntuniversal.game.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PaperExtensionServiceTest {
    @Test void coreDebugChangesOnlyTheRequestedOfflinePlayerAndRetainsTheExtensionOwner() {
        Harness h=new Harness();var owner=h.register("mode",Set.of(ControlDomain.ROLES));h.offline.add(C);
        var result=h.service.debugSetRole(h.session.id(),h.session.revision(),C,TeamRole.RUNNER);
        assertTrue(result.successful());assertEquals(Set.of(A,B,C),new HashSet<>(h.session.runnerIds()));assertTrue(owner.active());
        assertEquals(2,h.session.settings().runnerCount());assertEquals(TeamPreference.RUNNER,h.manager.teamSelections().preferenceOf(A));
        var changing=(RolesChangingEvent)h.events.stream().filter(RolesChangingEvent.class::isInstance).findFirst().orElseThrow();
        assertEquals("core:debug",changing.getSource().extensionId());
        assertEquals(OperationStatus.OK,h.service.roles().assign(h.context(owner),h.original()).status());
    }

    @Test void coreDebugCannotRemoveTheLastLivingTeamOrChangeAnEliminatedOrUnreadyPlayer() {
        Harness h=new Harness();assertTrue(h.service.debugSetRole(h.session.id(),h.session.revision(),A,TeamRole.HUNTER).successful());
        var before=h.snapshot();assertEquals(OperationStatus.INVALID_ASSIGNMENT,h.service.debugSetRole(h.session.id(),h.session.revision(),B,TeamRole.HUNTER).status());assertEquals(before,h.snapshot());
        h.unavailable.add(C);assertEquals(OperationStatus.PLAYER_UNAVAILABLE,h.service.debugSetRole(h.session.id(),h.session.revision(),C,TeamRole.RUNNER).status());
        h.manager.eliminateParticipant(h.session.id(),C,Optional.empty(),NOW);
        assertEquals(OperationStatus.INVALID_STATE,h.service.debugSetRole(h.session.id(),h.session.revision(),C,TeamRole.RUNNER).status());
        assertTrue(h.session.isEliminatedParticipant(C));
    }

    @Test void settingTheExistingRoleOnlyReplaysPresentation() {
        Harness h=new Harness();var before=h.snapshot();
        assertTrue(h.service.debugSetRole(h.session.id(),h.session.revision(),A,TeamRole.RUNNER).successful());
        assertEquals(before,h.snapshot());assertEquals(1,h.roleReveals);assertEquals(0,h.rolesSynced);assertTrue(h.events.isEmpty());
    }

    @Test void debugReshuffleChangesTheTeamEvenWhenTheRandomDrawMatchesTheOldTeam() {
        Harness h=new Harness(new Random(0){@Override public int nextInt(int bound){return bound-1;}});
        assertTrue(h.service.debugReshuffleRoles(h.session.id(),h.session.revision()).successful());
        assertEquals(List.of(B,C),h.session.runnerIds());assertEquals(1,h.rolesSynced);assertEquals(2,h.session.livingRunnerIds().size());
    }

    @Test void debugReshuffleRefusesWhenNoEligibleAlternativeExists() {
        Harness h=new Harness();h.unavailable.addAll(Set.of(C,D));var before=h.snapshot();
        assertEquals(OperationStatus.INVALID_ASSIGNMENT,h.service.debugReshuffleRoles(h.session.id(),h.session.revision()).status());
        assertEquals(before,h.snapshot());assertEquals(0,h.rolesSynced);assertTrue(h.events.isEmpty());
    }

    @Test void debugRoleChangesHonorCancellationReentryAndStaleState() {
        Harness h=new Harness();var before=h.snapshot();
        h.listener=event->{if(event instanceof RolesChangingEvent changing){
            assertEquals(OperationStatus.BUSY,h.service.debugSetRole(h.session.id(),h.session.revision(),D,TeamRole.RUNNER).status());changing.setCancelled(true);
        }};
        assertEquals(OperationStatus.CANCELLED,h.service.debugSetRole(h.session.id(),h.session.revision(),C,TeamRole.RUNNER).status());assertEquals(before,h.snapshot());
        assertEquals(OperationStatus.STALE_SESSION,h.service.debugReshuffleRoles(UUID.randomUUID(),h.session.revision()).status());
        assertEquals(OperationStatus.STALE_REVISION,h.service.debugReshuffleRoles(h.session.id(),h.session.revision()-1).status());
        h.mainThread=false;assertEquals(OperationStatus.WRONG_THREAD,h.service.debugSetRole(h.session.id(),-1,C,TeamRole.RUNNER).status());
    }
    @Test void publicRegistriesShareARealOwnerAndDisableRestoresBothMenuRoutes() throws Exception {
        Harness h = new Harness();
        // Replace only the live-world adapters: ownership, entrypoints and disable dispatch are real.
        var childrenField = PaperExtensionService.class.getDeclaredField("children"); childrenField.setAccessible(true);
        OwnedRegistry children = (OwnedRegistry) childrenField.get(h.service);
        var configs = new ConfigurationRegistryService(children, ignored -> {});
        var configField = PaperExtensionService.class.getDeclaredField("configurationRegistry"); configField.setAccessible(true); configField.set(h.service, configs);
        String[] selected = {null}; UUID world = UUID.randomUUID();
        var targets = new TrackingRegistryService(children, new TrackingRegistryService.Runtime() {
            public Optional<TrackingViewerContext> viewer(UUID id) { return Optional.of(new TrackingViewerContext(id,"en_us",new WorldPosition(world,0,70,0),"overworld",Optional.of(h.snapshot()),NOW)); }
            public boolean maySelect(UUID id) { return true; }
            public Optional<String> selection(UUID id) { return Optional.ofNullable(selected[0]); }
            public void selection(UUID id,String target) { selected[0]=target; }
            public Optional<TrackingSnapshot> snapshot(UUID id) { return Optional.empty(); }
            public void refresh(UUID id) { }
            public void invalidate(TrackingInvalidation scope) { }
            public void catalogChanged() { }
        });
        var trackingField = PaperExtensionService.class.getDeclaredField("trackingRegistry"); trackingField.setAccessible(true); trackingField.set(h.service,targets);
        com.caxture.manhuntuniversal.api.paper.PaperManhuntUniversalApi api = h.service;
        var owner = h.register("example:integrated",Set.of());
        assertEquals("1.1.0",api.apiVersion());
        assertTrue(api.capabilities().containsAll(Set.of(Capability.CONFIGURATION_REGISTRY,Capability.DYNAMIC_TRACKING)));
        int[] stored = {1};
        assertTrue(api.configuration().register(owner,new ConfigDefinition<>("example:count",LocalizedText.of("Count"),LocalizedText.of(""),1,
                ConfigTypes.integers(0,10),new ConfigStorage<Integer>() {public Integer read(){return stored[0];}public boolean save(Integer value){stored[0]=value;return true;}},ConfigTiming.IMMEDIATE,"")).successful());
        assertTrue(api.configuration().update(owner,"example:count",3,-1).successful());
        assertTrue(api.texts().override(owner,"prefix",10,context->"example-prefix").successful());
        var text = new TextContext("prefix","en_us",Optional.of(A),Map.of());
        assertEquals("example-prefix",api.texts().resolve(text).orElseThrow());
        assertTrue(api.tracking().register(owner,new TrackingTargetDefinition("example:checkpoint",LocalizedText.of("Checkpoint"),TrackingWorld.current(),
                (request,cancellation)->java.util.concurrent.CompletableFuture.completedFuture(LocateResult.notFound("test")))).successful());
        var menu = api.tracking().menu(A).orElseThrow();
        assertEquals("example:checkpoint",DynamicTrackingMessages.open(h.session.id(),1,menu).targets().getFirst().id());
        assertTrue(api.tracking().select(new TrackingMutation(h.context(owner),A,menu.revision()),"example:checkpoint").successful());
        var selector = new PresentationSelector(Set.of(PresentationChannel.PAPER_MENU,PresentationChannel.FABRIC_MENU),"",Optional.empty(),false);
        assertTrue(api.presentation().intercept(owner,selector,10,request->PresentationDecision.cancel()).successful());
        var receiver = proxy(org.bukkit.command.CommandSender.class,(object,method,args)->{throw new UnsupportedOperationException(method.getName());});
        var payload = PresentationPayload.text(net.kyori.adventure.text.Component.text("menu"));
        for(var channel:selector.channels())assertTrue(h.service.presentationService().process(new PresentationRequest("test:menu",channel,receiver,payload,Optional.of(h.snapshot()),Optional.empty(),false)).cancelled());
        var serverField = Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);Object original=serverField.get(null);
        try { serverField.set(null,h.server);h.service.pluginDisabled(new PluginDisableEvent(h.owner)); }
        finally { serverField.set(null,original); }
        assertFalse(owner.active());assertNull(selected[0]);assertEquals(3,stored[0]);
        assertTrue(api.configuration().find("example:count").isEmpty());assertTrue(api.texts().resolve(text).isEmpty());
        assertTrue(api.tracking().menu(A).orElseThrow().options().isEmpty());
        for(var channel:selector.channels())assertFalse(h.service.presentationService().process(new PresentationRequest("test:menu",channel,receiver,payload,Optional.of(h.snapshot()),Optional.empty(),false)).cancelled());
    }
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);

    @Test void domainsAreExclusiveAndTokensCannotBeForgedAndCloseIsIdempotent() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        assertEquals(OperationStatus.CONTROL_CONFLICT, h.service.register(h.owner,
                new ExtensionOptions("conflict", Set.of(ControlDomain.ROLES))).status());
        assertEquals(OperationStatus.OK, h.service.register(h.owner, new ExtensionOptions("observer", Set.of())).status());
        ExtensionRegistration forged = new ExtensionRegistration() {
            @Override public String id() { return roles.id(); }
            @Override public Set<ControlDomain> controls() { return roles.controls(); }
            @Override public boolean active() { return true; }
            @Override public void close() {}
        };
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION,
                h.service.roles().reroll(new MutationContext(forged, h.session.id(), "forged"), 2).status());
        assertEquals(OperationStatus.CONTROL_CONFLICT,
                h.service.control().stop(new MutationContext(roles, h.session.id(), "wrong domain")).status());
        roles.close(); roles.close();
        assertFalse(roles.active());
        assertEquals(OperationStatus.OK, h.service.register(h.owner,
                new ExtensionOptions("replacement", Set.of(ControlDomain.ROLES))).status());
    }

    @Test void pluginDisableClosesOnlyTheOwnersRegistrationsAndDoesNotChangeRoles() throws ReflectiveOperationException {
        Harness h = new Harness();
        ExtensionRegistration owned = h.register("owned", Set.of(ControlDomain.ROLES));
        Plugin other = owner("other");
        ExtensionRegistration observer = h.service.register(other, new ExtensionOptions("other", Set.of())).registration().orElseThrow();
        MatchSnapshot before = h.snapshot();
        // Paper's ServerEvent constructor consults the static Bukkit server even with a fake service boundary.
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object originalServer = serverField.get(null);
        try {
            serverField.set(null, h.server);
            h.service.pluginDisabled(new PluginDisableEvent(h.owner));
        } finally { serverField.set(null, originalServer); }
        assertFalse(owned.active()); assertTrue(observer.active());
        assertEquals(before, h.snapshot());
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION,
                h.service.roles().assign(h.context(owned), h.swapped()).status());
    }

    @Test void mainThreadAndServiceLifetimeGatesApplyWithoutReadingStateOffThread() {
        Harness h = new Harness();
        ExtensionRegistration registration = h.register("owner", Set.of(ControlDomain.ROLES));
        h.mainThread = false;
        assertThrows(IllegalStateException.class, () -> h.service.matches().current());
        assertThrows(IllegalStateException.class, registration::close);
        OperationResult wrongThread = h.service.roles().assign(h.context(registration), h.swapped());
        assertEquals(OperationStatus.WRONG_THREAD, wrongThread.status());
        assertTrue(wrongThread.match().isEmpty());
        assertEquals(OperationStatus.WRONG_THREAD,
                h.service.register(h.owner, new ExtensionOptions("late", Set.of())).status());
        h.mainThread = true;
        h.service.shutdown();
        assertFalse(registration.active());
        assertTrue(h.service.matches().current().isEmpty());
        assertEquals(OperationStatus.SERVICE_UNAVAILABLE,
                h.service.roles().assign(h.context(registration), h.swapped()).status());
    }

    @Test void cancelledRoleChangesAndReentrantRequestsHaveNoSideEffects() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        MatchSnapshot before = h.snapshot();
        h.listener = event -> {
            if (event instanceof RolesChangingEvent changing) {
                assertEquals(OperationStatus.BUSY, h.service.roles().assign(h.context(roles), h.swapped()).status());
                assertEquals(OperationStatus.BUSY, h.service.register(h.owner, new ExtensionOptions("nested", Set.of())).status());
                changing.setCancelled(true);
            }
        };
        assertEquals(OperationStatus.CANCELLED, h.service.roles().assign(h.context(roles), h.swapped()).status());
        assertEquals(before, h.snapshot());
        assertEquals(0, h.rolesSynced);
        assertEquals(0, h.events.stream().filter(RolesChangedEvent.class::isInstance).count());
    }

    @Test void roleEventsRevalidateOwnershipRevisionAndPlayerAvailability() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        h.listener = event -> { if (event instanceof RolesChangingEvent) roles.close(); };
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION, h.service.roles().assign(h.context(roles), h.swapped()).status());
        assertEquals(0, h.rolesSynced);
        ExtensionRegistration fresh = h.register("fresh", Set.of(ControlDomain.ROLES));
        h.listener = event -> { if (event instanceof RolesChangingEvent) h.unavailable.add(A); };
        assertEquals(OperationStatus.PLAYER_UNAVAILABLE, h.service.roles().assign(h.context(fresh), h.swapped()).status());
        assertEquals(0, h.rolesSynced);
        h.unavailable.clear();
        h.listener = event -> {
            if (event instanceof RolesChangingEvent) {
                h.manager.bindTargetEnd(h.session.id(), A, UUID.randomUUID());
            }
        };
        assertEquals(OperationStatus.STALE_REVISION, h.service.roles().assign(h.context(fresh), h.swapped()).status());
        assertEquals(0, h.rolesSynced);
    }

    @Test void assignmentRequiresCompleteRosterAndPreservesEliminatedRolesAndDetachedSnapshots() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        assertEquals(OperationStatus.INVALID_ASSIGNMENT,
                h.service.roles().assign(h.context(roles), Map.of(A, TeamRole.HUNTER)).status());
        MatchSnapshot before = h.snapshot();
        assertEquals(OperationStatus.OK, h.service.roles().assign(h.context(roles), h.swapped()).status());
        assertEquals(1, h.rolesSynced);
        assertEquals(TeamRole.RUNNER, before.participants().get(A).role());
        assertEquals(TeamRole.HUNTER, h.snapshot().participants().get(A).role());
        assertThrows(UnsupportedOperationException.class, () -> before.participants().clear());
        assertEquals(OperationStatus.STALE_REVISION, h.service.roles().assign(
                new MutationContext(roles, h.session.id(), before.revision(), "stale"), h.original()).status());
        assertEquals(OperationStatus.STALE_SESSION, h.service.roles().assign(
                new MutationContext(roles, UUID.randomUUID(), "stale"), h.original()).status());
        h.manager.eliminateParticipant(h.session.id(), C, Optional.empty(), NOW);
        assertEquals(OperationStatus.INVALID_ASSIGNMENT, h.service.roles().assign(h.context(roles), h.original()).status());
    }

    @Test void identicalAssignmentsDoNotFireEventsOrResynchronizeRoles() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        MatchSnapshot before = h.snapshot();
        assertEquals(OperationStatus.OK, h.service.roles().assign(h.context(roles), h.original()).status());
        assertEquals(before, h.snapshot());
        assertTrue(h.events.isEmpty()); assertEquals(0, h.rolesSynced);
    }

    @Test void repeatedRoleSynchronizationFailureStopsMatchWithoutReportingSuccess() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        h.syncFailures = 2;
        assertEquals(OperationStatus.SERVICE_UNAVAILABLE, h.service.roles().assign(h.context(roles), h.swapped()).status());
        assertEquals(2, h.rolesSynced);
        assertEquals(1, h.stopCalls);
        assertEquals(SessionState.SETTLING, h.session.state());
        assertTrue(h.events.stream().noneMatch(RolesChangedEvent.class::isInstance));
    }

    @Test void offlineAssignmentsPreservePendingResetDebtAndCanBeSavedForRestart() {
        Harness h=new Harness();var owner=h.register("offline",Set.of(ControlDomain.ROLES));
        h.offline.addAll(Set.of(A,C));h.pending.markResetPending(A);h.pending.markResetPending(C);
        UUID end=UUID.randomUUID();h.manager.bindTargetEnd(h.session.id(),B,end);
        assertEquals(OperationStatus.OK,h.service.roles().assign(h.context(owner),h.swapped()).status());
        assertEquals(TeamRole.HUNTER,h.snapshot().participants().get(A).role());
        assertEquals(TeamRole.RUNNER,h.snapshot().participants().get(C).role());
        assertTrue(h.pending.needsReset(A));assertTrue(h.pending.needsReset(C));
        assertEquals(Optional.of(end),h.session.targetEndWorldId());assertEquals(Optional.of(NOW),h.session.startedAt());
        var restarted=new GameManager(new RoleAssigner(new Random(9)));assertTrue(restarted.restore(h.session.snapshot()));
        assertEquals(Optional.of(Role.HUNTER),restarted.activeSession().orElseThrow().roleOf(A));
        assertEquals(Optional.of(Role.RUNNER),restarted.activeSession().orElseThrow().roleOf(C));
        assertEquals(1,h.rolesSynced);assertEquals(1,h.events.stream().filter(RolesChangedEvent.class::isInstance).count());
    }

    @Test void goingOfflineDuringTheChangingEventDoesNotInvalidateTheAssignment() {
        Harness h=new Harness();var owner=h.register("disconnect",Set.of(ControlDomain.ROLES));
        h.listener=event->{if(event instanceof RolesChangingEvent)h.offline.addAll(Set.of(A,C));};
        assertEquals(OperationStatus.OK,h.service.roles().assign(h.context(owner),h.swapped()).status());
        assertEquals(TeamRole.HUNTER,h.snapshot().participants().get(A).role());assertEquals(1,h.rolesSynced);
    }

    @Test void comingOnlineBeforeCommitRechecksPendingResetWithoutPartiallyChangingRoles() {
        Harness h=new Harness();var owner=h.register("reconnect",Set.of(ControlDomain.ROLES));
        h.offline.add(A);h.pending.markResetPending(A);var before=h.snapshot();
        h.listener=event->{if(event instanceof RolesChangingEvent)h.offline.remove(A);};
        assertEquals(OperationStatus.PLAYER_UNAVAILABLE,h.service.roles().assign(h.context(owner),h.swapped()).status());
        assertEquals(before,h.snapshot());assertEquals(0,h.rolesSynced);assertTrue(h.pending.needsReset(A));
    }

    @Test void rerollCanRemoveAnOfflineRunnerAndSelectAnOfflineHunter() {
        Harness first=new Harness();var owner=first.register("offline-runner",Set.of(ControlDomain.ROLES));
        first.offline.add(A);first.unavailable.addAll(Set.of(B,C,D));
        assertEquals(OperationStatus.OK,first.service.roles().reroll(first.context(owner),1).status());
        assertEquals(List.of(B),first.session.livingRunnerIds());assertEquals(Optional.of(Role.HUNTER),first.session.roleOf(A));
        Harness second=new Harness();var other=second.register("offline-hunter",Set.of(ControlDomain.ROLES));
        second.offline.add(C);second.unavailable.addAll(Set.of(A,B,D));
        assertEquals(OperationStatus.OK,second.service.roles().reroll(second.context(other),3).status());
        assertEquals(Optional.of(Role.RUNNER),second.session.roleOf(C));assertEquals(List.of(D),second.session.livingHunterIds());
    }

    @Test void allOfflineParticipantsCanBeRerolledButEliminatedRolesRemainFixed() {
        Harness h=new Harness();var owner=h.register("all-offline",Set.of(ControlDomain.ROLES));h.offline.addAll(Set.of(A,B,C,D));
        h.manager.eliminateParticipant(h.session.id(),D,Optional.empty(),NOW);
        assertEquals(OperationStatus.OK,h.service.roles().reroll(h.context(owner),1).status());
        assertEquals(1,h.session.livingRunnerIds().size());assertEquals(2,h.session.livingHunterIds().size());
        assertTrue(h.session.isEliminatedParticipant(D));assertEquals(Optional.of(Role.HUNTER),h.session.roleOf(D));
        var invalid=new LinkedHashMap<UUID,TeamRole>();h.snapshot().participants().forEach((id,player)->invalid.put(id,player.role()));invalid.put(D,TeamRole.RUNNER);
        assertEquals(OperationStatus.INVALID_ASSIGNMENT,h.service.roles().assign(h.context(owner),invalid).status());
    }

    @Test void rerollCountsFixedUnreadyOnlineRunnersAndKeepsEliminatedPlayers() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        h.unavailable.add(A);
        h.manager.eliminateParticipant(h.session.id(), D, Optional.empty(), NOW);
        assertEquals(OperationStatus.INVALID_ASSIGNMENT, h.service.roles().reroll(h.context(roles), 0).status());
        assertEquals(OperationStatus.OK, h.service.roles().reroll(h.context(roles), 1).status());
        assertTrue(h.session.isRunnerAlive(A));
        assertEquals(List.of(A), h.session.livingRunnerIds());
        assertEquals(Optional.of(Role.HUNTER), h.session.roleOf(D));
        assertTrue(h.session.isEliminatedParticipant(D));
        assertEquals(OperationStatus.INVALID_ASSIGNMENT, h.service.roles().reroll(h.context(roles), 3).status());
    }

    @Test void rerollAllowsAllCandidatesWhenAnUnreadyOnlineHunterKeepsTheTeamsValid() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        h.unavailable.add(D);
        assertEquals(OperationStatus.OK, h.service.roles().reroll(h.context(roles), 3).status());
        assertEquals(3, h.session.livingRunnerIds().size());
        assertEquals(List.of(D), h.session.livingHunterIds());
    }

    @Test void participantMutationsUsePoliciesAndEventsAndPreserveExplicitRevival() {
        Harness h = new Harness();
        ExtensionRegistration participants = h.register("participants", Set.of(ControlDomain.PARTICIPANTS));
        h.listener = event -> {
            if (event instanceof ParticipantEliminatingEvent eliminating) eliminating.setCancelled(true);
        };
        assertEquals(OperationStatus.CANCELLED, h.service.participants().eliminate(h.context(participants), C).status());
        assertFalse(h.session.isEliminatedParticipant(C));
        h.listener = event -> {};
        assertEquals(OperationStatus.OK, h.service.participants().eliminate(h.context(participants), C).status());
        assertTrue(h.session.isEliminatedParticipant(C));
        assertEquals(OperationStatus.INVALID_STATE, h.service.participants().eliminate(h.context(participants), C).status());
        assertEquals(OperationStatus.OK, h.service.participants().revive(h.context(participants), C).status());
        assertFalse(h.session.isEliminatedParticipant(C));
        assertEquals(OperationStatus.INVALID_STATE, h.service.participants().revive(h.context(participants), C).status());
        assertEquals(1, h.events.stream().filter(ParticipantEliminatedEvent.class::isInstance).count());
        assertEquals(1, h.events.stream().filter(ParticipantRevivedEvent.class::isInstance).count());
    }

    @Test void coreHooksBlockReentrantWritesAndPublishLifecycleSnapshots() {
        Harness h = new Harness();
        ExtensionRegistration roles = h.register("roles", Set.of(ControlDomain.ROLES));
        h.listener = event -> assertEquals(OperationStatus.BUSY,
                h.service.roles().reroll(h.context(roles), 2).status());
        assertTrue(h.service.preparing(h.session, 5));
        h.service.countdown(h.session, 5);
        h.service.started(h.session);
        UUID end = UUID.randomUUID();
        h.service.endEntered(h.session, A, end);
        h.service.ended(h.session, TerminationReason.ADMIN_STOPPED, null);
        assertEquals(List.of(MatchPreparingEvent.class, MatchCountdownStartedEvent.class, MatchStartedEvent.class,
                TargetEndEnteredEvent.class, MatchEndedEvent.class), h.events.stream().map(Object::getClass).toList());
        MatchEndedEvent ended = (MatchEndedEvent) h.events.getLast();
        assertTrue(ended.getVictory().isEmpty());
    }

    @Test void eliminationDefaultsPoliciesCancellationAndPolicyErrorsAreSafe() {
        Harness h = new Harness();
        assertFalse(h.service.shouldEliminate(h.session, C, "DEATH"));
        assertTrue(h.service.shouldEliminate(h.session, C, "API"));
        h.listener = event -> { if (event instanceof ParticipantEliminatingEvent eliminating) eliminating.setCancelled(true); };
        assertFalse(h.service.shouldEliminate(h.session, A, "DEATH"));
        h.listener = event -> {};
        h.service.register(h.owner, new ExtensionOptions("elimination", Set.of(ControlDomain.ELIMINATION),
                Optional.of(context -> { throw new AssertionError("broken policy"); }), Optional.empty()));
        assertFalse(h.service.shouldEliminate(h.session, A, "DEATH"));
        assertTrue(h.session.isRunnerAlive(A));
    }

    @Test void victoryPolicyExceptionsDeferAndExplicitFinishStillFiresCancellableEvent() {
        Harness h = new Harness();
        h.manager.onVictoryDecision(h.service::decidingVictory);
        ExtensionRegistration match = h.service.register(h.owner,
                new ExtensionOptions("victory", Set.of(ControlDomain.VICTORY), Optional.empty(),
                        Optional.of(context -> { throw new IllegalStateException("broken policy"); })))
                .registration().orElseThrow();
        GameResult proposal = h.proposal();
        assertTrue(h.service.decidingVictory(h.session, proposal).isEmpty());
        assertEquals(SessionState.RUNNING, h.session.state());
        h.listener = event -> { if (event instanceof MatchFinishingEvent finishing) finishing.setCancelled(true); };
        assertEquals(OperationStatus.CANCELLED, h.service.control().finish(h.context(match), new Victory(MatchWinner.RUNNERS, "manual")).status());
        assertEquals(SessionState.RUNNING, h.session.state());
        h.listener = event -> {};
        assertEquals(OperationStatus.OK, h.service.control().finish(h.context(match), new Victory(MatchWinner.RUNNERS, "manual")).status());
        assertEquals(SessionState.SETTLING, h.session.state());
        assertEquals("manual", h.finished.customReason());
        assertEquals(2, h.events.stream().filter(MatchFinishingEvent.class::isInstance).count());
    }

    @Test void replacementVictoryPolicyAndFinishingEventCanChangeWinnerAndReason() {
        Harness h = new Harness();
        h.service.register(h.owner, new ExtensionOptions("victory", Set.of(ControlDomain.VICTORY), Optional.empty(),
                Optional.of(context -> VictoryDecision.replace(new Victory(MatchWinner.RUNNERS, "policy")))));
        h.listener = event -> {
            if (event instanceof MatchFinishingEvent finishing) {
                assertEquals("policy", finishing.getVictory().reason());
                finishing.setVictory(new Victory(MatchWinner.HUNTERS, "listener"));
            }
        };
        GameResult result = h.service.decidingVictory(h.session, h.proposal()).orElseThrow();
        assertEquals(GameWinner.HUNTERS, result.winner());
        assertEquals(GameResultReason.CUSTOM, result.reason());
        assertEquals("listener", result.customReason());
        assertEquals(SessionState.RUNNING, h.session.state());
    }

    private static final class Harness implements ExtensionRuntime {
        final GameManager manager;
        final Plugin owner = owner("test");
        final Set<UUID> unavailable = new HashSet<>();
        final Set<UUID> offline = new HashSet<>();
        final com.caxture.manhuntuniversal.player.PendingPlayerState pending=new com.caxture.manhuntuniversal.player.PendingPlayerState();
        final List<Event> events = new ArrayList<>();
        Consumer<Event> listener = event -> {};
        boolean mainThread = true;
        int rolesSynced, roleReveals, syncFailures, stopCalls;
        GameResult finished;
        final GameSession session;
        final PaperExtensionService service;
        final Server server;

        Harness() {
            this(new Random(7));
        }
        Harness(Random random) {
            manager=new GameManager(new RoleAssigner(random));
            manager.teamSelections().choose(A, TeamPreference.RUNNER, 2);
            manager.teamSelections().choose(B, TeamPreference.RUNNER, 2);
            session = manager.start(List.of(new Participant(A, "a"), new Participant(B, "b"),
                            new Participant(C, "c"), new Participant(D, "d")),
                    new MatchSettings(2, false, new TrackingSettingsSnapshot(512, 30, 4096)), NOW).session().orElseThrow();
            manager.activate(session.id(), NOW);
            PluginManager plugins = proxy(PluginManager.class, (object, method, args) -> {
                if (method.getName().equals("callEvent")) {
                    Event event = (Event) args[0]; events.add(event); listener.accept(event); return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
            server = proxy(Server.class, (object, method, args) -> switch (method.getName()) {
                case "isPrimaryThread" -> mainThread;
                case "getPluginManager" -> plugins;
                case "getPlayer" -> offline.contains(args[0]) ? null : proxy(org.bukkit.entity.Player.class, (player, operation, ignored) -> switch(operation.getName()) {
                    case "isOnline" -> true;
                    case "isDead" -> unavailable.contains(args[0]);
                    default -> throw new UnsupportedOperationException(operation.getName());
                });
                default -> throw new UnsupportedOperationException(method.getName());
            });
            service = new PaperExtensionService(server, Logger.getLogger("extension-tests"), manager, this,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
        ExtensionRegistration register(String id, Set<ControlDomain> domains) {
            return service.register(owner, new ExtensionOptions(id, domains)).registration().orElseThrow();
        }
        MatchSnapshot snapshot() { return service.matches().current().orElseThrow(); }
        MutationContext context(ExtensionRegistration token) { return new MutationContext(token, session.id(), "test"); }
        Map<UUID, TeamRole> original() { return Map.of(A, TeamRole.RUNNER, B, TeamRole.RUNNER, C, TeamRole.HUNTER, D, TeamRole.HUNTER); }
        Map<UUID, TeamRole> swapped() { return Map.of(A, TeamRole.HUNTER, B, TeamRole.RUNNER, C, TeamRole.RUNNER, D, TeamRole.HUNTER); }
        GameResult proposal() {
            return new GameResult(session.id(), GameWinner.HUNTERS, GameResultReason.RUNNER_DIED, NOW,
                    session.participants(), session.runnerIds(), session.hunterIds(), session.roundHistory(), Optional.empty(), Optional.empty());
        }
        @Override public OperationStatus start(int seconds) { return OperationStatus.START_REJECTED; }
        @Override public boolean stop() {
            stopCalls++;
            return manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, NOW).isPresent();
        }
        @Override public boolean available(UUID id) { return com.caxture.manhuntuniversal.runtime.RoleChangeAvailability.available(server,pending,id); }
        @Override public void rolesChanged(GameSession session) {
            rolesSynced++;
            if (syncFailures-- > 0) throw new IllegalStateException("synchronization failed");
        }
        @Override public void revealRoles(GameSession session) { roleReveals++; }
        @Override public boolean eliminate(UUID sessionId, UUID id) {
            if (!service.shouldEliminate(session, id, "API")) return false;
            manager.eliminateParticipant(sessionId, id, Optional.empty(), NOW);
            if (!session.isEliminatedParticipant(id)) return false;
            service.eliminated(session, id, "API");
            return true;
        }
        @Override public boolean revive(UUID sessionId, UUID id) {
            if (!manager.reviveParticipant(sessionId, id)) return false;
            service.revived(session, id);
            return true;
        }
        @Override public boolean finish(UUID sessionId, GameWinner winner, String reason) {
            finished = manager.finishCustom(sessionId, winner, reason, NOW).orElse(null);
            return finished != null;
        }
    }

    private static Plugin owner(String name) {
        return proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            case "getName", "toString" -> name;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
