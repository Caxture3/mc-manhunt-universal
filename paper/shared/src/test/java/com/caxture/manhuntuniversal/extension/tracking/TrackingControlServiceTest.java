package com.caxture.manhuntuniversal.extension.tracking;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackingControlServiceTest {
    final UUID session=UUID.randomUUID(), player=UUID.randomUUID();
    TeamRole role=TeamRole.HUNTER; boolean eliminated; long matchRevision=4;
    final ExtensionRegistration owner=new ExtensionRegistration(){
        public String id(){return "test:tasks";} public Set<ControlDomain> controls(){return Set.of(ControlDomain.TRACKING);}
        public boolean active(){return true;} public void close(){}
    };
    final OwnedRegistry owned=new OwnedRegistry(()->{},()->null, token->token==owner, error->fail(error));
    final List<UUID> changed=new ArrayList<>();
    Optional<MatchSnapshot> match(){return Optional.of(new MatchSnapshot(session,matchRevision,MatchState.RUNNING,Instant.EPOCH,Optional.of(Instant.EPOCH),
            Map.of(player,new ParticipantSnapshot(player,"Alex",role,eliminated)),Optional.empty()));}
    final TrackingControlService service=new TrackingControlService(owned,this::match,id->true,id->id.equals("test:hidden"),()->8,changed::add);
    TrackingControlMutation mutation(long rev){return new TrackingControlMutation(new TrackingMutation(new MutationContext(owner,session,matchRevision,"task"),player,8),rev);}
    @Test void bothRolesCanForceHiddenTargetsAndOldHandlesCannotClearReplacement(){
        var old=service.apply(mutation(0),TrackingOverrideRequest.target("test:hidden")).value().orElseThrow();
        assertEquals(TrackingOverrideMode.TARGET,service.snapshot(player).orElseThrow().mode());
        role=TeamRole.RUNNER;
        var next=service.apply(mutation(1),TrackingOverrideRequest.player(UUID.randomUUID())).value().orElseThrow();
        old.close(); assertTrue(next.active()); assertEquals(2,service.snapshot(player).orElseThrow().revision());
        next.close(); assertEquals(TrackingOverrideMode.DEFAULT,service.snapshot(player).orElseThrow().mode());
        assertEquals(3,service.snapshot(player).orElseThrow().revision());
    }
    @Test void staleWritesAndMissingOwnershipAreRejectedWithoutChangingState(){
        service.apply(mutation(0),TrackingOverrideRequest.disabled());
        assertEquals(OperationStatus.STALE_REVISION,service.apply(mutation(0),TrackingOverrideRequest.defaults()).status());
        var stale=new TrackingControlMutation(new TrackingMutation(new MutationContext(owner,UUID.randomUUID(),4,"old"),player),1);
        assertEquals(OperationStatus.STALE_SESSION,service.apply(stale,TrackingOverrideRequest.defaults()).status());
        assertEquals(OperationStatus.STALE_REVISION,service.apply(new TrackingControlMutation(new TrackingMutation(new MutationContext(owner,session,3,"old"),player),1),TrackingOverrideRequest.defaults()).status());
        eliminated=true; assertEquals(OperationStatus.PLAYER_UNAVAILABLE,service.apply(mutation(1),TrackingOverrideRequest.defaults()).status());
    }
    @Test void lifecycleAndOwnerCleanupAdvanceRevisionAndPreventOldTaskReuse(){
        var handle=service.apply(mutation(0),TrackingOverrideRequest.disabled()).value().orElseThrow();
        service.clearPlayer(player); assertFalse(handle.active());
        assertEquals(OperationStatus.STALE_REVISION,service.apply(mutation(0),TrackingOverrideRequest.disabled()).status());
        service.apply(mutation(2),TrackingOverrideRequest.disabled()); // clear is revision 2 only after a new apply below
        var restored=service.apply(mutation(2),TrackingOverrideRequest.player(UUID.randomUUID()));
        // A failed stale write does not advance the revision.
        assertEquals(OperationStatus.STALE_REVISION,restored.status());
        owned.closeOwner(owner); assertEquals(TrackingOverrideMode.DEFAULT,service.snapshot(player).orElseThrow().mode());
    }
    @Test void callbacksCannotMutateTrackingAndInvalidCatalogIdsAreRejected(){
        assertEquals(OperationStatus.INVALID_ARGUMENT,service.apply(mutation(0),TrackingOverrideRequest.target("test:missing")).status());
        assertEquals(OperationStatus.BUSY,owned.invoke(()->service.apply(mutation(0),TrackingOverrideRequest.disabled())).status());
    }
    @Test void selectorsUseSuppliedImmutableCandidatesAndCanChangeWithoutMatchRevisionChanges(){
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),world=UUID.randomUUID();
        var point=new WorldPosition(world,0,70,0);
        var context=new PlayerTrackingContext(new TrackingViewerContext(player,"en_us",point,"overworld",match(),Instant.EPOCH),
                List.of(new PlayerTrackingCandidate(first,"Visitor",false,Optional.empty(),point),new PlayerTrackingCandidate(second,"Hunter",true,Optional.of(TeamRole.HUNTER),point)));
        UUID[] desired={first};
        service.apply(mutation(0),TrackingOverrideRequest.players(input->{
            assertEquals(OperationStatus.BUSY,service.apply(mutation(1),TrackingOverrideRequest.disabled()).status());return Optional.of(desired[0]);}));
        assertEquals(first,service.resolvePlayer(player,context).orElseThrow().playerId());
        desired[0]=second;assertEquals(second,service.resolvePlayer(player,context).orElseThrow().playerId());
        desired[0]=UUID.randomUUID();assertTrue(service.resolvePlayer(player,context).isEmpty());
        assertEquals(1,service.snapshot(player).orElseThrow().revision());
    }
    @Test void fixedUnknownPlayersAndSelectorFailuresHaveNoFallbackAndClosingOwnerCleansState(){
        var point=new WorldPosition(UUID.randomUUID(),0,70,0);
        var context=new PlayerTrackingContext(new TrackingViewerContext(player,"en_us",point,"overworld",match(),Instant.EPOCH),List.of());
        service.apply(mutation(0),TrackingOverrideRequest.player(UUID.randomUUID()));assertTrue(service.resolvePlayer(player,context).isEmpty());
        owned.closeOwner(owner);assertTrue(service.binding(player).isEmpty());assertEquals(2,service.snapshot(player).orElseThrow().revision());
        var errors=new ArrayList<RuntimeException>();var guarded=new OwnedRegistry(()->{},()->null,token->token==owner,errors::add);
        var other=new TrackingControlService(guarded,this::match,id->true,id->true,()->8,id->{});
        other.apply(mutation(0),TrackingOverrideRequest.players(input->{throw new IllegalStateException("broken selector");}));
        assertTrue(other.resolvePlayer(player,context).isEmpty());assertEquals(1,errors.size());
    }
    @Test void invalidTokensAndTokensWithoutTrackingDomainCannotWrite(){
        ExtensionRegistration missing=new ExtensionRegistration(){public String id(){return "no:domain";}public Set<ControlDomain> controls(){return Set.of();}public boolean active(){return true;}public void close(){}};
        var guarded=new OwnedRegistry(()->{},()->null,token->token==missing,error->fail(error));
        var other=new TrackingControlService(guarded,this::match,id->true,id->true,()->8,id->{});
        var denied=new TrackingControlMutation(new TrackingMutation(new MutationContext(missing,session,4,"test"),player),0);
        assertEquals(OperationStatus.CONTROL_CONFLICT,other.apply(denied,TrackingOverrideRequest.disabled()).status());
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION,other.apply(mutation(0),TrackingOverrideRequest.disabled()).status());
    }
    @Test void ownerClosingInsideEligibilityCallbackRejectsBeforeChangingRevisionOrBinding(){
        boolean[] registered={true};var guarded=new OwnedRegistry(()->{},()->null,token->token==owner&&registered[0],error->fail(error));
        var other=new TrackingControlService(guarded,this::match,id->{registered[0]=false;return true;},id->true,()->8,id->{});
        assertEquals(OperationStatus.UNREGISTERED_EXTENSION,other.apply(mutation(0),TrackingOverrideRequest.disabled()).status());
        assertEquals(0,other.snapshot(player).orElseThrow().revision());assertTrue(other.binding(player).isEmpty());
    }
}
