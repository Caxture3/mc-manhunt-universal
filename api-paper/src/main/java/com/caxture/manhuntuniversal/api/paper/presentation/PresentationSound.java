package com.caxture.manhuntuniversal.api.paper.presentation;
import java.util.Objects;
public record PresentationSound(String key, float volume, float pitch) {
    public PresentationSound { Objects.requireNonNull(key); if (!Float.isFinite(volume) || !Float.isFinite(pitch) || volume < 0 || pitch < 0) throw new IllegalArgumentException("Invalid sound"); }
}
