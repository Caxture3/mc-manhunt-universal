package com.caxture.manhuntuniversal.api.paper.event;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaperEventContractTest {
    private final UUID player = UUID.randomUUID();
    private final MatchSnapshot match = new MatchSnapshot(UUID.randomUUID(), 0, MatchState.RUNNING,
            Instant.EPOCH, Optional.of(Instant.EPOCH), Map.of(), Optional.empty());
    @Test void cancellationAndVictoryReplacementAreObservable() {
        MatchPreparingEvent preparing = new MatchPreparingEvent(match, 5);
        assertFalse(preparing.isCancelled());
        preparing.setCancelled(true);
        assertTrue(preparing.isCancelled());
        Victory original = new Victory(MatchWinner.RUNNERS, "goal");
        Victory replacement = new Victory(MatchWinner.HUNTERS, "custom");
        MatchFinishingEvent finishing = new MatchFinishingEvent(match, original);
        finishing.setVictory(replacement);
        assertEquals(replacement, finishing.getVictory());
        assertThrows(NullPointerException.class, () -> finishing.setVictory(null));
        finishing.setCancelled(true);
        assertTrue(finishing.isCancelled());
    }
    @Test void roleCandidateIsAnImmutableCopy() {
        Map<UUID, TeamRole> source = new HashMap<>();
        source.put(player, TeamRole.RUNNER);
        RolesChangingEvent event = new RolesChangingEvent(match, source, new ChangeSource("test", "reason"));
        source.clear();
        assertEquals(TeamRole.RUNNER, event.getProposed().get(player));
        assertThrows(UnsupportedOperationException.class, () -> event.getProposed().clear());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
    @Test void everyConcreteEventHasIndependentHandlerListAndIsSynchronous() throws Exception {
        Victory victory = new Victory(MatchWinner.RUNNERS, "goal");
        ChangeSource source = new ChangeSource("test", "reason");
        List<Event> events = List.of(new MatchPreparingEvent(match, 5), new MatchCountdownStartedEvent(match, 5),
                new MatchStartedEvent(match), new MatchEndedEvent(match, "stopped", Optional.empty()),
                new RolesChangingEvent(match, Map.of(player, TeamRole.RUNNER), source),
                new RolesChangedEvent(match, match, source), new ParticipantEliminatingEvent(match, player, "DEATH"),
                new ParticipantEliminatedEvent(match, player, "DEATH"), new ParticipantRevivedEvent(match, player),
                new TargetEndEnteredEvent(match, player, UUID.randomUUID()), new MatchFinishingEvent(match, victory));
        Set<Object> lists = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Event event : events) {
            assertFalse(event.isAsynchronous());
            assertTrue(lists.add(event.getHandlers()), event.getClass().getSimpleName());
            assertSame(event.getHandlers(), event.getClass().getMethod("getHandlerList").invoke(null));
        }
    }
    @Test void eliminatingEventCanBeCancelled() {
        ParticipantEliminatingEvent event = new ParticipantEliminatingEvent(match, player, "API");
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
