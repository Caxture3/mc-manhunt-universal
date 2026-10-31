package com.caxture.manhuntuniversal.persistence;

import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.player.*;
import com.caxture.manhuntuniversal.tracking.*;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Explicit schema: YAML never instantiates arbitrary plugin/platform classes. */
final class RuntimeCheckpointCodec {
    private RuntimeCheckpointCodec() { }
    static YamlConfiguration write(RuntimeCheckpoint saved) {
        YamlConfiguration yaml=new YamlConfiguration();
        yaml.set("format",1);yaml.set("saved-at",saved.savedAt().toString());yaml.set("countdown",saved.countdownSeconds());
        saved.game().ifPresent(game->yaml.set("game",game(game)));
        yaml.set("pending-resets",ids(saved.pendingResets()));
        Map<String,Object> blindness=new LinkedHashMap<>();
        saved.blindness().forEach((id,effect)->{
            Map<String,Object> value=new LinkedHashMap<>();value.put("present",effect.isPresent());
            effect.ifPresent(v->{value.put("duration",v.duration());value.put("amplifier",v.amplifier());value.put("ambient",v.ambient());value.put("particles",v.particles());value.put("icon",v.icon());});
            blindness.put(id.toString(),value);
        });
        yaml.set("blindness",blindness);
        Map<String,Object> modes=new LinkedHashMap<>();saved.spectatorModes().forEach((id,value)->modes.put(id.toString(),Map.of("session",value.sessionId().toString(),"mode",value.gameMode().name())));
        yaml.set("spectator-modes",modes);yaml.set("list-restores",uuidMap(saved.listRestores()));yaml.set("tracking-selections",uuidMap(saved.trackingSelections()));yaml.set("frozen-config",saved.frozenConfig());
        Map<String,Object> positions=new LinkedHashMap<>();saved.positions().forEach((id,values)->positions.put(id.toString(),values.stream().map(value->Map.of("world",value.worldId().toString(),"kind",value.worldKind().name(),"x",value.x(),"y",value.y(),"z",value.z())).toList()));yaml.set("positions",positions);
        return yaml;
    }
    static RuntimeCheckpoint read(YamlConfiguration yaml) {
        if(integer(yaml.get("format"))!=1)throw new IllegalArgumentException("Unsupported checkpoint format");
        Map<UUID,Optional<BlindnessSnapshot>> blindness=new LinkedHashMap<>();
        map(yaml.get("blindness")).forEach((id,raw)->{
            Map<String,Object> value=map(raw);Optional<BlindnessSnapshot> effect=Optional.empty();
            if(bool(value.get("present")))effect=Optional.of(new BlindnessSnapshot(integer(value.get("duration")),integer(value.get("amplifier")),bool(value.get("ambient")),bool(value.get("particles")),bool(value.get("icon"))));
            blindness.put(UUID.fromString(id),effect);
        });
        Map<UUID,RunnerSpectatorService.OriginalMode> modes=new LinkedHashMap<>();
        map(yaml.get("spectator-modes")).forEach((id,raw)->{var value=map(raw);modes.put(UUID.fromString(id),new RunnerSpectatorService.OriginalMode(uuid(value.get("session")),GameMode.valueOf(text(value.get("mode")))));});
        Map<UUID,List<TrackingPosition>> positions=new LinkedHashMap<>();map(yaml.get("positions")).forEach((id,raw)->positions.put(UUID.fromString(id),list(raw,entry->{var value=map(entry);return new TrackingPosition(uuid(value.get("world")),TrackingWorldKind.valueOf(text(value.get("kind"))),decimal(value.get("x")),decimal(value.get("y")),decimal(value.get("z")));})));
        return new RuntimeCheckpoint(Instant.parse(text(yaml.get("saved-at"))),Optional.ofNullable(yaml.get("game")).map(RuntimeCheckpointCodec::readGame),integer(yaml.get("countdown")),
                new LinkedHashSet<>(readIds(yaml.get("pending-resets"))),blindness,modes,readUuidMap(yaml.get("list-restores")),readUuidMap(yaml.get("tracking-selections")),map(yaml.get("frozen-config")),positions);
    }
    private static Map<String,Object> game(SessionSnapshot game) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",game.id().toString());out.put("created-at",game.createdAt().toString());out.put("state",game.state().name());out.put("revision",game.revision());
        out.put("participants",game.participants().stream().map(value->Map.of("id",value.id().toString(),"name",value.name())).toList());out.put("runners",ids(game.runners()));out.put("eliminated",ids(game.eliminated()));
        game.startedAt().ifPresent(value->out.put("started-at",value.toString()));game.roundStartedAt().ifPresent(value->out.put("round-started-at",value.toString()));game.targetEndWorldId().ifPresent(value->out.put("target-end",value.toString()));
        var settings=game.settings();var tracking=settings.trackingSettings();
        out.put("settings",Map.of("runner-count",settings.runnerCount(),"reset",settings.resetPlayersAtStart(),"retarget-distance",tracking.retargetDistanceBlocks(),"retarget-cooldown",tracking.retargetCooldownSeconds(),"search-radius",tracking.searchRadiusBlocks()));
        out.put("rounds",game.rounds().stream().map(round->Map.of("runners",ids(round.runners()),"start",round.startedAt().toString(),"end",round.endedAt().toString(),"reason",round.reason().name())).toList());
        return out;
    }
    private static SessionSnapshot readGame(Object raw) {
        Map<String,Object> value=map(raw),settings=map(value.get("settings"));
        List<Participant> participants=list(value.get("participants"),entry->{var player=map(entry);return new Participant(uuid(player.get("id")),text(player.get("name")));});
        List<RunnerRound> rounds=list(value.get("rounds"),entry->{var round=map(entry);return new RunnerRound(readIds(round.get("runners")),Instant.parse(text(round.get("start"))),Instant.parse(text(round.get("end"))),TerminationReason.valueOf(text(round.get("reason"))));});
        return new SessionSnapshot(uuid(value.get("id")),Instant.parse(text(value.get("created-at"))),participants,
                new MatchSettings(integer(settings.get("runner-count")),bool(settings.get("reset")),new TrackingSettingsSnapshot(integer(settings.get("retarget-distance")),integer(settings.get("retarget-cooldown")),integer(settings.get("search-radius")))),
                readIds(value.get("runners")),new LinkedHashSet<>(readIds(value.get("eliminated"))),SessionState.valueOf(text(value.get("state"))),optional(value.get("started-at"),Instant::parse),optional(value.get("round-started-at"),Instant::parse),optional(value.get("target-end"),UUID::fromString),whole(value.get("revision")),rounds);
    }
    private static Map<String,Object> map(Object raw) {
        if(raw==null)return Map.of();if(raw instanceof ConfigurationSection section)raw=section.getValues(false);
        if(!(raw instanceof Map<?,?> values)||values.size()>10000)throw new IllegalArgumentException("Expected bounded map");
        Map<String,Object> out=new LinkedHashMap<>();
        values.forEach((key,value)->{if(!(key instanceof String text))throw new IllegalArgumentException("Expected string key");out.put(text,value instanceof ConfigurationSection?map(value):value);});return out;
    }
    private static <T> List<T> list(Object raw,Function<Object,T> reader) {
        if(!(raw instanceof List<?> values)||values.size()>10000)throw new IllegalArgumentException("Expected bounded list");return values.stream().map(reader).toList();
    }
    private static List<String> ids(Collection<UUID> ids) {return ids.stream().map(UUID::toString).toList();}
    private static List<UUID> readIds(Object raw) {return list(raw,RuntimeCheckpointCodec::uuid);}
    private static Map<String,String> uuidMap(Map<UUID,String> values) {Map<String,String> out=new LinkedHashMap<>();values.forEach((id,value)->out.put(id.toString(),value));return out;}
    private static Map<UUID,String> readUuidMap(Object raw) {Map<UUID,String> out=new LinkedHashMap<>();map(raw).forEach((id,value)->out.put(UUID.fromString(id),text(value)));return out;}
    private static <T> Optional<T> optional(Object raw,Function<String,T> parser){return raw==null?Optional.empty():Optional.of(parser.apply(text(raw)));}
    private static String text(Object value) {if(value instanceof String text)return text;throw new IllegalArgumentException("Expected text");}
    private static UUID uuid(Object raw) {return UUID.fromString(text(raw));}
    private static boolean bool(Object value) {if(value instanceof Boolean bool)return bool;throw new IllegalArgumentException("Expected boolean");}
    private static long whole(Object value) {if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return ((Number)value).longValue();throw new IllegalArgumentException("Expected integer");}
    private static int integer(Object value) {return Math.toIntExact(whole(value));}
    private static double decimal(Object value) {if(value instanceof Number number&&Double.isFinite(number.doubleValue()))return number.doubleValue();throw new IllegalArgumentException("Expected finite coordinate");}
}
