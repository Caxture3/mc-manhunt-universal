package com.caxture.manhuntuniversal.api.paper.presentation;
import com.caxture.manhuntuniversal.api.*;
public interface PresentationRegistry {
    ExtensionResult<ExtensionHandle> identity(ExtensionRegistration owner, int priority, boolean revealRoles, ObserverIdentityProvider provider);
    ExtensionResult<ExtensionHandle> intercept(ExtensionRegistration owner, PresentationSelector selector, int priority, PresentationProvider provider);
    ExtensionResult<ExtensionHandle> appendResult(ExtensionRegistration owner, ResultSection section);
    ExtensionResult<Void> refresh(ExtensionRegistration owner, RefreshRequest request);
}
