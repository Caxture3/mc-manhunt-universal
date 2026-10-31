package com.caxture.manhuntuniversal.api.paper.presentation;
import java.util.*;
public record PresentationSelector(Set<PresentationChannel> channels, String idPrefix, Optional<UUID> recipientId, boolean revealRoles) {
    public PresentationSelector { channels = Set.copyOf(channels); Objects.requireNonNull(idPrefix); Objects.requireNonNull(recipientId); }
    public static PresentationSelector all() { return new PresentationSelector(EnumSet.allOf(PresentationChannel.class), "", Optional.empty(), false); }
}
