package com.caxture.manhuntuniversal.portal;

import org.bukkit.PortalType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Global portal input adjustment: no match, participant or game-mode gate. */
public final class NetherCoordinateScaleListener implements Listener {
    private final IntSupplier scale;
    private final IntSupplier maximumWorldSize;
    public NetherCoordinateScaleListener(IntSupplier scale) { this(scale, () -> Integer.MAX_VALUE); }
    public NetherCoordinateScaleListener(IntSupplier scale, IntSupplier maximumWorldSize) {
        this.scale = Objects.requireNonNull(scale); this.maximumWorldSize = Objects.requireNonNull(maximumWorldSize);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled() || event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getTo() == null || !NetherCoordinateScale.applies(event.getFrom().getWorld(), event.getTo().getWorld())) return;
        int current = scale.getAsInt();
        if (current != 8) event.setTo(NetherCoordinateScale.project(event.getFrom(), event.getTo(), current, maximumWorldSize.getAsInt()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.isCancelled() || event.getPortalType() != PortalType.NETHER
                || event.getTo() == null || !NetherCoordinateScale.applies(event.getFrom().getWorld(), event.getTo().getWorld())) return;
        int current = scale.getAsInt();
        if (current != 8) event.setTo(NetherCoordinateScale.project(event.getFrom(), event.getTo(), current, maximumWorldSize.getAsInt()));
    }
}
