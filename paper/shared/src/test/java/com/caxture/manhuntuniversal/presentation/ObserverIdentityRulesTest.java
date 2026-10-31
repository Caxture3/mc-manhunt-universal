package com.caxture.manhuntuniversal.presentation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ObserverIdentityRulesTest {
    public static final class Rules {
        boolean friendly,see; String collision="ALWAYS",death="ALWAYS",name="ALWAYS";
        public boolean isAllowFriendlyFire(){return friendly;} public void setAllowFriendlyFire(boolean value){friendly=value;}
        public boolean canSeeFriendlyInvisibles(){return see;} public void setSeeFriendlyInvisibles(boolean value){see=value;}
        public String getCollisionRule(){return collision;} public void setCollisionRule(String value){collision=value;}
        public String getDeathMessageVisibility(){return death;} public void setDeathMessageVisibility(String value){death=value;}
        public String getNameTagVisibility(){return name;} public void setNameTagVisibility(String value){name=value;}
    }
    @Test void removedOriginalTeamAndClearedOverrideRestoreEveryNativeDefault() throws Exception {
        Rules target=new Rules();target.name="NEVER";target.collision="NEVER";target.friendly=true;
        ObserverIdentityPacketSupport.copyRules(target,null,new Rules(),Rules.class);
        assertEquals("ALWAYS",target.name);assertEquals("ALWAYS",target.collision);assertFalse(target.friendly);
        Rules original=new Rules();original.name="HIDE_FOR_OWN_TEAM";
        ObserverIdentityPacketSupport.copyRules(target,original,new Rules(),Rules.class);assertEquals(original.name,target.name);
    }
    @Test void ownFailClosedConcealmentCanRecoverAndReapplyAfterShow() {
        List<String> calls=new ArrayList<>();
        ObserverIdentityRecovery.apply(true,()->calls.add("apply"),()->calls.add("show"));
        assertEquals(List.of("apply","show","apply"),calls);
        calls.clear();assertThrows(IllegalStateException.class,()->ObserverIdentityRecovery.apply(true,()->{throw new IllegalStateException();},()->calls.add("show")));
        assertTrue(calls.isEmpty());
    }
}
