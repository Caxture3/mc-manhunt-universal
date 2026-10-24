package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResultReportComposerTest {
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
