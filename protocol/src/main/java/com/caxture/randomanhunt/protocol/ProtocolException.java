package com.caxture.randomanhunt.protocol;

public final class ProtocolException extends IllegalArgumentException {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
