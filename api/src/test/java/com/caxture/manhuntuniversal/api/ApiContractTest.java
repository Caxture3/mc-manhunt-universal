package com.caxture.manhuntuniversal.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiContractTest {
    private final UUID player = UUID.randomUUID();
    @Test void snapshotsDefensivelyCopyAndRejectMutation() {
        Map<UUID, ParticipantSnapshot> source = new LinkedHashMap<>();
        source.put(player, new ParticipantSnapshot(player, "Runner", TeamRole.RUNNER, false));
        MatchSnapshot snapshot = new MatchSnapshot(UUID.randomUUID(), 0, MatchState.COUNTDOWN,
                Instant.EPOCH, Optional.empty(), source, Optional.empty());
        source.clear();
        assertEquals(Set.of(player), snapshot.participants().keySet());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.participants().clear());
    }
    @Test void registrationOptionsDefensivelyCopyDomains() {
        Set<ControlDomain> domains = EnumSet.of(ControlDomain.ROLES);
        ExtensionOptions options = new ExtensionOptions("test", domains);
        domains.clear();
        assertEquals(Set.of(ControlDomain.ROLES), options.controls());
        assertThrows(UnsupportedOperationException.class, () -> options.controls().clear());
    }
    @Test void optionsRejectPoliciesWithoutTheirControlDomain() {
        assertThrows(IllegalArgumentException.class, () -> new ExtensionOptions("test", Set.of(),
                Optional.of(context -> EliminationDecision.DEFAULT), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionOptions("test", Set.of(),
                Optional.empty(), Optional.of(context -> VictoryDecision.defaults())));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionOptions(" ", Set.of()));
    }
    @Test void victoryDecisionsRequireReplacementOnlyForReplace() {
        Victory victory = new Victory(MatchWinner.RUNNERS, "goal");
        assertEquals(victory, VictoryDecision.replace(victory).replacement().orElseThrow());
        assertTrue(VictoryDecision.defaults().replacement().isEmpty());
        assertEquals(VictoryDecision.Action.DEFER, VictoryDecision.defer().action());
        assertThrows(IllegalArgumentException.class, () -> new VictoryDecision(VictoryDecision.Action.REPLACE, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new VictoryDecision(VictoryDecision.Action.DEFAULT, Optional.of(victory)));
    }
    @Test void mutationContextUsesMinusOneForUnspecifiedRevision() {
        ExtensionRegistration token = new ExtensionRegistration() {
            public String id() { return "test"; }
            public Set<ControlDomain> controls() { return Set.of(ControlDomain.ROLES); }
            public boolean active() { return true; }
            public void close() { }
        };
        assertEquals(-1, new MutationContext(token, player, "test").expectedRevision());
        assertThrows(IllegalArgumentException.class, () -> new MutationContext(token, player, -2, "test"));
    }
    @Test void resultSuccessfulOnlyForOk() {
        for (OperationStatus status : OperationStatus.values()) {
            assertEquals(status == OperationStatus.OK, new OperationResult(status, "result", Optional.empty()).successful());
        }
    }
}
