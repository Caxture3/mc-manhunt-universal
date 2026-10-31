package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.Optional;

/** Outcome for registry operations, separate from match-oriented OperationResult. */
public record ExtensionResult<T>(OperationStatus status, Optional<T> value, String message) {
    public ExtensionResult {
        Objects.requireNonNull(status); Objects.requireNonNull(value); Objects.requireNonNull(message);
        if (status != OperationStatus.OK && value.isPresent()) throw new IllegalArgumentException("Failed result has a value");
    }
    public boolean successful() { return status == OperationStatus.OK; }
    public static <T> ExtensionResult<T> ok(T value) {
        return new ExtensionResult<>(OperationStatus.OK, Optional.ofNullable(value), "OK");
    }
    public static <T> ExtensionResult<T> rejected(OperationStatus status, String message) {
        return new ExtensionResult<>(status, Optional.empty(), message);
    }
}
