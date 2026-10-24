package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.GameSession;
import com.caxture.randomanhunt.game.Participant;
import com.caxture.randomanhunt.game.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class IdentityDisplayService {
    private final Server server;
    private final IdentityDisplayState state = new IdentityDisplayState();
    private final Map<UUID, String> originalTeams = new LinkedHashMap<>();
    private final Map<UUID, Component> pendingListNameRestores = new LinkedHashMap<>();
    private Team runnerTeam;
    private Team hunterTeam;

    public IdentityDisplayService(Server server) {
        this.server = server;
    }

    public void begin(GameSession session) {
        Scoreboard scoreboard = server.getScoreboardManager().getMainScoreboard();
        String suffix = session.id().toString().substring(0, 8);
        runnerTeam = replaceTeam(scoreboard, "rmh_r_" + suffix, NamedTextColor.RED);
        hunterTeam = replaceTeam(scoreboard, "rmh_h_" + suffix, NamedTextColor.AQUA);
        state.begin(session.id(), roles(session));
        originalTeams.clear();
        for (Participant participant : session.participants()) {
            Team previous = scoreboard.getEntryTeam(participant.name());
            originalTeams.put(participant.id(), previous == null ? null : previous.getName());
            Player player = server.getPlayer(participant.id());
            if (player != null && player.isOnline()) {
                applyJoiningPlayer(session, player);
            }
        }
    }

    public void applyJoiningPlayer(GameSession session, Player player) {
        Role role = session.roleOf(player.getUniqueId()).orElse(null);
        if (role == null) {
            return;
        }
        state.rememberOriginal(
                player.getUniqueId(), originalTeams.get(player.getUniqueId()), player.playerListName());
        applyRole(player, role);
    }

    public void clear(GameSession session) {
        Map<UUID, IdentityDisplayState.OriginalDisplay> originals = state.finish(session.id());
        Scoreboard scoreboard = server.getScoreboardManager().getMainScoreboard();
        for (Participant participant : session.participants()) {
            removeFromPluginTeams(participant.name());
            String originalTeam = originalTeams.get(participant.id());
            if (originalTeam != null) {
                Team previous = scoreboard.getTeam(originalTeam);
                if (previous != null) {
                    previous.addEntry(participant.name());
                }
            }
            IdentityDisplayState.OriginalDisplay original = originals.get(participant.id());
            if (original == null) {
                continue;
            }
            Player player = server.getPlayer(participant.id());
            if (player != null && player.isOnline()) {
                player.playerListName(original.listName());
            } else {
                pendingListNameRestores.put(participant.id(), original.listName());
            }
        }
        unregister(runnerTeam);
        unregister(hunterTeam);
        runnerTeam = null;
        hunterTeam = null;
        originalTeams.clear();
    }

    public void restorePending(Player player) {
        if (pendingListNameRestores.containsKey(player.getUniqueId())) {
            player.playerListName(pendingListNameRestores.remove(player.getUniqueId()));
        }
    }

    private void applyRole(Player player, Role role) {
        applyEntry(player.getName(), role);
        NamedTextColor color = role == Role.RUNNER ? NamedTextColor.RED : NamedTextColor.AQUA;
        Component current = player.playerListName();
        player.playerListName((current == null ? Component.text(player.getName()) : current).color(color));
    }

    private void applyEntry(String entry, Role role) {
        removeFromPluginTeams(entry);
        Team target = role == Role.RUNNER ? runnerTeam : hunterTeam;
        if (target != null) {
            target.addEntry(entry);
        }
    }

    private void removeFromPluginTeams(String entry) {
        if (runnerTeam != null) {
            runnerTeam.removeEntry(entry);
        }
        if (hunterTeam != null) {
            hunterTeam.removeEntry(entry);
        }
    }

    private static Team replaceTeam(Scoreboard scoreboard, String name, NamedTextColor color) {
        Team existing = scoreboard.getTeam(name);
        if (existing != null) {
            existing.unregister();
        }
        Team team = scoreboard.registerNewTeam(name);
        team.color(color);
        return team;
    }

    private static Map<UUID, Role> roles(GameSession session) {
        Map<UUID, Role> roles = new LinkedHashMap<>();
        session.participants().forEach(participant ->
                roles.put(participant.id(), session.roleOf(participant.id()).orElseThrow()));
        return roles;
    }

    private static void unregister(Team team) {
        if (team != null) {
            team.unregister();
        }
    }
}
