package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.GameResult;
import com.caxture.randomanhunt.game.GameResultReason;
import com.caxture.randomanhunt.game.GameWinner;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class ResultReportComposer {
    private ResultReportComposer() {
    }

    static Report compose(GameResult result, Localizer localizer, Function<UUID, String> nameLookup) {
        return compose(result, localizer, nameLookup, false);
    }

    static Report compose(GameResult result, Localizer localizer, Function<UUID, String> nameLookup, boolean hideRoles) {
        if (hideRoles) {
            return new Report("game.result.hidden-report", Map.of(
                    "winner", localizer.text(winnerKey(result.winner()), Map.of()),
                    "reason", localizer.text(resultReasonKey(result.reason()), Map.of())));
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
                "killer-section", killerSection));
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
