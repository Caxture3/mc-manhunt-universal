package com.caxture.manhuntuniversal.extension.feature;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.feature.FeatureContext;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Supplier;
/** Converts live Paper context to data only, on the event/server thread. */
public final class CoreFeatureAccess {
    private final FeatureRegistryService registry;
    private final Supplier<Optional<MatchSnapshot>> match;
    public CoreFeatureAccess(FeatureRegistryService registry, Supplier<Optional<MatchSnapshot>> match) { this.registry = registry; this.match = match; }
    public boolean allows(String id, Entity actor, Location location, String operation) {
        var current = match.get();
        Optional<UUID> player = actor instanceof Player ? Optional.of(actor.getUniqueId()) : Optional.empty();
        Optional<TeamRole> role = current.flatMap(snapshot -> player.map(snapshot.participants()::get)).map(ParticipantSnapshot::role);
        Optional<WorldPosition> position = location == null || location.getWorld() == null ? Optional.empty()
                : Optional.of(new WorldPosition(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()));
        return registry.allows(new FeatureContext(id, operation, current, player, role, position,
                actor != null && !(actor instanceof Player) ? Map.of("entity-type", actor.getType().name()) : Map.of()));
    }
}
