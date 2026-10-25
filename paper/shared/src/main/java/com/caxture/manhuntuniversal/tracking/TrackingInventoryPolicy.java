package com.caxture.manhuntuniversal.tracking;

import org.bukkit.event.inventory.InventoryAction;

public final class TrackingInventoryPolicy {
    private TrackingInventoryPolicy() {
    }

    public static boolean blocksBundleTransfer(
            InventoryAction action,
            boolean currentItemMarked,
            boolean cursorItemMarked
    ) {
        return switch (action) {
            case PICKUP_ALL_INTO_BUNDLE, PICKUP_SOME_INTO_BUNDLE -> currentItemMarked;
            case PLACE_ALL_INTO_BUNDLE, PLACE_SOME_INTO_BUNDLE -> cursorItemMarked;
            default -> false;
        };
    }
}
