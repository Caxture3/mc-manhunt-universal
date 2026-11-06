package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.game.*;
import java.util.*;
import java.util.function.Function;
/** Frozen configuration, exact per-participant destinations and actual waiting anchors. */
public record StartupPlan(UUID sessionId,int waitSeconds,PlayerStartPositions positions,Map<UUID,StartDestination> targets,Map<UUID,StartDestination> anchors){
    public StartupPlan{Objects.requireNonNull(sessionId);Objects.requireNonNull(positions);if(waitSeconds<0)throw new IllegalArgumentException("Negative wait");targets=Map.copyOf(targets);anchors=Map.copyOf(anchors);}
    public StartupPlan anchor(UUID player,StartDestination point){Map<UUID,StartDestination> updated=new HashMap<>(anchors);updated.put(player,point);return new StartupPlan(sessionId,waitSeconds,positions,targets,updated);}
    public static StartupPlan assign(GameSession session,int wait,PlayerStartPositions config,StartDestination primary,Function<UUID,StartDestination> personal){
        Map<UUID,StartDestination> targets=new LinkedHashMap<>();int hunters=0,runners=0;
        if(session.settings().resetPlayersAtStart())for(var participant:session.participants()){
            boolean hunter=session.roleOf(participant.id()).orElseThrow()==Role.HUNTER;var team=hunter?config.hunter():config.runner();int index=hunter?hunters++:runners++;
            StartDestination destination=switch(team.mode()){
                case "world-spawn"->primary;case "player-spawn"->personal.apply(participant.id());
                case "custom"->{if(team.custom().isEmpty())throw new IllegalArgumentException("No custom start positions for "+(hunter?"hunter":"runner"));var point=team.custom().get(index%team.custom().size());yield new StartDestination(point,point.yaw(),point.pitch());}
                default->throw new IllegalArgumentException("Invalid mode");};targets.put(participant.id(),destination);
        }
        return new StartupPlan(session.id(),wait,config,targets,Map.of());
    }
    public Map<String,Object> toMap(){return Map.of("session",sessionId.toString(),"wait-seconds",waitSeconds,"positions",positions.toMap(),"targets",encode(targets),"anchors",encode(anchors));}
    private static Map<String,Object> encode(Map<UUID,StartDestination> values){Map<String,Object> result=new LinkedHashMap<>();values.forEach((id,value)->result.put(id.toString(),value.toMap()));return result;}
    public static StartupPlan fromMap(Map<?,?> map){return new StartupPlan(UUID.fromString((String)map.get("session")),intValue(map.get("wait-seconds")),PlayerStartPositions.fromMap((Map<?,?>)map.get("positions")),decode(map.get("targets")),decode(map.get("anchors")));}
    private static int intValue(Object raw){if(!(raw instanceof Byte||raw instanceof Short||raw instanceof Integer||raw instanceof Long)||((Number)raw).longValue()<0||((Number)raw).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid waiting seconds");return ((Number)raw).intValue();}
    private static Map<UUID,StartDestination> decode(Object raw){if(!(raw instanceof Map<?,?> map))throw new IllegalArgumentException("Expected destinations");Map<UUID,StartDestination> result=new LinkedHashMap<>();map.forEach((id,value)->result.put(UUID.fromString((String)id),StartDestination.fromMap((Map<?,?>)value)));return result;}
}
