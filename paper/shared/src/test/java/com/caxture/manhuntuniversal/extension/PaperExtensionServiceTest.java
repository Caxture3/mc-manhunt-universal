package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
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

    @Test void rerollCountsFixedOfflineRunnersAndKeepsEliminatedPlayers() {
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

    @Test void rerollAllowsAllCandidatesWhenAnOfflineHunterKeepsTheTeamsValid() {
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
        final GameManager manager = new GameManager(new RoleAssigner(new Random(7)));
        final Plugin owner = owner("test");
        final Set<UUID> unavailable = new HashSet<>();
        final List<Event> events = new ArrayList<>();
        Consumer<Event> listener = event -> {};
        boolean mainThread = true;
        int rolesSynced, syncFailures, stopCalls;
        GameResult finished;
        final GameSession session;
        final PaperExtensionService service;
        final Server server;

        Harness() {
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
        @Override public boolean available(UUID id) { return !unavailable.contains(id); }
        @Override public void rolesChanged(GameSession session) {
            rolesSynced++;
            if (syncFailures-- > 0) throw new IllegalStateException("synchronization failed");
        }
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
