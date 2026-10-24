package com.caxture.randomanhunt.i18n;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.function.Consumer;

public final class LanguageTemplateResolver {
    private LanguageTemplateResolver() {
    }

    public static String resolve(
            YamlConfiguration external,
            YamlConfiguration builtInLanguage,
            YamlConfiguration builtInEnglish,
            String key,
            Consumer<String> warning
    ) {
        Object englishRaw = builtInEnglish.get(key);
        if (!(englishRaw instanceof String english)) {
            warning.accept(key);
            return key;
        }

        String localized = english;
        Object localizedRaw = builtInLanguage.get(key);
        if (localizedRaw instanceof String candidate
                && MessageTemplateValidator.samePlaceholders(candidate, english)) {
            localized = candidate;
        } else {
            warning.accept(key);
        }

        Object externalRaw = external.get(key);
        if (externalRaw instanceof String candidate
                && MessageTemplateValidator.samePlaceholders(candidate, english)) {
            return candidate;
        }
        warning.accept(key);
        return localized;
    }
}
