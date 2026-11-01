package com.caxture.manhuntuniversal.fabric.ui;
import com.caxture.manhuntuniversal.protocol.TrackingTargetDescriptor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectorResourceFallbackTest {
    @Test void trialUsesTheBundledTextureOrASelectableGenericFallback() {
        String texture="randomanhunt:textures/background/compass_selector/trial_chambers.png";
        var trial=new TrackingTargetDescriptor("core:trial_chambers","试炼密室","TRIAL CHAMBERS","",3,"generic",0xff303840,Optional.of(texture),Optional.empty(),0,true,"");
        assertEquals(texture,SelectorResourceStyle.texture(trial,texture::equals).orElseThrow());
        assertTrue(SelectorResourceStyle.texture(trial,ignored->false).isEmpty());assertTrue(trial.selectable());
    }
    @Test void resourcePackCanDisappearWithoutMakingATargetUnusable() {
        var option = new TrackingTargetDescriptor("test:a","A","","",0,"village",0,Optional.of("test:custom.png"),Optional.empty(),0,true,"");
        Set<String> present = new HashSet<>(Set.of("test:custom.png", "randomanhunt:textures/background/compass_selector/village.png"));
        assertEquals("test:custom.png",SelectorResourceStyle.texture(option,present::contains).orElseThrow());
        present.remove("test:custom.png"); assertTrue(SelectorResourceStyle.texture(option,present::contains).orElseThrow().contains("village.png"));
        present.clear(); assertTrue(SelectorResourceStyle.texture(option,present::contains).isEmpty()); assertTrue(option.selectable());
    }
}
