package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.Participant;
import com.caxture.manhuntuniversal.game.Role;
import com.caxture.manhuntuniversal.game.TeamPreference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IdentityDisplayService {
    private final Server server;
    private final IdentityDisplayState state = new IdentityDisplayState();
    private final Map<UUID, String> originalTeams = new LinkedHashMap<>();
    private final Map<UUID, Component> pendingListNameRestores = new LinkedHashMap<>();
    private final Map<UUID, LobbyOriginal> lobbyOriginals = new LinkedHashMap<>();
    private Team runnerTeam;
    private Team hunterTeam;
    private Team lobbyRunnerTeam;
    private Team lobbyHunterTeam;
    private boolean hidden;
    private GameSession displayedSession;

    public IdentityDisplayService(Server server) {
        this.server = server;
    }

    public void begin(GameSession session) {
        // Formal snapshots must describe the display before lobby selection, never its preview.
        clearLobbyPreviews();
        displayedSession = session;
        if (!hidden) beginVisible(session);
    }

    private void beginVisible(GameSession session) {
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
        if (hidden) return;
        Role role = session.roleOf(player.getUniqueId()).orElse(null);
        if (role == null) {
            return;
        }
        state.rememberOriginal(
                player.getUniqueId(), originalTeams.get(player.getUniqueId()), player.playerListName());
        applyRole(player, role);
    }

    public void clear(GameSession session) {
        // A cancelled countdown has previews, but no formal identity state yet.
        clearLobbyPreviews();
        clearFormal(session);
        if (displayedSession != null && displayedSession.id().equals(session.id())) displayedSession = null;
    }

    private void clearFormal(GameSession session) {
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

    public void updateLobbyChoice(UUID playerId, TeamPreference preference) {
        if (hidden || preference == TeamPreference.RANDOM) {
            restoreLobbyPreview(playerId);
            return;
        }
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        restorePending(player);
        Scoreboard scoreboard = server.getScoreboardManager().getMainScoreboard();
        lobbyOriginals.computeIfAbsent(playerId, ignored -> {
            Team previous = scoreboard.getEntryTeam(player.getName());
            return new LobbyOriginal(player.getName(), previous == null ? null : previous.getName(),
                    player.playerListName());
        });
        if (lobbyRunnerTeam == null) {
            lobbyRunnerTeam = replaceTeam(scoreboard, "rmh_lobby_r", NamedTextColor.RED);
            lobbyHunterTeam = replaceTeam(scoreboard, "rmh_lobby_h", NamedTextColor.AQUA);
        }
        removeFromLobbyTeams(player.getName());
        boolean runner = preference == TeamPreference.RUNNER;
        (runner ? lobbyRunnerTeam : lobbyHunterTeam).addEntry(player.getName());
        colorListName(player, runner ? Role.RUNNER : Role.HUNTER);
    }

    public void clearLobbyPreviews() {
        List.copyOf(lobbyOriginals.keySet()).forEach(this::restoreLobbyPreview);
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        if (hidden) {
            clearLobbyPreviews();
            if (displayedSession != null) clearFormal(displayedSession);
        } else if (displayedSession != null) {
            beginVisible(displayedSession);
        }
    }

    private void restoreLobbyPreview(UUID playerId) {
        LobbyOriginal original = lobbyOriginals.remove(playerId);
        if (original == null) return;
        removeFromLobbyTeams(original.name());
        if (original.teamName() != null) {
            Team previous = server.getScoreboardManager().getMainScoreboard().getTeam(original.teamName());
            if (previous != null) previous.addEntry(original.name());
        }
        Player player = server.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.playerListName(original.listName());
        } else {
            pendingListNameRestores.put(playerId, original.listName());
        }
        if (lobbyOriginals.isEmpty()) {
            unregister(lobbyRunnerTeam);
            unregister(lobbyHunterTeam);
            lobbyRunnerTeam = null;
            lobbyHunterTeam = null;
        }
    }

    private void removeFromLobbyTeams(String entry) {
        if (lobbyRunnerTeam != null) lobbyRunnerTeam.removeEntry(entry);
        if (lobbyHunterTeam != null) lobbyHunterTeam.removeEntry(entry);
    }

    private void applyRole(Player player, Role role) {
        applyEntry(player.getName(), role);
        colorListName(player, role);
    }

    private void colorListName(Player player, Role role) {
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

    private record LobbyOriginal(String name, String teamName, Component listName) {}
}
