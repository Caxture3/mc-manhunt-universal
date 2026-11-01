package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import com.caxture.manhuntuniversal.extension.tracking.TrackingRegistryService;
import com.caxture.manhuntuniversal.client.DynamicTrackingMessages;
import com.caxture.manhuntuniversal.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class CoreTrackingTargetsTest {
    private static final Instant START=Instant.parse("2026-11-01T00:00:00Z");
    private final UUID player=UUID.randomUUID(),world=UUID.randomUUID(),session=UUID.randomUUID();
    private Instant now=START;
    private boolean enabled=true;
    private ConfigurationValues settings=ConfigurationValues.defaults();
    private String selected;
    private final ExtensionRegistration owner=new ExtensionRegistration(){public String id(){return "test:trial";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}};
    private final OwnedRegistry owned=new OwnedRegistry(()->{},()->null,token->token==owner,error->fail(error));
    private final TrackingRegistryService.Runtime runtime=new TrackingRegistryService.Runtime(){
        public Optional<TrackingViewerContext> viewer(UUID id){return Optional.of(context());}
        public boolean maySelect(UUID id){return true;}
        public Optional<String> selection(UUID id){return Optional.ofNullable(selected);}
        public void selection(UUID id,String target){selected=target;}
        public Optional<TrackingSnapshot> snapshot(UUID id){return Optional.empty();}
        public void refresh(UUID id){}
        public void invalidate(TrackingInvalidation scope){}
        public void catalogChanged(){}
    };
    private final TrackingRegistryService registry=new TrackingRegistryService(owned,runtime);
    private TrackingViewerContext context(){
        var match=new MatchSnapshot(session,1,MatchState.RUNNING,START,Optional.of(START),Map.of(player,new ParticipantSnapshot(player,"Alex",TeamRole.RUNNER,false)),Optional.empty());
        return new TrackingViewerContext(player,"en_us",new WorldPosition(world,0,70,0),"overworld",Optional.of(match),now);
    }
    private void setTimes(RunnerStructureUnlockTimes times) {
        settings=settings.with(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE,times.village())
                .with(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_NETHER_FORTRESS,times.netherFortress())
                .with(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_BASTION_REMNANT,times.bastionRemnant())
                .with(IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_TRIAL_CHAMBERS,times.trialChambers());
    }
    private void install(){for(var target:CoreTrackingTargets.ids())registry.registerCore(CoreTrackingTargets.definition(target,
            target==TrackingTargetId.TRIAL_CHAMBERS?LocalizedText.bilingual("Trial Chambers","试炼密室"):LocalizedText.of(target.id()),
            viewer->CoreTrackingTargets.availability(target,enabled,settings::get,viewer)));}
    @Test void trialIsAFourthOverworldCoreTargetWithAnExplicitTextureAndNativeDelegation() throws Exception {
        install();assertEquals(4,registry.list().size());
        var definition=registry.definition("core:trial_chambers").orElseThrow();
        assertEquals(TrackingWorld.overworld(),definition.world());assertEquals("试炼密室",definition.name().resolve("zh_cn"));
        assertEquals(TrackingAppearance.Theme.GENERIC,definition.appearance().theme());
        assertEquals("randomanhunt:textures/background/compass_selector/trial_chambers.png",definition.appearance().texture().orElseThrow());
        assertTrue(definition.fallbackToCore());assertFalse(registry.customLocator("core:trial_chambers"));
    }
    @Test void defaultMenuRemainsThreeOptionsAndDisablingAllFourLeavesNoOptions() {
        install();var menu=registry.menu(player).orElseThrow();assertEquals(3,menu.options().size());assertTrue(menu.options().stream().noneMatch(option->option.id().equals("core:trial_chambers")));
        setTimes(new RunnerStructureUnlockTimes(-1,-1,-1,-1));assertTrue(registry.menu(player).orElseThrow().options().isEmpty());
        setTimes(new RunnerStructureUnlockTimes(-1,-1,-1,0));assertEquals(List.of("core:trial_chambers"),registry.menu(player).orElseThrow().options().stream().map(TrackingOptionSnapshot::id).toList());
        enabled=false;assertTrue(registry.menu(player).orElseThrow().options().isEmpty());
    }
    @Test void trialUnlockUsesFormalStartAndRejectsSelectionUntilTheDeadline() {
        install();setTimes(new RunnerStructureUnlockTimes(0,0,0,120));now=START.plusMillis(119999);
        var mutation=new TrackingMutation(new MutationContext(owner,session,1,"test"),player,-1);
        assertFalse(registry.select(mutation,"core:trial_chambers").successful());assertNull(selected);
        var option=registry.menu(player).orElseThrow().options().stream().filter(value->value.id().equals("core:trial_chambers")).findFirst().orElseThrow();assertEquals(1,option.availability().remainingMillis());
        var frame=DynamicTrackingMessages.open(session,1,registry.menu(player).orElseThrow());assertEquals(frame,ProtocolCodec.decode(ProtocolCodec.encode(frame)));
        now=START.plusSeconds(120);assertTrue(registry.select(mutation,"core:trial_chambers").successful());assertEquals("core:trial_chambers",selected);
        setTimes(new RunnerStructureUnlockTimes(0,0,0,-1));registry.menu(player);assertNull(selected);
    }
    @Test void fourBuiltinsLeaveTwentyEightExtensionSlotsInsideTheSameCatalogLimit() {
        install();for(int index=0;index<28;index++)assertTrue(registry.register(owner,new TrackingTargetDefinition("test:target"+index,LocalizedText.of("Target"),TrackingWorld.current(),(request,cancel)->CompletableFuture.completedFuture(LocateResult.notFound("test")))).successful());
        assertEquals(32,registry.list().size());assertFalse(registry.register(owner,new TrackingTargetDefinition("test:extra",LocalizedText.of("Extra"),TrackingWorld.current(),(request,cancel)->CompletableFuture.completedFuture(LocateResult.notFound("test")))).successful());
    }
}
