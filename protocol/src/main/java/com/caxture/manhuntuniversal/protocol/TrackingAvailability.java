package com.caxture.manhuntuniversal.protocol;

/** Remaining unlock delays: -1 is hidden, 0 is available, positive values are milliseconds. */
public record TrackingAvailability(
        long villageMillis,
        long netherFortressMillis,
        long bastionRemnantMillis
) {
    public TrackingAvailability {
        requireValid(villageMillis);
        requireValid(netherFortressMillis);
        requireValid(bastionRemnantMillis);
    }

    public long remainingMillis(TrackingSelectionTarget target) {
        return switch (target) {
            case NONE -> -1;
            case VILLAGE -> villageMillis;
            case NETHER_FORTRESS -> netherFortressMillis;
            case BASTION_REMNANT -> bastionRemnantMillis;
        };
    }

    public boolean visible(TrackingSelectionTarget target) {
        return remainingMillis(target) >= 0;
    }

    public boolean unlocked(TrackingSelectionTarget target) {
        return remainingMillis(target) == 0;
    }

    public boolean hasVisibleOptions() {
        return villageMillis >= 0 || netherFortressMillis >= 0 || bastionRemnantMillis >= 0;
    }

    public static TrackingAvailability allUnlocked() {
        return new TrackingAvailability(0, 0, 0);
    }

    private static void requireValid(long millis) {
        if (millis < -1) {
            throw new IllegalArgumentException("remaining milliseconds must be at least -1");
        }
    }
}
