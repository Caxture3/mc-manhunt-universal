package com.caxture.randomanhunt.fabric.ui;

import java.util.OptionalInt;

/** Pure geometry for the 16:9, three-panel tracking selector. */
public final class CompassSelectorLayout {
    public static final SelectorRect SOURCE_CROP = new SelectorRect(853, 0, 854, 1440);
    private static final int PANEL_COUNT = 3;

    private final SelectorRect canvas;

    private CompassSelectorLayout(SelectorRect canvas) {
        this.canvas = canvas;
    }

    public static CompassSelectorLayout fit(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        int contentWidth;
        int contentHeight;
        if ((long) screenWidth * 9L <= (long) screenHeight * 16L) {
            contentWidth = screenWidth;
            contentHeight = (int) ((long) screenWidth * 9L / 16L);
        } else {
            contentHeight = screenHeight;
            contentWidth = (int) ((long) screenHeight * 16L / 9L);
        }
        return new CompassSelectorLayout(new SelectorRect(
                (screenWidth - contentWidth) / 2,
                (screenHeight - contentHeight) / 2,
                contentWidth,
                contentHeight));
    }

    public SelectorRect canvas() {
        return canvas;
    }

    public SelectorRect panel(int index) {
        if (index < 0 || index >= PANEL_COUNT) {
            throw new IllegalArgumentException("panel index must be between 0 and 2");
        }
        int left = canvas.x() + (int) ((long) index * canvas.width() / PANEL_COUNT);
        int right = canvas.x() + (int) ((long) (index + 1) * canvas.width() / PANEL_COUNT);
        return new SelectorRect(left, canvas.y(), right - left, canvas.height());
    }

    public OptionalInt panelAt(int mouseX, int mouseY) {
        if (!canvas.contains(mouseX, mouseY)) {
            return OptionalInt.empty();
        }
        int relativeX = mouseX - canvas.x();
        int index = Math.min(PANEL_COUNT - 1,
                (int) ((long) relativeX * PANEL_COUNT / canvas.width()));
        return OptionalInt.of(index);
    }

    public SelectorTypography typography() {
        if (canvas.height() >= 600) {
            return new SelectorTypography(3, 3, 2);
        }
        if (canvas.height() >= 360) {
            return new SelectorTypography(2, 2, 1);
        }
        return new SelectorTypography(1, 1, 1);
    }

    public static float opacity(int panelIndex, int selectedIndex, int hoveredIndex) {
        return panelIndex == selectedIndex || panelIndex == hoveredIndex ? 1.0f : 0.5f;
    }
}
