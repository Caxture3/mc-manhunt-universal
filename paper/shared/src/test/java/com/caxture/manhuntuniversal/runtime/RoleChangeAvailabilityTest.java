package com.caxture.manhuntuniversal.runtime;

import com.caxture.manhuntuniversal.player.PendingPlayerState;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoleChangeAvailabilityTest {
    private final UUID id=UUID.randomUUID();
    private final PendingPlayerState pending=new PendingPlayerState();
    private boolean online=true,dead;
    private int deathReads;
    private final Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(proxy,method,args)->switch(method.getName()){
        case "isOnline"->online;
        case "isDead"->{deathReads++;yield dead;}
        default->throw new AssertionError(method.getName());
    });
    private Player result=player;
    private final Server server=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class[]{Server.class},(proxy,method,args)->{
        assertEquals("getPlayer",method.getName());assertEquals(id,args[0]);return result;
    });
    @Test void offlineWithPendingResetIsEligibleWithoutReadingStaleDeathStateOrClearingDebt() {
        pending.markResetPending(id);dead=true;result=null;
        assertTrue(RoleChangeAvailability.available(server,pending,id));
        result=player;online=false;
        assertTrue(RoleChangeAvailability.available(server,pending,id));assertEquals(0,deathReads);assertTrue(pending.needsReset(id));
    }
    @Test void onlinePlayersStillMustBeAliveAndHaveNoPendingReset() {
        assertTrue(RoleChangeAvailability.available(server,pending,id));
        dead=true;assertFalse(RoleChangeAvailability.available(server,pending,id));
        dead=false;pending.markResetPending(id);assertFalse(RoleChangeAvailability.available(server,pending,id));
        pending.completeReset(id);assertTrue(RoleChangeAvailability.available(server,pending,id));
    }
}
