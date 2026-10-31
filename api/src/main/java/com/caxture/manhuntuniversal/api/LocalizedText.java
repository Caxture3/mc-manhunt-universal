package com.caxture.manhuntuniversal.api;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable localized text with an explicit fallback; locale names use underscores. */
public record LocalizedText(String fallback, Map<String, String> translations) {
    public LocalizedText { Objects.requireNonNull(fallback); translations = Map.copyOf(translations); }
    public static LocalizedText of(String text) { return new LocalizedText(text, Map.of()); }
    public static LocalizedText bilingual(String english, String chinese) {
        return new LocalizedText(english, Map.of("en_us", english, "zh_cn", chinese));
    }
    public String resolve(String locale) {
        String normalized = locale == null ? "en_us" : locale.replace('-', '_').toLowerCase(Locale.ROOT);
        return translations.getOrDefault(normalized, normalized.startsWith("zh_")
                ? translations.getOrDefault("zh_cn", fallback) : fallback);
    }
}
