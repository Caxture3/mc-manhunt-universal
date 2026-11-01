package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.config.RunnerStructureUnlockTimes;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import java.time.Duration;
import java.time.Instant;

/** Authoritative server timing, shared by item eligibility, UI snapshots and input validation. */
public final class StructureUnlockPolicy {
    private StructureUnlockPolicy() {}

    public static TrackingAvailability availability(RunnerStructureUnlockTimes times, Instant startedAt, Instant now) {
        long elapsed = Math.max(0, Duration.between(startedAt, now).toMillis());
        return new TrackingAvailability(remaining(times.village(), elapsed),
                remaining(times.netherFortress(), elapsed), remaining(times.bastionRemnant(), elapsed));
    }

    public static boolean allows(TrackingTargetId target, TrackingAvailability availability) {
        return target == TrackingTargetId.NONE || target != null && target.selectableByRunner() && target.legacySelectorSupported()
                && availability.unlocked(TrackingSelectionTargetMapper.toProtocol(target));
    }

    public static long remainingMillis(TrackingTargetId target,RunnerStructureUnlockTimes times,Instant startedAt,Instant now) {
        int seconds=switch(target){
            case VILLAGE->times.village();case NETHER_FORTRESS->times.netherFortress();
            case BASTION_REMNANT->times.bastionRemnant();case TRIAL_CHAMBERS->times.trialChambers();case NONE->-1;
        };
        return remainingMillis(seconds,startedAt,now);
    }
    public static long remainingMillis(int seconds,Instant startedAt,Instant now) {
        if(seconds < -1)throw new IllegalArgumentException("Unlock time must be -1 or nonnegative seconds");
        return remaining(seconds,Math.max(0,Duration.between(startedAt,now).toMillis()));
    }

    private static long remaining(int seconds, long elapsed) {
        return seconds == -1 ? -1 : Math.max(0, seconds * 1000L - elapsed);
    }
}
