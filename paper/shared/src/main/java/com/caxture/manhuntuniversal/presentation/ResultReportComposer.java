package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameResultReason;
import com.caxture.manhuntuniversal.game.GameWinner;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;

final class ResultReportComposer {
    private ResultReportComposer() {
    }

    static Report compose(GameResult result, Localizer localizer, Function<UUID, String> nameLookup) {
        return compose(result, localizer, nameLookup, false);
    }

    static Report compose(GameResult result, Localizer localizer, Function<UUID, String> nameLookup, boolean hideRoles) {
        String reason = reasonText(result, localizer);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("winner", localizer.text(winnerKey(result.winner()), Map.of()));
        if (!reason.isBlank()) {
            placeholders.put("reason", reason);
        }
        if (!hideRoles) {
            String killerSection = result.killer()
                    .map(killer -> localizer.text("game.result.killer-section", Map.of("player", nameLookup.apply(killer))))
                    .orElse("");
            placeholders.put("runners", MatchTextFormatter.names(result.participants(), result.runners()));
            placeholders.put("hunters", MatchTextFormatter.names(result.participants(), result.hunters()));
            placeholders.put("killer-section", killerSection);
        }
        String template = hideRoles ? "game.result.hidden-report" : "game.result.full-report";
        return new Report(reason.isBlank() ? template + "-no-reason" : template, placeholders);
    }

    private static String winnerKey(GameWinner winner) {
        return winner == GameWinner.RUNNERS ? "winner.runners" : "winner.hunters";
    }

    private static String resultReasonKey(GameResultReason reason) {
        return switch (reason) {
            case RUNNER_DIED -> "reason.runner-died";
            case ENDER_DRAGON_DEFEATED -> "reason.dragon-defeated";
            case ENDER_DRAGON_ABSENT -> "reason.dragon-absent";
            case CUSTOM -> "reason.extension-finished";
        };
    }

    private static String reasonText(GameResult result, Localizer localizer) {
        return result.reason() == GameResultReason.CUSTOM
                ? result.customReason() : localizer.text(resultReasonKey(result.reason()), Map.of());
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
