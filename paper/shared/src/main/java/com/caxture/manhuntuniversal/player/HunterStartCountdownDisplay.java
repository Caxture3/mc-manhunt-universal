package com.caxture.manhuntuniversal.player;

/** Follow remaining wall-clock seconds rather than a separate 20-tick render phase. */
final class HunterStartCountdownDisplay {
    private long lastSeconds = -1;

    boolean update(long remainingSeconds) {
        if (remainingSeconds == lastSeconds) return false;
        lastSeconds = remainingSeconds;
        return true;
    }

    void reset() {
        lastSeconds = -1;
    }
}
