package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.paper.PaperManhuntUniversalApi;
import com.caxture.manhuntuniversal.api.paper.event.*;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.runtime.ActiveGameCoordinator;
import com.caxture.manhuntuniversal.runtime.GameStartCoordinator;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread extension service. Runtime state stays authoritative in GameManager. */
public final class PaperExtensionService implements PaperManhuntUniversalApi, GameExtensionHooks, Listener {
    private final Server server;
    private final Logger logger;
    private final GameManager manager;
    private final ExtensionRuntime runtime;
    private final Clock clock;
    private final ExtensionRegistry registry;
    private boolean available = true;
    private int mutationDepth;
    private boolean explicitFinish;

    private final MatchQuery query = this::current;
    private final MatchController matchController = new MatchController() {
        @Override public OperationResult start(ExtensionRegistration extension, int seconds) {
            return mutate(extension, ControlDomain.MATCH, () -> {
                if (seconds < 0 || seconds > 60) {
                    return result(OperationStatus.INVALID_ARGUMENT, "Countdown must be between zero and sixty seconds");
                }
                if (manager.activeSession().isPresent()) return result(OperationStatus.INVALID_STATE, "Match already exists");
                OperationStatus status = runtime.start(seconds);
                return result(status, status == OperationStatus.OK ? "Match started" : "Start rejected");
            });
        }
        @Override public OperationResult stop(MutationContext context) {
            return mutate(context, ControlDomain.MATCH, false, () -> runtime.stop()
                    ? result(OperationStatus.OK, "Match stopped")
                    : result(OperationStatus.INVALID_STATE, "Match cannot be stopped"));
        }
        @Override public OperationResult finish(MutationContext context, Victory victory) {
            return mutate(context, ControlDomain.VICTORY, true, () -> {
                if (victory == null || victory.reason().isBlank()) {
                    return result(OperationStatus.INVALID_ARGUMENT, "Victory and a nonblank reason are required");
                }
                explicitFinish = true;
                try {
                    return runtime.finish(context.sessionId(), GameWinner.valueOf(victory.winner().name()), victory.reason())
                            ? result(OperationStatus.OK, "Match finished")
                            : result(OperationStatus.CANCELLED, "Finish was prevented");
                } finally { explicitFinish = false; }
            });
        }
    };
    private final RoleController roleController = new RoleController() {
        @Override public OperationResult assign(MutationContext context, Map<UUID, TeamRole> assignments) {
            return mutate(context, ControlDomain.ROLES, true, () -> assignRoles(context, assignments));
        }
        @Override public OperationResult reroll(MutationContext context, int runnerCount) {
            return mutate(context, ControlDomain.ROLES, true, () -> rerollRoles(context, runnerCount));
        }
    };
    private final ParticipantController participantController = new ParticipantController() {
        @Override public OperationResult eliminate(MutationContext context, UUID playerId) {
            return mutate(context, ControlDomain.PARTICIPANTS, true, () -> {
                GameSession session = manager.activeSession().orElseThrow();
                OperationResult failure = participantFailure(session, playerId, false);
                if (failure != null) return failure;
                return runtime.eliminate(session.id(), playerId)
                        ? result(OperationStatus.OK, "Participant eliminated")
                        : result(OperationStatus.CANCELLED, "Elimination was prevented");
            });
        }
        @Override public OperationResult revive(MutationContext context, UUID playerId) {
            return mutate(context, ControlDomain.PARTICIPANTS, true, () -> {
                GameSession session = manager.activeSession().orElseThrow();
                OperationResult failure = participantFailure(session, playerId, true);
                if (failure != null) return failure;
                return runtime.revive(session.id(), playerId)
                        ? result(OperationStatus.OK, "Participant revived")
                        : result(OperationStatus.PLAYER_UNAVAILABLE, "Participant cannot be revived now");
            });
        }
    };

