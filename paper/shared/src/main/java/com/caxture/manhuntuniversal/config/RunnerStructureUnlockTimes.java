package com.caxture.manhuntuniversal.config;

/** One mapped configuration value; seconds are relative to the match's formal start. */
public record RunnerStructureUnlockTimes(int village, int bastionRemnant, int netherFortress, int trialChambers) {
    public static final String CONFIG_KEY = "runner-structure-tracker-unlock-time";

    public RunnerStructureUnlockTimes {
        if (village < -1 || bastionRemnant < -1 || netherFortress < -1 || trialChambers < -1) {
            throw new IllegalArgumentException("Unlock time must be -1 or nonnegative seconds");
        }
    }
    public RunnerStructureUnlockTimes(int village,int bastionRemnant,int netherFortress) {
        this(village,bastionRemnant,netherFortress,-1);
    }

    public boolean anyEnabled() {
        return village >= 0 || bastionRemnant >= 0 || netherFortress >= 0 || trialChambers >= 0;
    }

}
