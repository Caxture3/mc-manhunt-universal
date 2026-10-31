package com.caxture.manhuntuniversal.presentation;
import java.util.*;
/** Published to a connection's outbound handler; contains no live Bukkit state or callbacks. */
record ObserverPacketSnapshot(Map<UUID, PlayerPatch> players) {
    ObserverPacketSnapshot { players = Map.copyOf(players); }
    record PlayerPatch(String realName, String teamName, Object displayName, boolean listed, int order) { }
    static ObserverPacketSnapshot empty() { return new ObserverPacketSnapshot(Map.of()); }
    List<String> filterTeam(String team, Collection<String> original) {
        if (players.values().stream().anyMatch(player -> player.teamName.equals(team))) return List.copyOf(original);
        Set<String> controlled = new HashSet<>(); players.values().forEach(player -> controlled.add(player.realName));
        return original.stream().filter(name -> !controlled.contains(name)).toList();
    }
}
