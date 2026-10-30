package com.caxture.manhuntuniversal.fabric.ui;

import java.util.OptionalInt;
import java.util.List;
import java.util.Arrays;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;

/** Pure geometry for the 16:9 selector, with equal panels for visible options. */
public final class CompassSelectorLayout {
    public static final SelectorRect SOURCE_CROP = new SelectorRect(853, 0, 854, 1440);
    private final SelectorRect canvas;
    private final List<CompassSelectorOption> options;

    private CompassSelectorLayout(SelectorRect canvas, TrackingAvailability availability) {
        this.canvas = canvas;
        this.options = Arrays.stream(CompassSelectorOption.values())
                .filter(option -> availability.visible(option.target())).toList();
    }

    public static CompassSelectorLayout fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, TrackingAvailability.allUnlocked());
    }

    public static CompassSelectorLayout fit(int screenWidth, int screenHeight,
                                            TrackingAvailability availability) {
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
                contentHeight), availability);
    }

    public SelectorRect canvas() {
        return canvas;
    }

    public SelectorRect panel(int index) {
        if (index < 0 || index >= options.size()) {
            throw new IllegalArgumentException("panel index must identify a visible option");
        }
        int left = canvas.x() + (int) ((long) index * canvas.width() / options.size());
        int right = canvas.x() + (int) ((long) (index + 1) * canvas.width() / options.size());
        return new SelectorRect(left, canvas.y(), right - left, canvas.height());
    }

    public OptionalInt panelAt(int mouseX, int mouseY) {
        if (options.isEmpty() || !canvas.contains(mouseX, mouseY)) {
            return OptionalInt.empty();
        }
        for (int index = 0; index < options.size(); index++) {
            if (panel(index).contains(mouseX, mouseY)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    public int panelCount() {
        return options.size();
    }

    public CompassSelectorOption option(int index) {
        return options.get(index);
    }

    public SelectorRect sourceCrop() {
        if (options.size() == 3 || options.isEmpty()) {
            return SOURCE_CROP;
        }
        int cropWidth = 2560 / options.size();
        return new SelectorRect((2560 - cropWidth) / 2, 0, cropWidth, 1440);
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
        return opacity(panelIndex, selectedIndex, hoveredIndex, true);
    }

    public static float opacity(int panelIndex, int selectedIndex, int hoveredIndex, boolean unlocked) {
        return unlocked && (panelIndex == selectedIndex || panelIndex == hoveredIndex) ? 1.0f : 0.5f;
    }
}
