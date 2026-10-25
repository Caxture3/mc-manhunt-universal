package com.caxture.randomanhunt.optimization;

/** Promotes a fraction of vanilla non-target results, preserving their relative probabilities. */
public final class PiglinBarterPolicy {
    public static final int ROLL_BOUND = 154752;

    public enum Replacement { PEARLS, OBSIDIAN, FIRE_RESISTANCE, SPLASH_FIRE_RESISTANCE, KEEP }

    private PiglinBarterPolicy() {}

    public static Replacement replacement(int roll) {
        if (roll < 0 || roll >= ROLL_BOUND) throw new IllegalArgumentException("Invalid barter roll");
        if (roll < 11168) return Replacement.PEARLS;
        if (roll < 23948) return Replacement.OBSIDIAN;
        if (roll < 25097) return Replacement.FIRE_RESISTANCE;
        if (roll < 26246) return Replacement.SPLASH_FIRE_RESISTANCE;
        return Replacement.KEEP;
    }
}
