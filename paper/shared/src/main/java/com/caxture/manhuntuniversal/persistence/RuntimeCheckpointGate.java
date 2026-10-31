package com.caxture.manhuntuniversal.persistence;

import java.util.*;

/** Protect only the not-yet-resumed session; a stop or a replacement match must still be saved. */
final class RuntimeCheckpointGate {
    private boolean pending;
    private Optional<UUID> loading=Optional.empty();
    void begin(Optional<UUID> session) { loading=session;pending=true; }
    void completed() { pending=false; }
    boolean mayWrite(Optional<UUID> current) { return !pending||loading.isEmpty()||!loading.equals(current); }
}
