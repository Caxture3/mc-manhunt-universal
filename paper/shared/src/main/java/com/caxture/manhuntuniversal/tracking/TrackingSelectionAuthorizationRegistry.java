package com.caxture.manhuntuniversal.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Issues one-use selection capabilities and preserves the current one on stale submissions. */
public final class TrackingSelectionAuthorizationRegistry {
    private final Map<UUID, Authorization> current = new HashMap<>();
    private long nextAuthorizationId;

    public Authorization issue(UUID playerId, UUID sessionId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        long authorizationId = Math.incrementExact(nextAuthorizationId);
        nextAuthorizationId = authorizationId;
        Authorization authorization = new Authorization(sessionId, authorizationId);
        current.put(playerId, authorization);
        return authorization;
    }

    public boolean consume(UUID playerId, UUID sessionId, long authorizationId) {
        Authorization expected = current.get(playerId);
        if (expected == null
                || !expected.sessionId().equals(sessionId)
                || expected.authorizationId() != authorizationId) {
            return false;
        }
        current.remove(playerId);
        return true;
    }

    public Optional<Authorization> invalidate(UUID playerId) {
        return Optional.ofNullable(current.remove(playerId));
    }

    public void clear() {
        current.clear();
    }

    public record Authorization(UUID sessionId, long authorizationId) {
        public Authorization {
            Objects.requireNonNull(sessionId, "sessionId");
            if (authorizationId <= 0) {
                throw new IllegalArgumentException("authorizationId must be positive");
            }
        }
    }
}
