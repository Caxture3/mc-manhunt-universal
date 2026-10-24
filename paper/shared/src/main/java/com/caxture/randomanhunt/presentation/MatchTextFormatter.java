package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.Participant;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MatchTextFormatter {
    private MatchTextFormatter() {
    }

    public static String names(List<Participant> participants, List<UUID> ids) {
        Map<UUID, String> names = participants.stream()
                .collect(Collectors.toMap(Participant::id, Participant::name));
        return ids.stream().map(id -> names.getOrDefault(id, id.toString())).collect(Collectors.joining(", "));
    }

    public static String elapsed(Instant start, Instant end) {
        long seconds = Math.max(0, Duration.between(start, end).toSeconds());
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, remainder);
        }
        return "%02d:%02d".formatted(minutes, remainder);
    }

}
