package com.caxture.manhuntuniversal.api.paper.presentation;
import java.util.*;
public record RefreshRequest(UUID playerId, Set<RefreshScope> scopes) {
    public RefreshRequest { Objects.requireNonNull(playerId); scopes = Set.copyOf(scopes); }
}
