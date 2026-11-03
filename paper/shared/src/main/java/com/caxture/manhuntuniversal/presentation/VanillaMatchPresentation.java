package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.GameWinner;
import com.caxture.manhuntuniversal.game.Participant;
import com.caxture.manhuntuniversal.game.Role;
import com.caxture.manhuntuniversal.game.TerminationReason;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class VanillaMatchPresentation implements MatchPresentation {
    @Override public void showHunterStartCountdown(Player player,long seconds){language.actionbar(player,"core:hunter-start.countdown",language.component(player,"hunter-start.countdown",Map.of("seconds",Long.toString(seconds))));}
    @Override public void clearHunterStartCountdown(Player player){language.actionbar(player,"core:hunter-start.countdown",net.kyori.adventure.text.Component.empty());}
    private static final Title.Times SHORT_TITLE = Title.Times.times(
            Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200));
    private static final Title.Times RESULT_TITLE = Title.Times.times(
            Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700));

    private final Server server;
    private final LanguageService language;
    private final BooleanSupplier hideRoles;

    public VanillaMatchPresentation(Server server, LanguageService language, BooleanSupplier hideRoles) {
        this.server = server;
        this.language = language;
        this.hideRoles = hideRoles;
    }

    @Override
    public void showCountdown(GameSession session, int seconds) {
        forEachOnlineParticipant(session.participants(), player -> {
            language.title(player, "core:countdown.title", Title.title(
                    language.component(player, "game.start.countdown-title", Map.of("seconds", Integer.toString(seconds))),
                    net.kyori.adventure.text.Component.empty(), SHORT_TITLE));
            PresentationSoundCue.countdown().play(player, language, "core:countdown.sound");
        });
    }

    @Override
    public void showStart(GameSession session) {
        showRoles(session, "game.start.title");
    }

    @Override
    public void showRolesChanged(GameSession session) {
        showRoles(session, "game.roles.changed-title");
    }

    private void showRoles(GameSession session, String titleKey) {
        if (!hideRoles.getAsBoolean()) {
            broadcast("game.start.runners", Map.of(
                    "players", MatchTextFormatter.names(session.participants(), session.runnerIds())));
        }
        forEachOnlineParticipant(session.participants(), player -> {
            Role role = session.roleOf(player.getUniqueId()).orElseThrow();
            language.title(player, "core:role.title", Title.title(
                    language.component(player, titleKey, Map.of()),
                    language.component(player, "game.start.role-subtitle", Map.of(
                            "role", language.text(player, roleKey(role)))), SHORT_TITLE));
            PresentationSoundCue.roleReveal().play(player, language, "core:role.sound");
        });
    }

    @Override
    public void showStatus(CommandSender recipient, GameSession session, int countdownSeconds, Instant now) {
        if (session.startedAt().isEmpty()) {
            language.send(recipient, "game.status.countdown", Map.of(
                    "count", Integer.toString(session.participants().size()),
                    "seconds", Integer.toString(Math.max(0, countdownSeconds))));
            return;
        }
        language.send(recipient, "game.status.running", Map.of(
                "count", Integer.toString(session.participants().size()),
                "elapsed", MatchTextFormatter.elapsed(session.startedAt().orElseThrow(), now)));
        if (!hideRoles.getAsBoolean()) {
            language.send(recipient, "game.status.runners", Map.of(
                    "players", MatchTextFormatter.names(session.participants(), session.runnerIds())));
            language.send(recipient, "game.status.hunters", Map.of(
                    "players", MatchTextFormatter.names(session.participants(), session.hunterIds())));
        }
        language.send(recipient, "game.status.surviving-runners", Map.of(
                "alive", Integer.toString(session.livingRunnerIds().size()),
                "total", Integer.toString(session.runnerIds().size())));
    }

    @Override
    public void showIdleStatus(CommandSender recipient) {
        language.send(recipient, "game.status.idle");
    }

    @Override
    public void showResult(GameResult result) {
        var match = language.currentMatch();
        java.util.Map<UUID, com.caxture.manhuntuniversal.api.ParticipantSnapshot> participants = new java.util.LinkedHashMap<>();
        result.participants().forEach(player -> participants.put(player.id(), new com.caxture.manhuntuniversal.api.ParticipantSnapshot(
                player.id(), player.name(), result.runners().contains(player.id()) ? com.caxture.manhuntuniversal.api.TeamRole.RUNNER
                : com.caxture.manhuntuniversal.api.TeamRole.HUNTER, false)));
        var snapshot = new com.caxture.manhuntuniversal.api.ResultSnapshot(result.sessionId(),
                com.caxture.manhuntuniversal.api.MatchWinner.valueOf(result.winner().name()),
                result.customReason().isBlank() ? result.reason().name() : result.customReason(),
                match.flatMap(com.caxture.manhuntuniversal.api.MatchSnapshot::startedAt), result.settledAt(),
                match.map(com.caxture.manhuntuniversal.api.MatchSnapshot::participants).orElse(participants), result.killer());
        language.withResult(snapshot, () -> showResultContent(result));
    }
    private void showResultContent(GameResult result) {
        forEachOnlineParticipant(result.participants(), player -> {
            String titleKey = result.winner() == GameWinner.RUNNERS
                    ? "game.result.runners-title" : "game.result.hunters-title";
            language.title(player, "core:result.title", Title.title(
                    language.component(player, titleKey, Map.of()),
                    net.kyori.adventure.text.Component.empty(), RESULT_TITLE));
            language.sound(player, "core:result.sound", "ui.toast.challenge_complete", 1.0F, 1.0F);
        });

        forEachRecipient(recipient -> {
            ResultReportComposer.Report report = ResultReportComposer.compose(
                    result,
                    (key, placeholders) -> language.text(recipient, key, placeholders),
                    id -> participantName(result.participants(), id), hideRoles.getAsBoolean());
            language.send(recipient, report.languageKey(), report.placeholders());
        });
    }

    @Override
    public void showStopped(TerminationReason reason) {
        broadcast(reason == TerminationReason.PLUGIN_DISABLED
                ? "game.stop.plugin-disabled" : "game.stop.done", Map.of());
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        forEachRecipient(recipient -> language.send(recipient, key, placeholders));
    }

    private void forEachRecipient(java.util.function.Consumer<CommandSender> action) {
        server.getOnlinePlayers().forEach(action);
        action.accept(server.getConsoleSender());
    }

    private void forEachOnlineParticipant(List<Participant> participants, java.util.function.Consumer<Player> action) {
        for (Participant participant : participants) {
            Player player = server.getPlayer(participant.id());
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        }
    }

    private String participantName(List<Participant> participants, UUID id) {
        return participants.stream()
                .filter(participant -> participant.id().equals(id))
                .map(Participant::name)
                .findFirst()
                .orElseGet(() -> {
                    String name = server.getOfflinePlayer(id).getName();
                    return name == null ? id.toString() : name;
                });
    }

    private static String roleKey(Role role) {
        return role == Role.RUNNER ? "identity.runner" : "identity.hunter";
    }

}
