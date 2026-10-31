package com.caxture.manhuntuniversal.game;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionSnapshotTest {
    private final Instant start=Instant.parse("2026-11-01T00:00:00Z");
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    private GameManager manager(){return new GameManager(new RoleAssigner(new Random(1)));}
    private GameSession start(GameManager games){games.teamSelections().choose(a,TeamPreference.RUNNER,2);games.teamSelections().choose(b,TeamPreference.RUNNER,2);return games.start(List.of(new Participant(a,"A"),new Participant(b,"B"),new Participant(c,"C")),new MatchSettings(2,false,new TrackingSettingsSnapshot(512,30,4096)),start).session().orElseThrow();}
    @Test void runningRestorePreservesIdentityEliminationEndAndPausedTimeWithoutActivation(){
        var games=manager();var game=start(games);games.activate(game.id(),start.plusSeconds(5));
        games.recordRunnerDeath(game.id(),a,Optional.empty(),start.plusSeconds(20));UUID end=UUID.randomUUID();games.bindTargetEnd(game.id(),b,end);
        var restored=manager();var shifted=game.snapshot().shift(Duration.ofHours(4));assertTrue(restored.restore(shifted));
        var actual=restored.activeSession().orElseThrow();
        assertEquals(game.id(),actual.id());assertEquals(game.revision(),actual.revision());assertEquals(game.runnerIds(),actual.runnerIds());
        assertTrue(actual.isEliminatedParticipant(a));assertEquals(end,actual.targetEndWorldId().orElseThrow());
        assertEquals(Duration.ofSeconds(15),Duration.between(actual.startedAt().orElseThrow(),start.plus(Duration.ofHours(4)).plusSeconds(20)));
        assertEquals(SessionState.RUNNING,actual.state());assertFalse(restored.restore(shifted));
    }
    @Test void countdownRoundTripDoesNotStartOrResetTheGame(){var game=start(manager());var restored=manager();assertTrue(restored.restore(game.snapshot()));var actual=restored.activeSession().orElseThrow();assertEquals(SessionState.COUNTDOWN,actual.state());assertTrue(actual.startedAt().isEmpty());assertEquals(game.settings(),actual.settings());}
    @Test void duplicateRosterAndUnknownEliminatedPlayersAreRejectedBeforeInstallation(){
        var value=start(manager()).snapshot();
        assertThrows(IllegalArgumentException.class,()->new SessionSnapshot(value.id(),value.createdAt(),List.of(new Participant(a,"A"),new Participant(a,"B")),value.settings(),value.runners(),Set.of(),value.state(),value.startedAt(),value.roundStartedAt(),value.targetEndWorldId(),value.revision(),value.rounds()));
        assertThrows(IllegalArgumentException.class,()->new SessionSnapshot(value.id(),value.createdAt(),value.participants(),value.settings(),value.runners(),Set.of(UUID.randomUUID()),value.state(),value.startedAt(),value.roundStartedAt(),value.targetEndWorldId(),value.revision(),value.rounds()));
    }
}
