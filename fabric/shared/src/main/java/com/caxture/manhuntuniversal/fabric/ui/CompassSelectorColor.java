package com.caxture.manhuntuniversal.fabric.ui;

/** Alpha-only colors shared by selector images and labels. */
public final class CompassSelectorColor {
    private CompassSelectorColor() {
    }

    public static int alphaWhite(float opacity) {
        return alphaChannel(opacity) << 24 | 0x00FFFFFF;
    }

    private static int alphaChannel(float opacity) {
        return Math.round(Math.clamp(opacity, 0.0F, 1.0F) * 255.0F);
    }
}
