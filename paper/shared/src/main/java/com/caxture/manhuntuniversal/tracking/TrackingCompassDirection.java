package com.caxture.manhuntuniversal.tracking;
import org.bukkit.inventory.meta.CompassMeta;
/** An empty lodestone component spins; removing the component would point to world spawn. */
final class TrackingCompassDirection {
    private TrackingCompassDirection(){}
    static void pause(CompassMeta meta){meta.setLodestone(null);meta.setLodestoneTracked(false);}
}
