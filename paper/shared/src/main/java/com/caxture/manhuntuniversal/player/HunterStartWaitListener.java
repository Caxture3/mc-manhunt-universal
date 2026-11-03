package com.caxture.manhuntuniversal.player;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.*;
import java.util.function.*;
/** Waiting hunters can look/chat/view inventory, but cannot act or take damage. */
public final class HunterStartWaitListener implements Listener {
    private final Predicate<UUID> held;private final Function<UUID,Location> anchor;private final BooleanSupplier internalTeleport;
    public HunterStartWaitListener(Predicate<UUID> held,Function<UUID,Location> anchor,BooleanSupplier internalTeleport){this.held=held;this.anchor=anchor;this.internalTeleport=internalTeleport;}
    private boolean waiting(Entity entity){return entity instanceof Player&&held.test(entity.getUniqueId());}
    private boolean attacking(Entity entity){return waiting(entity)||entity instanceof Projectile projectile&&projectile.getShooter() instanceof Entity shooter&&waiting(shooter);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void preAttack(PrePlayerAttackEntityEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent event){if(waiting(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void vehicleDamage(VehicleDamageEvent event){if(attacking(event.getAttacker()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void vehicleDestroy(VehicleDestroyEvent event){if(attacking(event.getAttacker()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hanging(HangingBreakByEntityEvent event){if(attacking(event.getRemover()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void move(PlayerMoveEvent event){
        if(!waiting(event.getPlayer())||event.getTo()==null)return;Location fixed=anchor.apply(event.getPlayer().getUniqueId());if(fixed==null)return;
        Location to=event.getTo();if(!fixed.getWorld().equals(to.getWorld())||fixed.getX()!=to.getX()||fixed.getY()!=to.getY()||fixed.getZ()!=to.getZ()){
            fixed=fixed.clone();fixed.setYaw(to.getYaw());fixed.setPitch(to.getPitch());event.setTo(fixed);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void teleport(PlayerTeleportEvent event){if(!internalTeleport.getAsBoolean()&&waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void portal(PlayerPortalEvent event){teleport(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent event){if(waiting(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void attack(EntityDamageByEntityEvent event){
        if(attacking(event.getDamager()))event.setCancelled(true);
        if(waiting(event.getEntity()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void interact(PlayerInteractEvent event){if(waiting(event.getPlayer())){event.setCancelled(true);event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interactEntity(PlayerInteractEntityEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interactAtEntity(PlayerInteractAtEntityEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void placeBlock(BlockPlaceEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void consume(PlayerItemConsumeEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent event){if(waiting(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void inventoryClick(InventoryClickEvent event){if(held.test(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void inventoryDrag(InventoryDragEvent event){if(held.test(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void shoot(EntityShootBowEvent event){if(waiting(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void launch(ProjectileLaunchEvent event){if(event.getEntity().getShooter() instanceof Entity entity&&waiting(entity))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void mount(EntityMountEvent event){if(waiting(event.getEntity()))event.setCancelled(true);}
}
