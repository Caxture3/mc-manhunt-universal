package com.caxture.randomanhunt.game;

public final class CountdownSequence {
    private int remaining;
    private boolean cancelled;
    private boolean started;

    public CountdownSequence(int seconds) {
        if (seconds < 1 || seconds > 60) {
            throw new IllegalArgumentException("Countdown must be in 1..60");
        }
        this.remaining = seconds;
    }

    public CountdownStep next() {
        if (cancelled) {
            return CountdownStep.Cancelled.INSTANCE;
        }
        if (remaining > 0) {
            return new CountdownStep.Number(remaining--);
        }
        started = true;
        return CountdownStep.Start.INSTANCE;
    }

    public void cancel() {
        if (!started) {
            cancelled = true;
        }
    }

    public int remainingSeconds() {
        return remaining;
    }
}
