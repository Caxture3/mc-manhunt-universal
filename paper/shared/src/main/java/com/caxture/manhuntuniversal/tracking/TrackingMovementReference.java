package com.caxture.manhuntuniversal.tracking;
import com.caxture.manhuntuniversal.api.WorldPosition;
final class TrackingMovementReference{
    private TrackingMovementReference(){ }
    static boolean moved(WorldPosition old,WorldPosition current,int minimum){return !old.worldId().equals(current.worldId())
            ||Math.pow(old.x()-current.x(),2)+Math.pow(old.y()-current.y(),2)+Math.pow(old.z()-current.z(),2)>=(double)minimum*minimum;}
}
