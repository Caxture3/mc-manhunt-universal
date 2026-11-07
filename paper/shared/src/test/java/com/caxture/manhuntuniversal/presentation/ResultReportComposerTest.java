package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.i18n.LanguageTemplateResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResultReportComposerTest {
    @Test void reasonlessReportsResolveBundledTemplatesWithLegacyExternalAttributionPresent() throws Exception {
        var result = new GameResult(UUID.randomUUID(), GameWinner.RUNNERS, GameResultReason.CUSTOM,
                Instant.EPOCH, List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var english = bundled("en_us");
        for (String locale : List.of("en_us", "zh_cn")) {
            var localized = bundled(locale);
            var external = new YamlConfiguration();
            external.set("reason.extension-finished", "OUTDATED_EXTENSION_NOTICE");
            for (boolean hidden : List.of(false, true)) {
                var report = ResultReportComposer.compose(result,
                        (key, values) -> LanguageTemplateResolver.resolve(external, localized, english, key, ignored -> {}),
                        id -> "", hidden);
                assertTrue(localized.isString(report.languageKey()), report.languageKey());
                String text = LanguageTemplateResolver.resolve(external, localized, english, report.languageKey(), ignored -> {});
                for (var value : report.placeholders().entrySet()) text = text.replace("{" + value.getKey() + "}", value.getValue());
                assertFalse(text.contains("OUTDATED_EXTENSION_NOTICE"));
                assertFalse(text.contains("{reason}"));
                assertFalse(text.contains("{winner}"));
            }
        }
    }

    private static YamlConfiguration bundled(String locale) throws Exception {
        try (var input = ResultReportComposerTest.class.getResourceAsStream("/lang/" + locale + ".yml")) {
            assertNotNull(input);
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return yaml;
        }
    }
    @Test void blankCustomReasonsOmitAttributionInVisibleAndHiddenReports() {
        UUID runner = new UUID(0, 1), hunter = new UUID(0, 2);
        var result = new GameResult(UUID.randomUUID(), GameWinner.RUNNERS, GameResultReason.CUSTOM,
                Instant.EPOCH, List.of(new Participant(runner, "Runner"), new Participant(hunter, "Hunter")),
                List.of(runner), List.of(hunter), List.of(), Optional.empty(), Optional.empty(), "  ");
        for (boolean hidden : List.of(false, true)) {
            var report = ResultReportComposer.compose(result, (key, values) -> {
                assertFalse(key.startsWith("reason."), "A missing custom reason has no fallback attribution");
                return key;
            }, id -> { fail("No killer name should be requested"); return ""; }, hidden);
            assertEquals(hidden ? "game.result.hidden-report-no-reason" : "game.result.full-report-no-reason", report.languageKey());
            assertFalse(report.placeholders().containsKey("reason"));
            assertEquals(!hidden, report.placeholders().containsKey("runners"));
        }
    }

    @Test void suppliedCustomReasonRemainsPlayerFacingAndKeepsKillerInformation() {
        UUID runner = new UUID(0, 1), hunter = new UUID(0, 2);
        var result = new GameResult(UUID.randomUUID(), GameWinner.HUNTERS, GameResultReason.CUSTOM,
                Instant.EPOCH, List.of(new Participant(runner, "Runner"), new Participant(hunter, "Hunter")),
                List.of(runner), List.of(hunter), List.of(), Optional.empty(), Optional.of(hunter), "目标已完成");
        var report = ResultReportComposer.compose(result, (key, values) -> key, id -> "Hunter");
        assertEquals("game.result.full-report", report.languageKey());
        assertEquals("目标已完成", report.placeholders().get("reason"));
        assertEquals("game.result.killer-section", report.placeholders().get("killer-section"));
    }
    @Test
    void hiddenResultNeverRequestsNamesOrEmitsTeamAndKillerPlaceholders() {
        UUID runner = new UUID(0, 1), hunter = new UUID(0, 2);
        GameResult result = new GameResult(UUID.randomUUID(), GameWinner.HUNTERS,
                GameResultReason.RUNNER_DIED, Instant.EPOCH,
                List.of(new Participant(runner, "SecretRunner"), new Participant(hunter, "SecretHunter")),
                List.of(runner), List.of(hunter), List.of(), Optional.of(runner), Optional.of(hunter));
        var report = ResultReportComposer.compose(result, (key, values) -> {
            assertFalse(key.contains("killer"));
            return key;
        }, id -> { fail("Hidden reports must never resolve participant names"); return ""; }, true);
        assertEquals("game.result.hidden-report", report.languageKey());
        assertEquals(java.util.Set.of("winner", "reason"), report.placeholders().keySet());
        assertFalse(report.placeholders().toString().contains("Secret"));
    }

    @Test
    void settlementIncludesTeamsAndKillerWithoutRequestingOrRenderingHistory() {
        UUID runner = new UUID(0, 1);
        UUID hunter = new UUID(0, 2);
        Instant start = Instant.parse("2026-10-25T00:00:00Z");
        GameResult result = new GameResult(UUID.randomUUID(), GameWinner.HUNTERS,
                GameResultReason.RUNNER_DIED, start.plusSeconds(60),
                List.of(new Participant(runner, "Runner"), new Participant(hunter, "Hunter")),
                List.of(runner), List.of(hunter),
                List.of(new RunnerRound(List.of(runner), start, start.plusSeconds(60), TerminationReason.RUNNER_DIED)),
                Optional.of(runner), Optional.of(hunter));
        var report = ResultReportComposer.compose(result, (key, values) -> {
            assertFalse(key.contains("history"), "History must not be generated for settlement");
            return key.equals("game.result.killer-section") ? "killer=" + values.get("player") : key;
        }, id -> "Hunter");
        assertEquals("Runner", report.placeholders().get("runners"));
        assertEquals("Hunter", report.placeholders().get("hunters"));
        assertEquals("killer=Hunter", report.placeholders().get("killer-section"));
        assertFalse(report.placeholders().containsKey("round-history"));
    }
}
