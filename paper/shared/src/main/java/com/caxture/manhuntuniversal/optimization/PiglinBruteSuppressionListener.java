package com.caxture.manhuntuniversal.optimization;

import com.caxture.manhuntuniversal.api.feature.CoreFeatureIds;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.game.*;
import org.bukkit.Server;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import java.util.function.*;

/** Removes without killing: no drops, experience or gameplay death side effects. */
public final class PiglinBruteSuppressionListener implements Listener {
    private final BooleanSupplier active;
    private final Supplier<? extends Iterable<? extends Entity>> existing;
    private final Predicate<Entity> core;
    public PiglinBruteSuppressionListener(Server server, GameManager games, ConfigurationService configuration) {
        this(() -> configuration.get(BooleanConfigKey.DISABLE_PIGLIN_BRUTES)
                        && games.activeSession().filter(game -> game.state() == SessionState.RUNNING).isPresent(),
                () -> server.getWorlds().stream().flatMap(world -> world.getEntitiesByClass(PiglinBrute.class).stream()).toList(),
                entity -> configuration.coreEffect(CoreFeatureIds.PIGLIN_BRUTE_SUPPRESSION, entity, entity.getLocation(), "piglin-brute-suppression"));
    }
    PiglinBruteSuppressionListener(BooleanSupplier active, Supplier<? extends Iterable<? extends Entity>> existing, Predicate<Entity> core) {
        this.active=active;this.existing=existing;this.core=core;
    }
    private boolean blocked(Entity entity) { return active.getAsBoolean() && entity.getType()==EntityType.PIGLIN_BRUTE && core.test(entity); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawned(EntitySpawnEvent event) { if(blocked(event.getEntity()))event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void loaded(EntitiesLoadEvent event) { for(Entity entity:event.getEntities())if(blocked(entity))entity.remove(); }
    public void reconcile() { if(active.getAsBoolean())for(Entity entity:existing.get())if(blocked(entity))entity.remove(); }
}