    public PaperExtensionService(JavaPlugin plugin, GameManager manager, GameStartCoordinator start,
                                 ActiveGameCoordinator active, Clock clock) {
        this(plugin.getServer(), plugin.getLogger(), manager, new ExtensionRuntime() {
            @Override public OperationStatus start(int seconds) { return start.startForApi(seconds); }
            @Override public boolean stop() { return active.stopAdmin(); }
            @Override public boolean available(UUID id) { return active.playerAvailableForRoleChange(id); }
            @Override public void rolesChanged(GameSession session) { active.rolesChanged(session); }
            @Override public boolean eliminate(UUID session, UUID id) { return active.eliminateForApi(session, id); }
            @Override public boolean revive(UUID session, UUID id) { return active.reviveForApi(session, id); }
            @Override public boolean finish(UUID session, GameWinner winner, String reason) {
                return active.finishForApi(session, winner, reason);
            }
        }, clock);
    }

    PaperExtensionService(Server server, Logger logger, GameManager manager, ExtensionRuntime runtime, Clock clock) {
        this.server = Objects.requireNonNull(server);
        this.logger = Objects.requireNonNull(logger);
        this.manager = Objects.requireNonNull(manager);
        this.runtime = Objects.requireNonNull(runtime);
        this.clock = Objects.requireNonNull(clock);
        this.registry = new ExtensionRegistry(this::requireThread);
    }

    @Override public String apiVersion() { return "1.0.0"; }
    @Override public Set<Capability> capabilities() { return Set.of(Capability.values()); }
    @Override public MatchQuery matches() { return query; }
    @Override public MatchController control() { return matchController; }
    @Override public RoleController roles() { return roleController; }
    @Override public ParticipantController participants() { return participantController; }

    private Optional<MatchSnapshot> current() {
        requireThread();
        if (!available) return Optional.empty();
        return manager.activeSession().map(MatchSnapshots::of);
    }

    @Override public RegistrationResult register(Plugin owner, ExtensionOptions options) {
        OperationStatus denied = writeGate();
        if (denied != null) return new RegistrationResult(denied, Optional.empty(), denied.name());
        return registry.register(owner, options);
    }

    @EventHandler public void pluginDisabled(PluginDisableEvent event) { registry.unregisterOwner(event.getPlugin()); }

    public void shutdown() {
        requireThread();
        available = false;
        registry.clear();
    }

    @Override public <T> T inMutation(Supplier<T> action) {
        requireThread();
        mutationDepth++;
        try { return action.get(); }
        finally { mutationDepth--; }
    }

    @Override public boolean mutationActive() { return mutationDepth > 0; }

    private OperationStatus writeGate() {
        if (!server.isPrimaryThread()) return OperationStatus.WRONG_THREAD;
        if (!available) return OperationStatus.SERVICE_UNAVAILABLE;
        if (mutationDepth > 0) return OperationStatus.BUSY;
        return null;
    }

    private void requireThread() {
        if (!server.isPrimaryThread()) throw new IllegalStateException("Manhunt Universal API requires the server thread");
    }

    private OperationResult mutate(ExtensionRegistration token, ControlDomain domain, Supplier<OperationResult> action) {
        OperationStatus denied = writeGate();
        if (denied != null) return result(denied, denied.name());
        OperationStatus ownership = registry.validate(token, domain);
        if (ownership != OperationStatus.OK) return result(ownership, ownership.name());
        return inMutation(action);
    }

    private OperationResult mutate(MutationContext context, ControlDomain domain, boolean running,
                                   Supplier<OperationResult> action) {
        OperationStatus denied = writeGate();
        if (denied != null) return result(denied, denied.name());
        if (context == null) return result(OperationStatus.INVALID_ARGUMENT, "Mutation context is required");
        return mutate(context.extension(), domain, () -> {
            OperationResult failure = contextFailure(context, domain, running);
            return failure != null ? failure : action.get();
        });
    }

