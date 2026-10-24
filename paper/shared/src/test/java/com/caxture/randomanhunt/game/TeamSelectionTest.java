package com.caxture.randomanhunt.game;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamSelectionTest {
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);
    private final UUID d = new UUID(0, 4);
    private final GameManager manager = new GameManager(new RoleAssigner(new Random(15)));
    private final TeamSelectionService teams = manager.teamSelections();
    private final Instant now = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void onlySuccessfulChoiceMutationsNotifyAfterUpdatingThePreference() {
        List<String> changes = new ArrayList<>();
        teams.onChoiceChanged(id -> changes.add(id + ":" + teams.preferenceOf(id)));
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(b, TeamPreference.RUNNER, 2);
        teams.choose(c, TeamPreference.RUNNER, 2);
        teams.trimRunners(1);
        teams.remove(a);
        teams.remove(a);
        teams.choose(c, TeamPreference.HUNTER, 1);
        teams.clear();
        assertEquals(List.of(a + ":RUNNER", b + ":RUNNER", b + ":RANDOM", a + ":RANDOM",
                c + ":HUNTER", c + ":RANDOM"), changes);
    }

    @Test
    void fullRunnerSelectionPreservesThePreviousHunterChoice() {
        teams.choose(a, TeamPreference.RUNNER, 1);
        teams.choose(b, TeamPreference.HUNTER, 1);
        assertEquals(TeamSelectionService.ChoiceResult.FULL, teams.choose(b, TeamPreference.RUNNER, 1));
        assertEquals(TeamPreference.HUNTER, teams.preferenceOf(b));
        assertEquals(TeamSelectionService.ChoiceResult.UNCHANGED, teams.choose(a, TeamPreference.RUNNER, 1));
        teams.choose(a, TeamPreference.RANDOM, 1);
        assertEquals(TeamSelectionService.ChoiceResult.CHANGED, teams.choose(b, TeamPreference.RUNNER, 1));
    }

    @Test
    void shrinkingDropsLatestRunnersWithoutReorderingRepeatedChoices() {
        teams.choose(a, TeamPreference.RUNNER, 3);
        teams.choose(b, TeamPreference.RUNNER, 3);
        teams.choose(a, TeamPreference.RUNNER, 3);
        teams.choose(c, TeamPreference.RUNNER, 3);
        assertEquals(List.of(c, b), teams.trimRunners(1));
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(a));
        assertEquals(TeamPreference.RANDOM, teams.preferenceOf(b));
        assertEquals(TeamPreference.RANDOM, teams.preferenceOf(c));
        assertTrue(teams.trimRunners(3).isEmpty());
        assertEquals(TeamPreference.RANDOM, teams.preferenceOf(c));
    }

    @Test
    void leavingAndRejoiningRunnerGoesToTheBackAndDisconnectReleasesSlot() {
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(b, TeamPreference.RUNNER, 2);
        teams.choose(a, TeamPreference.HUNTER, 2);
        teams.choose(a, TeamPreference.RUNNER, 2);
        assertEquals(List.of(a), teams.trimRunners(1));
        teams.remove(b);
        assertEquals(TeamSelectionService.ChoiceResult.CHANGED, teams.choose(c, TeamPreference.RUNNER, 1));
    }

    @Test
    void explicitRunnersAreKeptAndHuntersExcludedEvenWhenAllRandomPlayersAreNeeded() {
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(b, TeamPreference.HUNTER, 2);
        teams.choose(d, TeamPreference.HUNTER, 2);
        GameSession session = start(2).session().orElseThrow();
        assertEquals(List.of(a, c), session.runnerIds());
        assertEquals(List.of(b, d), session.hunterIds());
    }

    @Test
    void explicitRunnersCanFillEverySlotWithoutAnyRandomPlayers() {
        teams.choose(a, TeamPreference.RUNNER, 1);
        for (UUID id : List.of(b, c, d)) teams.choose(id, TeamPreference.HUNTER, 1);
        assertEquals(List.of(a), start(1).session().orElseThrow().runnerIds());
    }

    @Test
    void insufficientRandomPoolDoesNotCreateSessionOrClearPreferences() {
        for (UUID id : List.of(b, c, d)) teams.choose(id, TeamPreference.HUNTER, 2);
        assertEquals(StartStatus.NOT_ENOUGH_RANDOM_PLAYERS, start(2).status());
        assertTrue(manager.activeSession().isEmpty());
        assertEquals(TeamPreference.HUNTER, teams.preferenceOf(b));
    }

    @Test
    void shrinkingWhileCountdownOrRunningDoesNotClearChoicesOrNotifyCapacityRemoval() {
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(b, TeamPreference.RUNNER, 2);
        GameSession session = start(2).session().orElseThrow();
        List<UUID> changes = new ArrayList<>();
        teams.onChoiceChanged(changes::add);
        assertTrue(teams.trimRunners(1).isEmpty());
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(b));
        manager.activate(session.id(), now);
        assertTrue(teams.trimRunners(0).isEmpty());
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(a));
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(b));
        assertTrue(changes.isEmpty());
    }

    @Test
    void countdownLocksSelectionsAndDepartureOrCapacityChangeCannotChangeLockedRoles() {
        teams.choose(a, TeamPreference.RUNNER, 2);
        teams.choose(b, TeamPreference.RUNNER, 2);
        GameSession session = start(2).session().orElseThrow();
        assertEquals(TeamSelectionService.ChoiceResult.LOCKED, teams.choose(c, TeamPreference.RUNNER, 2));
        teams.remove(a);
        teams.trimRunners(1);
        assertEquals(List.of(a, b), session.runnerIds());
        manager.cancel(session.id(), TerminationReason.ADMIN_STOPPED, now).orElseThrow();
        manager.clearSettled(session.id()).orElseThrow();
        assertTrue(teams.snapshot().isEmpty());
    }

    @Test
    void tooFewPlayersKeepsChoicesAndNormalSettlementClearsThem() {
        teams.choose(a, TeamPreference.RUNNER, 1);
        var settings = new MatchSettings(1, false, new TrackingSettingsSnapshot(512, 30, 4096));
        assertEquals(StartStatus.NOT_ENOUGH_PLAYERS, manager.start(List.of(new Participant(a, "A")), settings, now).status());
        assertEquals(TeamPreference.RUNNER, teams.preferenceOf(a));
        GameSession session = start(1).session().orElseThrow();
        manager.activate(session.id(), now);
        manager.recordRunnerDeath(session.id(), a, java.util.Optional.empty(), now.plusSeconds(10)).orElseThrow();
        manager.clearSettled(session.id()).orElseThrow();
        assertTrue(teams.snapshot().isEmpty());
    }

    private StartResult start(int runnerCount) {
        return manager.start(List.of(new Participant(a, "A"), new Participant(b, "B"),
                        new Participant(c, "C"), new Participant(d, "D")),
                new MatchSettings(runnerCount, false, new TrackingSettingsSnapshot(512, 30, 4096)), now);
    }
}
