package com.caxture.randomanhunt.fabric.ui;

import java.util.Locale;

public final class CompassSelectorLanguage {
    private static final String TITLE_KEY = "randomanhunt.tracking_selector.title.";

    private CompassSelectorLanguage() {
    }

    public static String titleKey(String locale) {
        if (locale == null) {
            return TITLE_KEY + "en";
        }
        String normalized = locale.toLowerCase(Locale.ROOT);
        return TITLE_KEY + (normalized.equals("zh")
                || normalized.startsWith("zh_")
                || normalized.startsWith("zh-") ? "zh" : "en");
    }
}
