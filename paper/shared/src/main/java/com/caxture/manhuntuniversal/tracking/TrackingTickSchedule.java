package com.caxture.manhuntuniversal.tracking;

/** Separates display-only cadence from the once-per-second inventory/UI/search maintenance. */
final class TrackingTickSchedule {
    private int maintenanceTicks;
    private long displayTicks;

    Step advance(long intervalTicks) {
        boolean maintenance = ++maintenanceTicks >= 20;
        if (maintenance) maintenanceTicks = 0;
        boolean display = ++displayTicks >= intervalTicks;
        if (display) displayTicks = 0;
        return new Step(maintenance, display, maintenance && !display && intervalTicks > 20);
    }
    void displayed() { displayTicks = 0; }
    void clear() { maintenanceTicks = 0; displayTicks = 0; }
    record Step(boolean maintenance, boolean display, boolean keepAlive) {}
}
