package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.game.*;
import org.bukkit.*;
import java.util.*;
/** Resolves destinations once; pending resets never consult mutable next-match settings. */
public final class PlayerStartService {
    private final Server server;private StartupPlan plan;
    public PlayerStartService(Server server){this.server=server;}
    public static void checkCustomLists(ConfigurationValues values){if(!values.get(BooleanConfigKey.RESET_PLAYERS_AT_START))return;
        for(String role:List.of("hunter","runner")){var team=values.playerStartPositions().team(role);if(team.mode().equals("custom")&&team.custom().isEmpty())throw new IllegalArgumentException(role);}}
    public void freeze(GameSession session,ConfigurationValues values){
        plan=StartupPlan.assign(session,values.get(IntConfigKey.HUNTER_START_WAIT_SECONDS),values.playerStartPositions(),primary(),id->{var player=server.getPlayer(id);Location personal=player==null?null:player.getRespawnLocation();return personal==null?primary():StartDestination.from(personal);});
        validateTargets(session);
    }
    public Optional<StartupPlan> snapshot(){return Optional.ofNullable(plan);}
    public void restore(GameSession session,Optional<StartupPlan> saved){
        plan=saved.orElseGet(()->{Map<UUID,StartDestination> targets=new LinkedHashMap<>();if(session.settings().resetPlayersAtStart())session.participants().forEach(value->targets.put(value.id(),primary()));return new StartupPlan(session.id(),0,PlayerStartPositions.defaults(),targets,Map.of());});
        if(!plan.sessionId().equals(session.id()))throw new IllegalArgumentException("Startup session mismatch");
    }
    public void validateTargets(GameSession session){if(plan==null||!plan.sessionId().equals(session.id()))throw new IllegalArgumentException("Missing startup plan");
        if(session.settings().resetPlayersAtStart())for(var participant:session.participants())validate(target(participant.id()));}
    public Location target(UUID player){if(plan==null||!plan.targets().containsKey(player))throw new IllegalArgumentException("Missing participant start position");return plan.targets().get(player).location(server);}
    public void anchor(UUID player,Location point){if(plan!=null)plan=plan.anchor(player,StartDestination.from(point));}
    public void removeAnchor(UUID player){if(plan!=null&&plan.anchors().containsKey(player)){var anchors=new HashMap<>(plan.anchors());anchors.remove(player);plan=new StartupPlan(plan.sessionId(),plan.waitSeconds(),plan.positions(),plan.targets(),anchors);}}
    public Location anchor(UUID player){return plan==null?null:Optional.ofNullable(plan.anchors().get(player)).map(value->value.location(server)).orElse(null);}
    public void clear(){plan=null;}
    public int waitSeconds(GameSession session){return plan!=null&&plan.sessionId().equals(session.id())?plan.waitSeconds():0;}
    private StartDestination primary(){return StartDestination.from(server.getWorlds().getFirst().getSpawnLocation());}
    private void validate(Location point){var world=point.getWorld();if(point.getY()<world.getMinHeight()||point.getY()>=world.getMaxHeight()||Math.abs(point.getX())>server.getMaxWorldSize()||Math.abs(point.getZ())>server.getMaxWorldSize()||!world.getWorldBorder().isInside(point))throw new IllegalArgumentException("Invalid start position: "+world.getName()+" ("+point.getX()+", "+point.getY()+", "+point.getZ()+")");}
}
