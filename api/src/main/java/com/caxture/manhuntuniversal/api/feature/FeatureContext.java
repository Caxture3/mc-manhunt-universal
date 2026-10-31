package com.caxture.manhuntuniversal.api.feature;
import com.caxture.manhuntuniversal.api.*;
import java.util.*;
/** An environment operation has no player/role. Metadata is detached from Bukkit events. */
public record FeatureContext(String featureId, String operation, Optional<MatchSnapshot> match,
                             Optional<UUID> playerId, Optional<TeamRole> role, Optional<WorldPosition> position,
                             Map<String, String> attributes) {
    public FeatureContext {
        Objects.requireNonNull(featureId); Objects.requireNonNull(operation); Objects.requireNonNull(match);
        Objects.requireNonNull(playerId); Objects.requireNonNull(role); Objects.requireNonNull(position);
        attributes = Map.copyOf(attributes);
    }
}
