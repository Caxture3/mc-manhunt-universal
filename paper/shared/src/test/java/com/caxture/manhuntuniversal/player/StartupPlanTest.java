package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.game.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StartupPlanTest {
    final UUID world=UUID.randomUUID(),runner=new UUID(0,1),a=new UUID(0,2),b=new UUID(0,3),c=new UUID(0,4);
    final StartDestination spawn=new StartDestination(new StartPosition(world,"world",0,70,0),10,20);
    GameSession session(boolean reset){var games=new GameManager(new RoleAssigner(new Random(1)));games.teamSelections().choose(runner,TeamPreference.RUNNER,1);
        return games.start(List.of(new Participant(runner,"R"),new Participant(a,"A"),new Participant(b,"B"),new Participant(c,"C")),new MatchSettings(1,reset,new TrackingSettingsSnapshot(512,30,4096)),Instant.EPOCH).session().orElseThrow();}
    @Test void customRoleOrderCyclesAndSurplusPositionsAreIgnored(){
        var first=new StartPosition(world,"world",535,15,235);var second=new StartPosition(world,"world",532.5,17,238);
        var config=new PlayerStartPositions(new PlayerStartPositions.Team("custom",List.of(first,second)),PlayerStartPositions.defaults().runner());
        var plan=StartupPlan.assign(session(true),30,config,spawn,id->spawn);
        assertEquals(first,plan.targets().get(a).point());assertEquals(second,plan.targets().get(b).point());assertEquals(first,plan.targets().get(c).point());assertEquals(spawn,plan.targets().get(runner));
        assertEquals(plan,StartupPlan.fromMap(plan.toMap()));
    }
    @Test void personalRespawnAndDisabledResetBehaveIndependentlyOfSavedCustomLists(){
        var personal=new StartDestination(new StartPosition(UUID.randomUUID(),"nether",2,80,4),50,0);
        var config=new PlayerStartPositions(new PlayerStartPositions.Team("player-spawn",List.of()),new PlayerStartPositions.Team("custom",List.of()));
        var invalid=config;assertThrows(IllegalArgumentException.class,()->StartupPlan.assign(session(true),30,invalid,spawn,id->personal));
        var disabled=StartupPlan.assign(session(false),30,config,spawn,id->personal);assertTrue(disabled.targets().isEmpty());
        config=new PlayerStartPositions(config.hunter(),PlayerStartPositions.defaults().runner());
        assertEquals(personal,StartupPlan.assign(session(true),30,config,spawn,id->personal).targets().get(a));
    }
}
