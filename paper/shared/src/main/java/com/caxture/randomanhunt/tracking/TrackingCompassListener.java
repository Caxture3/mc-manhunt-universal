package com.caxture.randomanhunt.tracking;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class TrackingCompassListener implements Listener {
    private final CompassTrackingService tracking;

    public TrackingCompassListener(CompassTrackingService tracking) {
        this.tracking = tracking;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (tracking.isMarkedCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            tracking.ensureNextTick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean external = isExternalView(event);
        boolean clickTop = event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean drops = event.getAction() == InventoryAction.DROP_ALL_CURSOR
                || event.getAction() == InventoryAction.DROP_ONE_CURSOR
                || event.getAction() == InventoryAction.DROP_ALL_SLOT
                || event.getAction() == InventoryAction.DROP_ONE_SLOT;
        if (TrackingInventoryPolicy.blocksBundleTransfer(
                event.getAction(),
                tracking.isMarkedCompass(current),
                tracking.isMarkedCompass(cursor))) {
            event.setCancelled(true);
            return;
        }
        if (drops && (tracking.isMarkedCompass(current) || tracking.isMarkedCompass(cursor))) {
            event.setCancelled(true);
            return;
        }
        if (clickTop && event.getClickedInventory() != player.getInventory()
                && (tracking.isMarkedCompass(cursor) || tracking.isMarkedCompass(current))) {
            event.setCancelled(true);
            return;
        }
        if (external
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && tracking.isMarkedCompass(current)) {
            event.setCancelled(true);
            return;
        }
        if (external
                && clickTop
                && event.getClick() == ClickType.NUMBER_KEY
                && event.getHotbarButton() >= 0
                && tracking.isMarkedCompass(
                player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            return;
        }
        if (external
                && clickTop
                && event.getClick() == ClickType.SWAP_OFFHAND
                && tracking.isMarkedCompass(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }
        tracking.ensureNextTick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !tracking.isMarkedCompass(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
        tracking.ensureNextTick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (containsMarkedCompass(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsMarkedCompass(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!tracking.isMarkedCompass(event.getItem())) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                if (tracking.activeParticipant(event.getPlayer())) {
                    tracking.openRunnerSelection(event.getPlayer());
                } else {
                    tracking.cleanupStale(event.getPlayer());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (!tracking.isMarkedCompass(item)) {
            return;
        }
        event.setCancelled(true);
        if (tracking.activeParticipant(event.getPlayer())) {
            tracking.openRunnerSelection(event.getPlayer());
        } else {
            tracking.ensureNextTick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        tracking.recordCurrentPosition(player);
        boolean keep = tracking.activeParticipant(player);
        List<ItemStack> retained = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            if (!tracking.isMarkedCompass(item)) {
                return false;
            }
            if (keep && tracking.isCurrentCompassFor(player, item)) {
                retained.add(item);
            }
            return true;
        });
        if (!event.getKeepInventory()) {
            event.getItemsToKeep().addAll(retained);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removeRetainedCompassAfterSettlement(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (tracking.activeParticipant(player)) {
            return;
        }
        event.getItemsToKeep().removeIf(tracking::isMarkedCompass);
        event.getDrops().removeIf(tracking::isMarkedCompass);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        tracking.ensureNextTick(event.getPlayer());
    }

    private boolean containsMarkedCompass(ItemStack[] items) {
        for (ItemStack item : items) {
            if (tracking.isMarkedCompass(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExternalView(InventoryEvent event) {
        InventoryType type = event.getView().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
    }
}
