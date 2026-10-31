package com.caxture.manhuntuniversal.api.config;

/** Main-thread configuration registration and effective-value service. */
public interface ConfigurationRegistry {
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> listen(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, java.util.function.Consumer<ConfigurationChange> listener);
    java.util.List<ConfigSnapshot> list();
    java.util.Optional<ConfigSnapshot> find(String key);
    <T> com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> register(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, ConfigDefinition<T> definition);
    com.caxture.manhuntuniversal.api.ExtensionResult<ConfigSnapshot> update(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key, Object value, long expectedConfigRevision);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> lock(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key, int priority, String reason);
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> override(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key,
            java.util.function.Function<ConfigSnapshot, Object> provider, int priority);
    /** Re-evaluate a dynamic provider after its external inputs change. */
    com.caxture.manhuntuniversal.api.ExtensionResult<ConfigSnapshot> refresh(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String key);
}
