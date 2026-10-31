package com.caxture.manhuntuniversal.api.text;

/** Runtime text layers; registrations never rewrite server language files. */
public interface TextRegistry {
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> register(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key, com.caxture.manhuntuniversal.api.LocalizedText definition);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> override(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key, int priority, TextProvider provider);
    java.util.Optional<String> resolve(TextContext context);
}
