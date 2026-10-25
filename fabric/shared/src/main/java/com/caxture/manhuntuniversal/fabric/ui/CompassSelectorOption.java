package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;

import java.util.Arrays;
import java.util.Locale;

public enum CompassSelectorOption {
    VILLAGE(TrackingSelectionTarget.VILLAGE, "village", 1),
    NETHER_FORTRESS(TrackingSelectionTarget.NETHER_FORTRESS, "fortress", 2),
    BASTION_REMNANT(TrackingSelectionTarget.BASTION_REMNANT, "bastion_remnant", 2);

    private final TrackingSelectionTarget target;
    private final String assetName;
    private final int englishLines;

    CompassSelectorOption(TrackingSelectionTarget target, String assetName, int englishLines) {
        this.target = target;
        this.assetName = assetName;
        this.englishLines = englishLines;
    }

    public TrackingSelectionTarget target() {
        return target;
    }

    public String texturePath() {
        return "textures/background/compass_selector/" + assetName + ".png";
    }

    public String translationBaseKey() {
        return "randomanhunt.tracking_selector.option." + target.name().toLowerCase(Locale.ROOT);
    }

    public int englishLines() {
        return englishLines;
    }

    public static CompassSelectorOption fromTarget(TrackingSelectionTarget target) {
        return Arrays.stream(values())
                .filter(option -> option.target == target)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target is not displayed by the compass selector: " + target));
    }
}
