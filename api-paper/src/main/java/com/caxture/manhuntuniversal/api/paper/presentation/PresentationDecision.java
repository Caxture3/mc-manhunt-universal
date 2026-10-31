package com.caxture.manhuntuniversal.api.paper.presentation;
import java.util.Objects;
public record PresentationDecision(boolean cancelled, PresentationPayload payload) {
    public PresentationDecision { Objects.requireNonNull(payload); }
    public static PresentationDecision replace(PresentationPayload payload) { return new PresentationDecision(false, payload); }
    public static PresentationDecision cancel() { return new PresentationDecision(true, new PresentationPayload(java.util.List.of(), java.util.Optional.empty(), java.util.Optional.empty())); }
}
