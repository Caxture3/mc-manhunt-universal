package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.game.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HunterStartWaitPolicyTest {
    final UUID runner=new UUID(0,1),hunter=new UUID(0,2),other=new UUID(0,3);
    GameManager games(boolean reset){var games=new GameManager(new RoleAssigner(new Random(1)));games.teamSelections().choose(runner,TeamPreference.RUNNER,1);
        games.start(List.of(new Participant(runner,"R"),new Participant(hunter,"H"),new Participant(other,"O")),new MatchSettings(1,reset,new TrackingSettingsSnapshot(512,30,4096)),Instant.EPOCH);return games;}
    @Test void onlyResetEnabledRunningHuntersWaitAndRemainingSecondsRoundUp(){
        var games=games(true);var session=games.activeSession().orElseThrow();assertEquals(0,HunterStartWaitPolicy.remaining(session,30,Instant.EPOCH));games.activate(session.id(),Instant.EPOCH);
        assertTrue(HunterStartWaitPolicy.held(session,hunter,30,Instant.EPOCH));assertFalse(HunterStartWaitPolicy.held(session,runner,30,Instant.EPOCH));
        assertEquals(30,HunterStartWaitPolicy.remaining(session,30,Instant.EPOCH.plusMillis(1)));assertEquals(1,HunterStartWaitPolicy.remaining(session,30,Instant.EPOCH.plusMillis(29_999)));
        assertEquals(0,HunterStartWaitPolicy.remaining(session,30,Instant.EPOCH.plusSeconds(30)));assertEquals(0,HunterStartWaitPolicy.remaining(session,0,Instant.EPOCH));
        var noReset=games(false);noReset.activate(noReset.activeSession().orElseThrow().id(),Instant.EPOCH);assertFalse(HunterStartWaitPolicy.held(noReset.activeSession().get(),hunter,30,Instant.EPOCH));
    }
    @Test void roleChangesEliminationAndPausedRestartCannotCreateANewDeadline(){
        var games=games(true);var session=games.activeSession().orElseThrow();games.activate(session.id(),Instant.EPOCH);
        games.assignRoles(session.id(),Map.of(runner,Role.HUNTER,hunter,Role.RUNNER,other,Role.HUNTER),Instant.EPOCH.plusSeconds(10));
        assertFalse(HunterStartWaitPolicy.held(session,hunter,30,Instant.EPOCH.plusSeconds(10)));assertTrue(HunterStartWaitPolicy.held(session,runner,30,Instant.EPOCH.plusSeconds(10)));
        var saved=session.snapshot().shift(Duration.ofHours(1));var resumed=new GameManager(new RoleAssigner(new Random(1)));resumed.restore(saved);
        assertEquals(20,HunterStartWaitPolicy.remaining(resumed.activeSession().get(),30,Instant.EPOCH.plusSeconds(3610)));
        games.eliminateParticipant(session.id(),runner,Optional.empty(),Instant.EPOCH.plusSeconds(11));assertFalse(HunterStartWaitPolicy.held(session,runner,30,Instant.EPOCH.plusSeconds(11)));
    }
}
