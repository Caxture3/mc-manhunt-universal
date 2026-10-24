package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LobbyIdentityDisplayTest {
    private final Fixture fixture = new Fixture();
    private final IdentityDisplayService display = new IdentityDisplayService(fixture.server);

    @Test
    void switchingPreviewsAndReturningToRandomRestoresOriginalTeamAndTabName() {
        display.updateLobbyChoice(fixture.id, TeamPreference.HUNTER);
        assertEquals(NamedTextColor.AQUA, fixture.listName.color());
        assertEquals(NamedTextColor.AQUA, fixture.entryTeam().color());
        display.updateLobbyChoice(fixture.id, TeamPreference.RUNNER);
        assertEquals(NamedTextColor.RED, fixture.listName.color());
        assertEquals(NamedTextColor.RED, fixture.entryTeam().color());
        display.updateLobbyChoice(fixture.id, TeamPreference.RANDOM);
        fixture.assertOriginal();
    }

    @Test
    void beginningGameRestoresEveryPreviewBeforeCapturingFormalOriginals() {
        display.updateLobbyChoice(fixture.id, TeamPreference.HUNTER);
        GameSession session = countdown();
        display.begin(session);
        assertEquals(NamedTextColor.RED, fixture.listName.color());
        assertEquals(NamedTextColor.RED, fixture.entryTeam().color());
        display.clear(session);
        fixture.assertOriginal();
        assertEquals(Set.of("original"), fixture.teams.keySet());
    }

    @Test
    void removalWhileOfflineRestoresTeamImmediatelyAndTabOnRejoin() {
        display.updateLobbyChoice(fixture.id, TeamPreference.RUNNER);
        fixture.online = false;
        display.updateLobbyChoice(fixture.id, TeamPreference.RANDOM);
        assertEquals("original", fixture.entryTeam().getName());
        fixture.online = true;
        display.restorePending(fixture.player);
        fixture.assertOriginal();
    }

    @Test
    void cancellingCountdownAndDisablingIdleLobbyRestorePreviews() {
        display.updateLobbyChoice(fixture.id, TeamPreference.RUNNER);
        display.clear(countdown());
        fixture.assertOriginal();
        display.updateLobbyChoice(fixture.id, TeamPreference.HUNTER);
        display.clearLobbyPreviews();
        fixture.assertOriginal();
        assertEquals(Set.of("original"), fixture.teams.keySet());
        display.clearLobbyPreviews();
        fixture.assertOriginal();
    }

    private GameSession countdown() {
        GameManager manager = new GameManager(new RoleAssigner(new Random(0)));
        manager.teamSelections().choose(fixture.id, TeamPreference.RUNNER, 1);
        return manager.start(List.of(new Participant(fixture.id, "Player"),
                        new Participant(new UUID(0, 2), "Other")),
                new MatchSettings(1, false, new TrackingSettingsSnapshot(512, 30, 4096)), Instant.EPOCH)
                .session().orElseThrow();
    }

    /** Stateful boundary fake: entries can belong to only one scoreboard team, as in Bukkit. */
    private static final class Fixture {
        final UUID id = new UUID(0, 1);
        final Component originalName = Component.text("Original", NamedTextColor.GOLD);
        Component listName = originalName;
        boolean online = true;
        final Map<String, Team> teams = new LinkedHashMap<>();
        final Map<String, Set<String>> entries = new LinkedHashMap<>();
        final Map<String, NamedTextColor> colors = new LinkedHashMap<>();
        final Scoreboard scoreboard = proxy(Scoreboard.class, (object, method, args) -> switch (method.getName()) {
            case "getTeam" -> teams.get(args[0]);
            case "getEntryTeam" -> teams.values().stream()
                    .filter(team -> entries.get(team.getName()).contains(args[0])).findFirst().orElse(null);
            case "registerNewTeam" -> newTeam((String) args[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final Player player = proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> "Player";
            case "isOnline" -> online;
            case "playerListName" -> {
                if (args != null && args.length == 1) { listName = (Component) args[0]; yield null; }
                yield listName;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final ScoreboardManager manager = proxy(ScoreboardManager.class, (object, method, args) -> scoreboard);
        final Server server = proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getScoreboardManager" -> manager;
            case "getPlayer" -> id.equals(args[0]) ? player : null;
            default -> throw new UnsupportedOperationException(method.getName());
        });

        Fixture() { newTeam("original").addEntry("Player"); }

        Team newTeam(String name) {
            entries.put(name, new LinkedHashSet<>());
            Team team = proxy(Team.class, (object, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "addEntry" -> {
                    entries.values().forEach(values -> values.remove(args[0]));
                    yield entries.get(name).add((String) args[0]);
                }
                case "removeEntry" -> entries.get(name).remove(args[0]);
                case "color" -> {
                    if (args != null && args.length == 1) { colors.put(name, (NamedTextColor) args[0]); yield null; }
                    yield colors.get(name);
                }
                case "unregister" -> { teams.remove(name); entries.remove(name); colors.remove(name); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
            teams.put(name, team);
            return team;
        }

        Team entryTeam() { return scoreboard.getEntryTeam("Player"); }
        void assertOriginal() {
            assertEquals(originalName, listName);
            assertEquals("original", entryTeam().getName());
        }

        static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
