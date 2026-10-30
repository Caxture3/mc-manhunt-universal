package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingAvailability;

import java.util.Objects;

/** Advances received remaining durations using a local monotonic time anchor. */
public final class TrackingAvailabilityClock {
    private TrackingAvailability received;
    private long receivedNanos;

    public TrackingAvailabilityClock(TrackingAvailability availability, long receivedNanos) {
        update(availability, receivedNanos);
    }

    public void update(TrackingAvailability availability, long nowNanos) {
        received = Objects.requireNonNull(availability, "availability");
        receivedNanos = nowNanos;
    }

    public TrackingAvailability at(long nowNanos) {
        long elapsedMillis = Math.max(0L, nowNanos - receivedNanos) / 1_000_000L;
        return new TrackingAvailability(remaining(received.villageMillis(), elapsedMillis),
                remaining(received.netherFortressMillis(), elapsedMillis),
                remaining(received.bastionRemnantMillis(), elapsedMillis));
    }

    private static long remaining(long initial, long elapsedMillis) {
        return initial < 0 ? -1 : Math.max(0L, initial - elapsedMillis);
    }
}
