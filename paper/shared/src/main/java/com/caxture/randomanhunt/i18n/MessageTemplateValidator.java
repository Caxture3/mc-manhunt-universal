package com.caxture.randomanhunt.i18n;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageTemplateValidator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_-]+)}");

    private MessageTemplateValidator() {
    }

    public static Set<String> placeholders(String template) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return Set.copyOf(result);
    }

    public static boolean samePlaceholders(String candidate, String canonical) {
        return placeholders(candidate).equals(placeholders(canonical));
    }
}
