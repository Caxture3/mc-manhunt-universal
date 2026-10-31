package com.caxture.manhuntuniversal.tracking;
import java.time.Instant;
import java.util.*;
final class DeferredTrackingRefresh {
    private final Map<UUID,Instant> pending=new HashMap<>();
    void request(UUID player,Instant due){pending.merge(player,due,(a,b)->a.isBefore(b)?a:b);}
    Set<UUID> drain(Instant now){Set<UUID> ready=new HashSet<>();pending.entrySet().removeIf(entry->{boolean due=!now.isBefore(entry.getValue());if(due)ready.add(entry.getKey());return due;});return Set.copyOf(ready);}
    void cancel(UUID player){pending.remove(player);} void clear(){pending.clear();}
}