    private OperationResult contextFailure(MutationContext context, ControlDomain domain, boolean running) {
        OperationStatus ownership = registry.validate(context.extension(), domain);
        if (ownership != OperationStatus.OK) return result(ownership, ownership.name());
        if (context.sessionId() == null || context.expectedRevision() < -1 || context.reason() == null) {
            return result(OperationStatus.INVALID_ARGUMENT, "Invalid mutation context");
        }
        GameSession session = manager.activeSession().orElse(null);
        if (session == null) return result(OperationStatus.NO_ACTIVE_MATCH, "No active match");
        if (!session.id().equals(context.sessionId())) return result(OperationStatus.STALE_SESSION, "Session changed");
        if (context.expectedRevision() >= 0 && context.expectedRevision() != session.revision()) {
            return result(OperationStatus.STALE_REVISION, "Revision changed");
        }
        if (session.state() == SessionState.SETTLING || running && session.state() != SessionState.RUNNING) {
            return result(OperationStatus.INVALID_STATE, "Operation is not available in this match state");
        }
        return null;
    }

    private OperationResult result(OperationStatus status, String message) {
        // Failure results from asynchronous callers must not inspect mutable match state.
        Optional<MatchSnapshot> snapshot = server.isPrimaryThread() && available
                ? manager.activeSession().map(MatchSnapshots::of) : Optional.empty();
        return new OperationResult(status, message, snapshot);
    }

    private OperationResult participantFailure(GameSession session, UUID playerId, boolean eliminated) {
        if (playerId == null || session.participant(playerId).isEmpty()) {
            return result(OperationStatus.INVALID_ARGUMENT, "Player must be a participant");
        }
        if (session.isEliminatedParticipant(playerId) != eliminated) {
            return result(OperationStatus.INVALID_STATE, eliminated ? "Participant is not eliminated" : "Participant is eliminated");
        }
        return null;
    }

    private OperationResult assignRoles(MutationContext context, Map<UUID, TeamRole> supplied) {
        if (supplied == null || supplied.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            return result(OperationStatus.INVALID_ASSIGNMENT, "Complete nonnull assignments are required");
        }
        Map<UUID, TeamRole> proposed = Map.copyOf(supplied);
        GameSession session = manager.activeSession().orElseThrow();
        OperationResult failure = assignmentFailure(session, proposed);
        if (failure != null) return failure;
        boolean changed = session.participants().stream().anyMatch(player ->
                !session.roleOf(player.id()).orElseThrow().name().equals(proposed.get(player.id()).name()));
        if (!changed) return result(OperationStatus.OK, "Roles already match the assignment");
        MatchSnapshot before = MatchSnapshots.of(session);
        ChangeSource source = new ChangeSource(context.extension().id(), context.reason());
        RolesChangingEvent changing = new RolesChangingEvent(before, proposed, source);
        fire(changing);
        if (changing.isCancelled()) return result(OperationStatus.CANCELLED, "Role change cancelled");
        failure = contextFailure(context, ControlDomain.ROLES, true);
        if (failure != null) return failure;
        if (manager.activeSession().orElse(null) != session || session.revision() != before.revision()) {
            return result(OperationStatus.STALE_REVISION, "Match changed during the role event");
        }
        failure = assignmentFailure(session, proposed);
        if (failure != null) return failure;
        Map<UUID, Role> internal = new LinkedHashMap<>();
        proposed.forEach((id, role) -> internal.put(id, Role.valueOf(role.name())));
        if (!manager.assignRoles(session.id(), internal, Instant.now(clock))) {
            return result(OperationStatus.INVALID_ASSIGNMENT, "Assignment could not be committed");
        }
        try { runtime.rolesChanged(session); }
        catch (RuntimeException | LinkageError failureToPresent) {
            logger.log(Level.SEVERE, "Roles were committed but runtime synchronization failed; retrying", failureToPresent);
            try { runtime.rolesChanged(session); }
            catch (RuntimeException | LinkageError repeated) {
                logger.log(Level.SEVERE, "Role synchronization failed again; stopping the match to restore player state", repeated);
                try {
                    if (manager.activeSession().orElse(null) == session && !runtime.stop()) {
                        logger.severe("The match could not be stopped after role synchronization failed");
                    }
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    logger.log(Level.SEVERE, "Match cleanup failed after role synchronization failed", cleanupFailure);
                }
                return result(OperationStatus.SERVICE_UNAVAILABLE, "Role synchronization failed; match stop requested");
            }
        }
        if (manager.activeSession().orElse(null) != session || session.state() != SessionState.RUNNING) {
            return result(OperationStatus.INVALID_STATE, "Match ended while synchronizing roles");
        }
        fire(new RolesChangedEvent(before, MatchSnapshots.of(session), source));
        return result(OperationStatus.OK, "Roles changed");
    }

