package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.GameResult;
import com.caxture.randomanhunt.game.GameResultReason;
import com.caxture.randomanhunt.game.GameWinner;
import com.caxture.randomanhunt.game.TerminationReason;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class ResultReportComposer {
    private ResultReportComposer() {
    }

    static Report compose(GameResult result, Localizer localizer, Function<UUID, String> nameLookup) {
        List<String> historyLines = new ArrayList<>();
        for (int index = 0; index < result.roundHistory().size(); index++) {
            Map<String, String> values = new LinkedHashMap<>(MatchTextFormatter.history(
                    index + 1, result.roundHistory().get(index), result.participants()));
            values.put("reason", localizer.text(
                    terminationReasonKey(result.roundHistory().get(index).reason()), Map.of()));
            historyLines.add(localizer.text("game.result.history-round-line", values));
            historyLines.add(localizer.text("game.result.history-detail-line", values));
        }

        String killerSection = result.killer()
                .map(killer -> localizer.text("game.result.killer-section", Map.of(
                        "player", nameLookup.apply(killer))))
                .orElse("");

        return new Report("game.result.full-report", Map.of(
                "winner", localizer.text(winnerKey(result.winner()), Map.of()),
                "reason", localizer.text(resultReasonKey(result.reason()), Map.of()),
                "runners", MatchTextFormatter.names(result.participants(), result.runners()),
                "hunters", MatchTextFormatter.names(result.participants(), result.hunters()),
                "killer-section", killerSection,
                "round-history", String.join("\n", historyLines)));
    }

    private static String winnerKey(GameWinner winner) {
        return winner == GameWinner.RUNNERS ? "winner.runners" : "winner.hunters";
    }

    private static String resultReasonKey(GameResultReason reason) {
        return switch (reason) {
            case RUNNER_DIED -> "reason.runner-died";
            case ENDER_DRAGON_DEFEATED -> "reason.dragon-defeated";
            case ENDER_DRAGON_ABSENT -> "reason.dragon-absent";
        };
    }

    private static String terminationReasonKey(TerminationReason reason) {
        return switch (reason) {
            case RUNNER_DIED -> "reason.runner-died";
            case ENDER_DRAGON_DEFEATED -> "reason.dragon-defeated";
            case ENDER_DRAGON_ABSENT -> "reason.dragon-absent";
            case ADMIN_STOPPED -> "reason.admin-stopped";
            case PLUGIN_DISABLED -> "reason.plugin-disabled";
        };
    }

    @FunctionalInterface
    interface Localizer {
        String text(String languageKey, Map<String, String> placeholders);
    }

    record Report(String languageKey, Map<String, String> placeholders) {
        Report {
            placeholders = Map.copyOf(placeholders);
        }
    }
}
