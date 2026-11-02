package com.caxture.manhuntuniversal.tracking;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TrackingCoordinateProjectionTest {
    final World overworld=world(World.Environment.NORMAL,100,64,200),nether=world(World.Environment.NETHER,5,80,6),end=world(World.Environment.THE_END,0,50,0);
    @Test void projectedOriginsAndMovementReferencesUseCurrentScaleInBothDirections(){
        var source=new Location(overworld,128,60,-32);
        for(int scale:new int[]{1,4,8,32}){
            var origin=TrackingCoordinateProjection.origin(source,nether,Optional.empty(),scale,29_999_984);
            var reference=TrackingCoordinateProjection.reference(source,nether,Optional.empty(),scale,29_999_984);
            assertEquals(128.0/scale,origin.getX());assertEquals(-32.0/scale,origin.getZ());assertEquals(origin,reference);assertEquals(80,origin.getY());
            var reverse=TrackingCoordinateProjection.origin(new Location(nether,10,100,-20),overworld,Optional.empty(),scale,29_999_984);
            assertEquals(10.0*scale,reverse.getX());assertEquals(-20.0*scale,reverse.getZ());assertEquals(64,reverse.getY());
        }
    }
    @Test void realVisitedPositionsArePreservedButMovementBasisUsesNewProjection(){
        var saved=new TrackingPosition(nether.getUID(),TrackingWorldKind.NETHER,500,70,-400);
        var source=new Location(overworld,128,60,-32);
        assertEquals(new Location(nether,500,70,-400),TrackingCoordinateProjection.origin(source,nether,Optional.of(saved),4,29_999_984));
        assertEquals(32,TrackingCoordinateProjection.reference(source,nether,Optional.of(saved),4,29_999_984).getX());assertEquals(500,saved.x());
        assertEquals(source,TrackingCoordinateProjection.origin(source,overworld,Optional.empty(),4,29_999_984));
        assertEquals(end.getSpawnLocation(),TrackingCoordinateProjection.origin(source,end,Optional.empty(),4,29_999_984));
    }
    static World world(World.Environment environment,double x,double y,double z){
        UUID id=UUID.randomUUID();World[] self={null};
        var border=(WorldBorder)Proxy.newProxyInstance(WorldBorder.class.getClassLoader(),new Class<?>[]{WorldBorder.class},(p,m,a)->switch(m.getName()){
            case "getCenter"->new Location(self[0],0,0,0);case "getSize"->59_999_968.0;case "getMaxCenterCoordinate"->29_999_984.0;default->throw new UnsupportedOperationException(m.getName());});
        self[0]=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
            case "getUID"->id;case "getEnvironment"->environment;case "getSpawnLocation"->new Location(self[0],x,y,z);case "getWorldBorder"->border;
            case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->environment.name();default->throw new UnsupportedOperationException(m.getName());});return self[0];
    }
}
