package com.caxture.manhuntuniversal.persistence;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeCheckpointGateTest {
    @Test void stoppingOrReplacingThePendingSessionCanNeverLeaveItsOldCheckpointActive() {
        var gate=new RuntimeCheckpointGate();var old=Optional.of(UUID.randomUUID());gate.begin(old);
        assertFalse(gate.mayWrite(old));
        assertTrue(gate.mayWrite(Optional.empty())); // stop during another plugin's onEnable
        assertTrue(gate.mayWrite(Optional.of(UUID.randomUUID()))); // stop then start before the resume tick
        gate.completed();assertTrue(gate.mayWrite(old));
    }
    @Test void anIdleDebtRestoreDoesNotBlockSavingANewMatch() {
        var gate=new RuntimeCheckpointGate();gate.begin(Optional.empty());assertTrue(gate.mayWrite(Optional.empty()));assertTrue(gate.mayWrite(Optional.of(UUID.randomUUID())));
    }
}
