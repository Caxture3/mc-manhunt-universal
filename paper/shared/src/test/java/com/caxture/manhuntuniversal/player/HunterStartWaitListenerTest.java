package com.caxture.manhuntuniversal.player;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HunterStartWaitListenerTest {
    final UUID id=UUID.randomUUID();boolean held=true,internal;
    final Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
        case "getUniqueId"->id;case "toString"->"Hunter";default->throw new UnsupportedOperationException(m.getName());});
    final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
        case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->throw new UnsupportedOperationException(m.getName());});
    final Location anchor=new Location(world,10,70,20);
    final HunterStartWaitListener listener=new HunterStartWaitListener(player->held,player->anchor,()->internal);
    @Test void freezesCoordinatesButKeepsViewRotationAndDoesNotMutateAnchor(){
        var event=new PlayerMoveEvent(player,anchor.clone(),new Location(world,11,71,21,80,30));listener.move(event);
        assertEquals(10,event.getTo().getX());assertEquals(70,event.getTo().getY());assertEquals(20,event.getTo().getZ());assertEquals(80,event.getTo().getYaw());assertEquals(30,event.getTo().getPitch());assertEquals(0,anchor.getYaw());
        held=false;var free=new PlayerMoveEvent(player,anchor.clone(),new Location(world,11,71,21));listener.move(free);assertEquals(11,free.getTo().getX());
    }
    @Test void externalTeleportsAreBlockedButOwnedCorrectionAndReleasedPlayersAreAllowed(){
        var event=new PlayerTeleportEvent(player,anchor.clone(),new Location(world,100,80,200),PlayerTeleportEvent.TeleportCause.COMMAND);listener.teleport(event);assertTrue(event.isCancelled());
        event.setCancelled(false);internal=true;listener.teleport(event);assertFalse(event.isCancelled());internal=false;held=false;listener.teleport(event);assertFalse(event.isCancelled());
    }
    @Test void alreadyCancelledAirInteractionsStillDenyItemUse(){
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,null,null,null);event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DEFAULT);
        listener.interact(event);assertEquals(Event.Result.DENY,event.useItemInHand());assertEquals(Event.Result.DENY,event.useInteractedBlock());
    }
    @Test void attackingVehiclesAndOtherNonlivingEntitiesIsBlockedBeforeDamage(){
        Entity vehicle=(Entity)Proxy.newProxyInstance(Entity.class.getClassLoader(),new Class<?>[]{Entity.class},(p,m,a)->{throw new UnsupportedOperationException(m.getName());});
        var attack=new PrePlayerAttackEntityEvent(player,vehicle,true);listener.preAttack(attack);assertTrue(attack.isCancelled());
        held=false;var released=new PrePlayerAttackEntityEvent(player,vehicle,true);listener.preAttack(released);assertFalse(released.isCancelled());
    }
    @Test void passiveItemPickupIsBlockedOnlyForWaitingPlayers(){
        Item item=(Item)Proxy.newProxyInstance(Item.class.getClassLoader(),new Class<?>[]{Item.class},(p,m,a)->{throw new UnsupportedOperationException(m.getName());});
        var pickup=new EntityPickupItemEvent(player,item,0);listener.pickup(pickup);assertTrue(pickup.isCancelled());
        held=false;var released=new EntityPickupItemEvent(player,item,0);listener.pickup(released);assertFalse(released.isCancelled());held=true;
        LivingEntity mob=(LivingEntity)Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),new Class<?>[]{LivingEntity.class},(p,m,a)->{throw new UnsupportedOperationException(m.getName());});
        var other=new EntityPickupItemEvent(mob,item,0);listener.pickup(other);assertFalse(other.isCancelled());
    }
}
