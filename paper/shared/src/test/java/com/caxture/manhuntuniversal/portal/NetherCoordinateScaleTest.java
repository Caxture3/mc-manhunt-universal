package com.caxture.manhuntuniversal.portal;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPortalEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NetherCoordinateScaleTest {
    static final double LIMIT=29_999_984;
    final World overworld=world(World.Environment.NORMAL,0,0,LIMIT*2);
    final World nether=world(World.Environment.NETHER,0,0,LIMIT*2);
    final Player player=proxy(Player.class,"Player");final Entity entity=proxy(Entity.class,"Entity");
    @Test void projectsBothDirectionsAtOneEightAndOtherIntegerScales(){
        for(int scale:new int[]{1,8,32,Integer.MAX_VALUE}){
            var from=new Location(overworld,128.5,72,-320.25);var template=new Location(nether,0,95,0,20,30);
            var mapped=NetherCoordinateScale.project(from,template,scale);
            assertEquals(128.5/scale,mapped.getX());assertEquals(-320.25/scale,mapped.getZ());
            var reverse=NetherCoordinateScale.project(new Location(nether,1,10,-2),new Location(overworld,0,90,0),scale);
            assertEquals(Math.min(LIMIT-1e-5,scale),reverse.getX(),1e-6);assertEquals(Math.max(-LIMIT,-2.0*scale),reverse.getZ(),1e-6);
            assertEquals(0,template.getX());assertEquals(95,mapped.getY());assertEquals(20,mapped.getYaw());assertEquals(30,mapped.getPitch());assertSame(nether,mapped.getWorld());
        }
    }
    @Test void destinationBorderAndAbsoluteLimitClampBothSignsWithoutOverflow(){
        World small=world(World.Environment.NORMAL,100,-50,20);
        var result=NetherCoordinateScale.project(new Location(nether,Double.MAX_VALUE,40,-Double.MAX_VALUE),new Location(small,0,80,0),Integer.MAX_VALUE);
        assertEquals(110-1e-5,result.getX(),1e-8);assertEquals(-60,result.getZ());assertEquals(80,result.getY());
        var huge=NetherCoordinateScale.project(new Location(nether,20_000_000,0,-20_000_000),new Location(overworld,0,50,0),Integer.MAX_VALUE);
        assertEquals(LIMIT-1e-5,huge.getX(),1e-6);assertEquals(-LIMIT,huge.getZ());
    }
    @Test void smallerServerWorldSizeAndOffsetBordersIntersectTheNativeCoordinateLimit(){
        var result=NetherCoordinateScale.project(new Location(nether,20_000_000,60,-20_000_000),new Location(overworld,0,90,0),Integer.MAX_VALUE,1000);
        assertEquals(1000-1e-5,result.getX(),1e-8);assertEquals(-1000,result.getZ());
        World offset=world(World.Environment.NORMAL,950,-950,200);
        var edge=NetherCoordinateScale.project(new Location(nether,500,60,-500),new Location(offset,0,80,0),Integer.MAX_VALUE,1000);
        assertEquals(1000-1e-5,edge.getX(),1e-8);assertEquals(-1000,edge.getZ());
        var inside=NetherCoordinateScale.project(new Location(nether,-1,60,1),new Location(offset,0,80,0),Integer.MAX_VALUE,1000);
        assertEquals(850,inside.getX());assertEquals(-850-1e-5,inside.getZ(),1e-8);
    }
    @Test void playersAndNonplayersUseCurrentScaleAndKeepNativeSearchAndCreateSettings(){
        int[] scale={4};var listener=new NetherCoordinateScaleListener(()->scale[0]);
        var original=new Location(nether,99,101,99,15,25);
        var event=new PlayerPortalEvent(player,new Location(overworld,128,64,-32),original,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,16,false,7);
        listener.onPlayerPortal(event);assertEquals(32,event.getTo().getX());assertEquals(-8,event.getTo().getZ());assertEquals(101,event.getTo().getY());
        assertEquals(15,event.getTo().getYaw());assertEquals(25,event.getTo().getPitch());assertEquals(16,event.getSearchRadius());assertFalse(event.getCanCreatePortal());assertEquals(7,event.getCreationRadius());
        scale[0]=2;
        var other=new EntityPortalEvent(entity,new Location(nether,10,60,-20),new Location(overworld,80,90,-160),128,true,9,PortalType.NETHER);
        listener.onEntityPortal(other);assertEquals(20,other.getTo().getX());assertEquals(-40,other.getTo().getZ());assertEquals(90,other.getTo().getY());
        assertEquals(128,other.getSearchRadius());assertTrue(other.getCanCreatePortal());assertEquals(9,other.getCreationRadius());
    }
    @Test void defaultEightAndCancelledEndAndOtherPortalTypesRemainUntouched()throws Exception{
        var original=new Location(nether,17,99,22);var from=new Location(overworld,128,60,160);
        var vanilla=new PlayerPortalEvent(player,from,original,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        new NetherCoordinateScaleListener(()->8).onPlayerPortal(vanilla);assertSame(original,vanilla.getTo());
        var listener=new NetherCoordinateScaleListener(()->1);
        for(var cause:List.of(PlayerTeleportEvent.TeleportCause.END_PORTAL,PlayerTeleportEvent.TeleportCause.COMMAND,PlayerTeleportEvent.TeleportCause.PLUGIN)){
            var event=new PlayerPortalEvent(player,from,original,cause);listener.onPlayerPortal(event);assertSame(original,event.getTo());
        }
        var cancelled=new PlayerPortalEvent(player,from,original,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);cancelled.setCancelled(true);listener.onPlayerPortal(cancelled);assertSame(original,cancelled.getTo());assertTrue(cancelled.isCancelled());
        var custom=new EntityPortalEvent(entity,from,original,128,true,16,PortalType.CUSTOM);listener.onEntityPortal(custom);assertSame(original,custom.getTo());
        var end=new PlayerPortalEvent(player,new Location(world(World.Environment.THE_END,0,0,1000),1,70,1),original,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);listener.onPlayerPortal(end);assertSame(original,end.getTo());
        for(var method:NetherCoordinateScaleListener.class.getDeclaredMethods())if(method.isAnnotationPresent(EventHandler.class)){
            var annotation=method.getAnnotation(EventHandler.class);assertEquals(EventPriority.NORMAL,annotation.priority());assertTrue(annotation.ignoreCancelled());
        }
    }
    static World world(World.Environment environment,double x,double z,double size){
        UUID id=UUID.randomUUID();World[] self={null};
        WorldBorder border=(WorldBorder)Proxy.newProxyInstance(WorldBorder.class.getClassLoader(),new Class<?>[]{WorldBorder.class},(p,m,a)->switch(m.getName()){
            case "getCenter"->new Location(self[0],x,0,z);case "getSize"->size;case "getMaxCenterCoordinate"->LIMIT;default->throw new UnsupportedOperationException(m.getName());});
        self[0]=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
            case "getUID"->id;case "getEnvironment"->environment;case "getWorldBorder"->border;case "getCoordinateScale"->environment==World.Environment.NETHER?8.0:1.0;
            case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->environment.name();default->throw new UnsupportedOperationException(m.getName());});return self[0];
    }
    static <T>T proxy(Class<T> type,String name){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()){
        case "toString"->name;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->throw new UnsupportedOperationException(m.getName());}));}
}
