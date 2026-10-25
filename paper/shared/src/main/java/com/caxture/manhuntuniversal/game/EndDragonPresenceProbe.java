package com.caxture.manhuntuniversal.game;

public final class EndDragonPresenceProbe {
    public static final int STABILIZATION_TICKS = 100;

    private int elapsedTicks;
    private Outcome outcome = Outcome.WAITING;

    public Outcome observe(boolean livingDragon, boolean respawnInProgress) {
        if (outcome != Outcome.WAITING) {
            return outcome;
        }
        if (livingDragon) {
            outcome = Outcome.PRESENT;
            return outcome;
        }
        elapsedTicks++;
        if (elapsedTicks >= STABILIZATION_TICKS && !respawnInProgress) {
            outcome = Outcome.ABSENT;
        }
        return outcome;
    }

    public enum Outcome {
        WAITING,
        PRESENT,
        ABSENT
    }
}
