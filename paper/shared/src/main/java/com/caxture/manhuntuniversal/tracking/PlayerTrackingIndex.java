package com.caxture.manhuntuniversal.tracking;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import java.util.*;
/** Bounded process-local observations; never loads offline data or enters persisted roster history. */
final class PlayerTrackingIndex {
    private record Observation(String name,WorldPosition position){}
    private final int capacity;
    private final Map<UUID,Observation> observations=new LinkedHashMap<>();
    PlayerTrackingIndex(int capacity){if(capacity<1)throw new IllegalArgumentException("Invalid capacity");this.capacity=capacity;}
    void observe(UUID player,String name,WorldPosition position){observations.remove(player);observations.put(player,new Observation(name,position));
        while(observations.size()>capacity)observations.remove(observations.keySet().iterator().next());}
    List<PlayerTrackingCandidate> candidates(MatchSnapshot match,Set<UUID> online){
        List<PlayerTrackingCandidate> result=new ArrayList<>();observations.forEach((id,value)->{
            var participant=match.participants().get(id);if(participant!=null&&participant.eliminated())return;
            result.add(new PlayerTrackingCandidate(id,value.name,online.contains(id),Optional.ofNullable(participant).map(ParticipantSnapshot::role),value.position));
        });return List.copyOf(result);
    }
    void clear(){observations.clear();}
}