    private OperationResult assignmentFailure(GameSession session, Map<UUID, TeamRole> proposed) {
        Set<UUID> roster = new HashSet<>();
        session.participants().forEach(player -> roster.add(player.id()));
        if (!roster.equals(proposed.keySet())) return result(OperationStatus.INVALID_ASSIGNMENT, "Assignment must include exactly the roster");
        int runners = 0;
        int hunters = 0;
        for (UUID id : roster) {
            Role old = session.roleOf(id).orElseThrow();
            if (!old.name().equals(proposed.get(id).name())) {
                if (session.isEliminatedParticipant(id)) return result(OperationStatus.INVALID_ASSIGNMENT, "Eliminated roles must be preserved");
                if (!runtime.available(id)) return result(OperationStatus.PLAYER_UNAVAILABLE, "Changed players must be online, alive and ready");
            }
            if (!session.isEliminatedParticipant(id)) {
                if (proposed.get(id) == TeamRole.RUNNER) runners++; else hunters++;
            }
        }
        return runners > 0 && hunters > 0 ? null : result(OperationStatus.INVALID_ASSIGNMENT, "At least one living runner and hunter are required");
    }

    private OperationResult rerollRoles(MutationContext context, int runnerCount) {
        if (runnerCount < 0) return result(OperationStatus.INVALID_ARGUMENT, "Runner count must be nonnegative");
        GameSession session = manager.activeSession().orElseThrow();
        Map<UUID, TeamRole> assignment = new LinkedHashMap<>();
        List<UUID> candidates = new ArrayList<>();
        int fixedRunners = 0;
        for (Participant player : session.participants()) {
            UUID id = player.id();
            TeamRole role = TeamRole.valueOf(session.roleOf(id).orElseThrow().name());
            assignment.put(id, role);
            if (!session.isEliminatedParticipant(id)) {
                if (runtime.available(id)) candidates.add(id);
                else if (role == TeamRole.RUNNER) fixedRunners++;
            }
        }
        int selectedCount = runnerCount - fixedRunners;
        if (selectedCount < 0 || selectedCount > candidates.size()) {
            return result(OperationStatus.INVALID_ASSIGNMENT, "Runner count conflicts with fixed participants");
        }
        Set<UUID> runners = Set.copyOf(manager.randomRunners(candidates, selectedCount));
        candidates.forEach(id -> assignment.put(id, runners.contains(id) ? TeamRole.RUNNER : TeamRole.HUNTER));
        return assignRoles(context, assignment);
    }

