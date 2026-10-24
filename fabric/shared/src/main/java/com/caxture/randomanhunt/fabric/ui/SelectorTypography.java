package com.caxture.randomanhunt.fabric.ui;

public record SelectorTypography(int titleScale, int chineseScale, int englishScale) {
    public SelectorTypography {
        if (titleScale < 1 || chineseScale < 1 || englishScale < 1) {
            throw new IllegalArgumentException("font scales must be positive integers");
        }
    }
}
