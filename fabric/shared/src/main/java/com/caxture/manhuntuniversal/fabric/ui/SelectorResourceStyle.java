package com.caxture.manhuntuniversal.fabric.ui;
import com.caxture.manhuntuniversal.protocol.TrackingTargetDescriptor;
import java.util.*;
import java.util.function.Predicate;
/** Lookups are against the currently installed resource manager, so pack reload/removal is safe. */
public final class SelectorResourceStyle {
    private SelectorResourceStyle() { }
    public static Optional<String> texture(TrackingTargetDescriptor option, Predicate<String> exists) {
        if (option.texture().filter(exists).isPresent()) return option.texture();
        String name = switch (option.theme()) { case "village" -> "village"; case "fortress" -> "fortress"; case "bastion" -> "bastion_remnant"; default -> null; };
        if (name == null) return Optional.empty();
        String fallback = "randomanhunt:textures/background/compass_selector/" + name + ".png";
        return exists.test(fallback) ? Optional.of(fallback) : Optional.empty();
    }
}
