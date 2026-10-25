package com.caxture.manhuntuniversal.i18n;

import java.util.Locale;

public final class LocaleSelector {
    private LocaleSelector() {
    }

    public static SupportedLanguage select(Locale locale) {
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage())
                ? SupportedLanguage.ZH_CN
                : SupportedLanguage.EN_US;
    }
}
