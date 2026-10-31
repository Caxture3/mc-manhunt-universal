package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
/** Existing resource identifiers only; never URLs, paths or item NBT. Missing textures use the theme fallback. */
public record TrackingAppearance(Theme theme, int color, Optional<String> texture, Optional<String> itemIcon) {
    public enum Theme { GENERIC, VILLAGE, FORTRESS, BASTION }
    public TrackingAppearance { Objects.requireNonNull(theme); Objects.requireNonNull(texture); Objects.requireNonNull(itemIcon);
        texture.ifPresent(TrackingAppearance::resource); itemIcon.ifPresent(id -> {
            resource(id); if (!id.startsWith("minecraft:")) throw new IllegalArgumentException("Item icons must use vanilla IDs");
        }); }
    public static TrackingAppearance generic() { return new TrackingAppearance(Theme.GENERIC, 0xff303840, Optional.empty(), Optional.empty()); }
    private static void resource(String id) { if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("..") || id.contains("://")) throw new IllegalArgumentException("Invalid resource identifier"); }
}
