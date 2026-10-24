package com.caxture.randomanhunt.i18n;

public enum SupportedLanguage {
    ZH_CN("zh_cn"),
    EN_US("en_us");

    private final String resourceName;

    SupportedLanguage(String resourceName) {
        this.resourceName = resourceName;
    }

    public String resourceName() {
        return resourceName;
    }
}
