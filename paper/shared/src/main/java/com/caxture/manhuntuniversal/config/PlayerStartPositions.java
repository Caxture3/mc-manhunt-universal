package com.caxture.manhuntuniversal.config;
import com.caxture.manhuntuniversal.api.LocalizedText;
import com.caxture.manhuntuniversal.api.config.*;
import java.util.*;
public record PlayerStartPositions(Team hunter,Team runner) {
    public static final String KEY="player-start-pos";
    public static final List<String> MODES=List.of("world-spawn","player-spawn","custom");
    public PlayerStartPositions{Objects.requireNonNull(hunter);Objects.requireNonNull(runner);}
    public record Team(String mode,List<StartPosition> custom){
        public Team {if(!MODES.contains(mode))throw new IllegalArgumentException("Unknown start position mode");custom=List.copyOf(custom);
            Set<String> seen=new HashSet<>();for(var point:custom)if(!seen.add(point.worldId()+":"+point.x()+":"+point.y()+":"+point.z()))throw new IllegalArgumentException("Duplicate start position");}
        public Team withMode(String value){return new Team(value,custom);}
        Map<String,Object> toMap(){return Map.of("mode",mode,"custom",custom.stream().map(StartPosition::toMap).toList());}
    }
    public static PlayerStartPositions defaults(){return new PlayerStartPositions(new Team("world-spawn",List.of()),new Team("world-spawn",List.of()));}
    public Team team(String role){return switch(role){case "hunter"->hunter;case "runner"->runner;default->throw new IllegalArgumentException("Unknown role");};}
    public Map<String,Object> toMap(){Map<String,Object> result=new LinkedHashMap<>();result.put("hunter",hunter.toMap());result.put("runner",runner.toMap());return Collections.unmodifiableMap(result);}
    public static PlayerStartPositions fromMap(Map<?,?> value){return new PlayerStartPositions(readTeam(value.get("hunter")),readTeam(value.get("runner")));}
    private static Team readTeam(Object raw){if(raw==null)return new Team("world-spawn",List.of());if(!(raw instanceof Map<?,?> map))throw new IllegalArgumentException("Expected team map");
        Object entries=map.containsKey("custom")?map.get("custom"):List.of();if(!(entries instanceof List<?> list))throw new IllegalArgumentException("Expected position list");
        List<StartPosition> points=new ArrayList<>();for(Object item:list){if(!(item instanceof Map<?,?> point))throw new IllegalArgumentException("Expected position map");points.add(StartPosition.fromMap(point));}
        return new Team(map.containsKey("mode")?(String)map.get("mode"):"world-spawn",points);
    }
    public static ConfigType<Map<String,Object>> type(){
        ConfigType<List<Map<String,Object>>> list=new ConfigType<>(){
            public List<Map<String,Object>> parse(String raw){throw new IllegalArgumentException("Use add-custom/remove-custom");}
            public List<Map<String,Object>> validate(Object raw){if(!(raw instanceof List<?> values))throw new IllegalArgumentException("Expected position list");
                List<StartPosition> points=new ArrayList<>();for(Object item:values){if(!(item instanceof Map<?,?> map))throw new IllegalArgumentException("Expected position map");points.add(StartPosition.fromMap(map));}
                new Team("custom",points);return points.stream().map(StartPosition::toMap).toList();}
        };
        Map<String,ConfigField<?>> fields=new LinkedHashMap<>();fields.put("mode",new ConfigField<>(LocalizedText.of("Mode"),ConfigTypes.enumeration(MODES),""));fields.put("custom",new ConfigField<>(LocalizedText.of("Custom positions"),list,""));
        var team=ConfigTypes.group(fields);Map<String,ConfigField<?>> roles=new LinkedHashMap<>();roles.put("hunter",new ConfigField<>(LocalizedText.bilingual("Hunters","猎人"),team,""));roles.put("runner",new ConfigField<>(LocalizedText.bilingual("Speedrunners","速通者"),team,""));return ConfigTypes.group(roles);
    }
}
