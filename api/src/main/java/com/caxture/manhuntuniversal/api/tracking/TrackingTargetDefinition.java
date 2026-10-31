package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.LocalizedText;
import java.util.*;
import java.util.function.Function;
/** Providers remain owned by the registering plugin; list() exposes metadata without these callbacks. */
public record TrackingTargetDefinition(String id, LocalizedText name, LocalizedText secondaryName, LocalizedText description,
                                       int order, TrackingWorld world, TrackingAppearance appearance,
                                       TrackingAvailabilityPolicy availability, TrackingLocator locator,
                                       boolean fallbackToCore, Optional<Function<LocateRequest, String>> sharedCacheKey) {
    public TrackingTargetDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(secondaryName); Objects.requireNonNull(description);
        Objects.requireNonNull(world); Objects.requireNonNull(appearance); Objects.requireNonNull(availability);
        Objects.requireNonNull(locator); Objects.requireNonNull(sharedCacheKey);
        if (id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("://")) throw new IllegalArgumentException("Invalid target ID");
        limit(name, 64); limit(secondaryName, 64); limit(description, 128);
    }
    public TrackingTargetDefinition(String id, LocalizedText name, TrackingWorld world, TrackingLocator locator) {
        this(id, name, LocalizedText.of(""), LocalizedText.of(""), 100, world, TrackingAppearance.generic(),
                viewer -> TargetAvailability.available(), locator, false, Optional.empty());
    }
    private static void limit(LocalizedText value, int maximum) {
        if (value.fallback().codePointCount(0, value.fallback().length()) > maximum
                || value.translations().values().stream().anyMatch(text -> text.codePointCount(0, text.length()) > maximum))
            throw new IllegalArgumentException("Tracking label exceeds " + maximum + " code points");
    }
}
