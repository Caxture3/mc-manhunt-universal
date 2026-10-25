package com.caxture.manhuntuniversal.i18n;

public final class PrefixExpander {
    private PrefixExpander() {
    }

    public static String expand(String message, String prefix, boolean includePrefix) {
        return message.replace("{prefix}", includePrefix ? prefix : "");
    }
}
