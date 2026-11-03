package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.game.*;
import java.time.*;
import java.util.UUID;
public final class HunterStartWaitPolicy {
    private HunterStartWaitPolicy(){}
    public static long remaining(GameSession game,int seconds,Instant now){
        if(game==null||game.state()!=SessionState.RUNNING||!game.settings().resetPlayersAtStart()||seconds<=0)return 0;
        Instant deadline=game.startedAt().orElseThrow().plusSeconds(seconds);if(!now.isBefore(deadline))return 0;
        Duration duration=Duration.between(now,deadline);return duration.getSeconds()+(duration.getNano()==0?0:1);
    }
    public static boolean held(GameSession game,UUID player,int seconds,Instant now){return remaining(game,seconds,now)>0
            &&game.roleOf(player).orElse(null)==Role.HUNTER&&!game.isEliminatedParticipant(player);}
}
