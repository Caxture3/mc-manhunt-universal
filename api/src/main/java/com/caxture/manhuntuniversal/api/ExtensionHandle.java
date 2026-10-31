package com.caxture.manhuntuniversal.api;

/** Plugin-owned subregistration. Live operations require the server thread. */
public interface ExtensionHandle extends AutoCloseable {
    boolean active();
    /** Idempotently removes this registration and recomputes current underlying behavior. */
    @Override void close();
}