    @Override public boolean preparing(GameSession session, int seconds) {
        return inMutation(() -> {
            MatchPreparingEvent event = new MatchPreparingEvent(MatchSnapshots.of(session), seconds);
            fire(event);
            return !event.isCancelled();
        });
    }
    @Override public void countdown(GameSession session, int seconds) {
        inMutation(() -> { fire(new MatchCountdownStartedEvent(MatchSnapshots.of(session), seconds)); return null; });
    }
    @Override public void started(GameSession session) {
        inMutation(() -> { fire(new MatchStartedEvent(MatchSnapshots.of(session))); return null; });
    }
    @Override public void ended(GameSession session, TerminationReason reason, GameResult result) {
        inMutation(() -> {
            fire(new MatchEndedEvent(MatchSnapshots.of(session), reason.name(), Optional.ofNullable(result).map(this::victory)));
            return null;
        });
    }
    @Override public boolean shouldEliminate(GameSession session, UUID playerId, String cause) {
        return inMutation(() -> {
            if (session.participant(playerId).isEmpty() || session.isEliminatedParticipant(playerId)) return false;
            MatchSnapshot before = MatchSnapshots.of(session);
            EliminationDecision decision = EliminationDecision.DEFAULT;
            Optional<EliminationPolicy> policy = registry.eliminationPolicy();
            if (policy.isPresent()) {
                try { decision = Objects.requireNonNull(policy.get().decide(new EliminationContext(before, playerId, cause))); }
                catch (Throwable error) {
                    logger.log(Level.SEVERE, "Extension elimination policy failed; elimination prevented", error);
                    return false;
                }
            }
            if (decision == EliminationDecision.PREVENT
                    || decision == EliminationDecision.DEFAULT && !"API".equals(cause) && !session.isRunnerAlive(playerId)) return false;
            ParticipantEliminatingEvent event = new ParticipantEliminatingEvent(before, playerId, cause);
            fire(event);
            return !event.isCancelled() && unchanged(session, before.revision());
        });
    }
    @Override public void eliminated(GameSession session, UUID id, String cause) {
        inMutation(() -> { fire(new ParticipantEliminatedEvent(MatchSnapshots.of(session), id, cause)); return null; });
    }
    @Override public void revived(GameSession session, UUID id) {
        inMutation(() -> { fire(new ParticipantRevivedEvent(MatchSnapshots.of(session), id)); return null; });
    }
    @Override public void endEntered(GameSession session, UUID id, UUID worldId) {
        inMutation(() -> { fire(new TargetEndEnteredEvent(MatchSnapshots.of(session), id, worldId)); return null; });
    }

    @Override public Optional<GameResult> decidingVictory(GameSession session, GameResult proposed) {
        return inMutation(() -> {
            MatchSnapshot before = MatchSnapshots.of(session);
            Victory selected = victory(proposed);
            if (!explicitFinish) {
                Optional<VictoryPolicy> policy = registry.victoryPolicy();
                if (policy.isPresent()) {
                    try {
                        VictoryDecision decision = Objects.requireNonNull(policy.get().evaluate(new VictoryContext(before, selected)));
                        if (decision.action() == VictoryDecision.Action.DEFER) return Optional.empty();
                        if (decision.action() == VictoryDecision.Action.REPLACE) selected = decision.replacement().orElseThrow();
                    } catch (Throwable error) {
                        logger.log(Level.SEVERE, "Extension victory policy failed; victory deferred", error);
                        return Optional.empty();
                    }
                }
            }
            MatchFinishingEvent event = new MatchFinishingEvent(before, selected);
            fire(event);
            if (event.isCancelled() || !unchanged(session, before.revision())) return Optional.empty();
            Victory chosen = event.getVictory();
            return Optional.of(chosen.equals(victory(proposed)) ? proposed : new GameResult(
                    proposed.sessionId(), GameWinner.valueOf(chosen.winner().name()), GameResultReason.CUSTOM,
                    proposed.settledAt(), proposed.participants(), proposed.runners(), proposed.hunters(),
                    proposed.roundHistory(), proposed.deadRunner(), proposed.killer(), chosen.reason()));
        });
    }

    private Victory victory(GameResult result) {
        return new Victory(MatchWinner.valueOf(result.winner().name()),
                result.reason() == GameResultReason.CUSTOM ? result.customReason() : result.reason().name());
    }

    private boolean unchanged(GameSession session, long revision) {
        return manager.activeSession().orElse(null) == session && session.revision() == revision;
    }

    private void fire(Event event) { server.getPluginManager().callEvent(event); }
}
