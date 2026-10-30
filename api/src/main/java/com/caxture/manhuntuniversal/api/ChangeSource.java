package com.caxture.manhuntuniversal.api;

import java.util.Objects;

/**
 * Origin and explanation of an atomic role change.
 * @param extensionId identifier of the extension initiating the change
 * @param reason explanation of the change or result
 */
public record ChangeSource(String extensionId, String reason) {
    /** Creates a validated value; null components are rejected. */
    public ChangeSource {
        Objects.requireNonNull(extensionId, "extensionId");
        Objects.requireNonNull(reason, "reason");
    }
}
