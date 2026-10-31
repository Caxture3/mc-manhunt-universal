package com.caxture.manhuntuniversal.api.feature;

/** Main-thread registry of policies controlling individual core effects. */
public interface FeatureRegistry {
    java.util.Set<String> list();
    com.caxture.manhuntuniversal.api.ExtensionResult<com.caxture.manhuntuniversal.api.ExtensionHandle> policy(
            com.caxture.manhuntuniversal.api.ExtensionRegistration owner, String featureId, int priority, FeaturePolicy policy);
}
