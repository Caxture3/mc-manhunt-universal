package com.caxture.manhuntuniversal.protocol;
import java.util.*;
/** Data-only, recipient-localized selector card; no game coordinates or executable content. */
public record TrackingTargetDescriptor(String id, String name, String secondaryName, String description, int order,
                                        String theme, int color, Optional<String> texture, Optional<String> itemIcon,
                                        long remainingMillis, boolean selectable, String lockedReason) {
    public TrackingTargetDescriptor {
        DynamicTrackingValidation.id(id); DynamicTrackingValidation.text(name, 64, false);
        DynamicTrackingValidation.text(secondaryName, 64, false); DynamicTrackingValidation.text(description, 128, false);
        DynamicTrackingValidation.text(theme, 32, true); DynamicTrackingValidation.text(lockedReason, 128, false);
        if (!theme.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid theme");
        Objects.requireNonNull(texture); Objects.requireNonNull(itemIcon);
        texture.ifPresent(DynamicTrackingValidation::resource); itemIcon.ifPresent(value -> {
            DynamicTrackingValidation.resource(value); if (!value.startsWith("minecraft:")) throw new IllegalArgumentException("Icon must be vanilla");
        });
        if (remainingMillis < 0 || selectable && remainingMillis != 0) throw new IllegalArgumentException("Invalid selectable state");
    }
}
