package com.caxture.manhuntuniversal.persistence;

import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.player.*;
import java.time.*;
import java.util.*;

/** Complete core-owned continuation state. Extension callbacks and world objects never enter this value. */
public record RuntimeCheckpoint(Instant savedAt, Optional<SessionSnapshot> game, int countdownSeconds,
                                Set<UUID> pendingResets, Map<UUID,Optional<BlindnessSnapshot>> blindness,
                                Map<UUID,RunnerSpectatorService.OriginalMode> spectatorModes,
                                Map<UUID,String> listRestores, Map<UUID,String> trackingSelections, Map<String,Object> frozenConfig,
                                Map<UUID,java.util.List<com.caxture.manhuntuniversal.tracking.TrackingPosition>> positions) {
    public RuntimeCheckpoint {
        Objects.requireNonNull(savedAt);Objects.requireNonNull(game);
        pendingResets=Set.copyOf(pendingResets);blindness=Map.copyOf(blindness);spectatorModes=Map.copyOf(spectatorModes);
        listRestores=Map.copyOf(listRestores);trackingSelections=Map.copyOf(trackingSelections);
        Map<UUID,java.util.List<com.caxture.manhuntuniversal.tracking.TrackingPosition>> copied=new LinkedHashMap<>();positions.forEach((id,values)->copied.put(id,java.util.List.copyOf(values)));positions=Map.copyOf(copied);
        @SuppressWarnings("unchecked") Map<String,Object> immutable=(Map<String,Object>)com.caxture.manhuntuniversal.api.config.ConfigTypes.immutable(frozenConfig);
        frozenConfig=immutable;
        if(game.filter(value->value.state()==SessionState.COUNTDOWN).isPresent()) {
            if(countdownSeconds<1||countdownSeconds>60)throw new IllegalArgumentException("Invalid remaining countdown");
        } else if(countdownSeconds!=0)throw new IllegalArgumentException("Countdown without countdown game");
        if(game.isPresent()&&(game.get().createdAt().isAfter(savedAt)||game.get().roundStartedAt().filter(at->at.isAfter(savedAt)).isPresent()))
            throw new IllegalArgumentException("Session timestamps exceed checkpoint");
        if(game.isEmpty()&&(!pendingResets.isEmpty()||!trackingSelections.isEmpty()||!frozenConfig.isEmpty()||!positions.isEmpty()))throw new IllegalArgumentException("Active state without game");
        if(game.isPresent()) {
            Set<UUID> roster=new HashSet<>();game.get().participants().forEach(player->roster.add(player.id()));
            if(!roster.containsAll(pendingResets)||!roster.containsAll(trackingSelections.keySet())||!roster.containsAll(positions.keySet()))throw new IllegalArgumentException("Unknown game participant");
        }
        if(pendingResets.size()>10000||blindness.size()>10000||spectatorModes.size()>10000||listRestores.size()>10000||trackingSelections.size()>10000)
            throw new IllegalArgumentException("Checkpoint exceeds player limit");
        if(listRestores.values().stream().anyMatch(value->value.length()>65536)||trackingSelections.values().stream().anyMatch(value->value.length()>128||!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
            throw new IllegalArgumentException("Invalid saved presentation");
        for(var values:positions.values())for(var position:values)if(position.worldId()==null||position.worldKind()==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z()))throw new IllegalArgumentException("Invalid tracking position");
        blindness.values().forEach(value->value.ifPresent(effect->{if(effect.duration() < -1||effect.amplifier()<0)throw new IllegalArgumentException("Invalid blindness effect");}));
    }
    public Optional<SessionSnapshot> resumedGame(Instant now) { return game.map(value->value.shift(Duration.between(savedAt,now))); }
}
