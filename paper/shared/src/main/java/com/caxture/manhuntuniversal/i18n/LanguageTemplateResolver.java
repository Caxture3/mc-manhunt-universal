package com.caxture.manhuntuniversal.i18n;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class LanguageTemplateResolver {
    private static final Set<String> COMMAND_USAGE_KEYS = Set.of(
            "command.usage", "game.start.usage", "config.usage", "team.usage", "config.structure-unlock-usage");
    private static final Pattern LEGACY_COMMAND_ROOT = Pattern.compile(
            "(?<![a-z0-9_./-])/(?:[a-z0-9_.-]+:)?rmh(?![a-z0-9_./:-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);

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
                && MessageTemplateValidator.samePlaceholders(candidate, english)
                && !(COMMAND_USAGE_KEYS.contains(key)
                && LEGACY_COMMAND_ROOT.matcher(LEGACY_FORMATTING.matcher(candidate).replaceAll("")).find())) {
            return candidate;
        }
        warning.accept(key);
        return localized;
    }
}
