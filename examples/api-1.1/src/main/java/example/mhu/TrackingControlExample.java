package example.mhu;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import java.util.*;

/** Compile-checked API 1.2 recipes. Owner must have exclusive ControlDomain.TRACKING. */
public final class TrackingControlExample {
    private final ManhuntUniversalApi api;
    private final ExtensionRegistration owner;
    public TrackingControlExample(ManhuntUniversalApi api,ExtensionRegistration owner){this.api=api;this.owner=owner;}
    public ExtensionHandle structure(UUID participant){return apply(participant,TrackingOverrideRequest.target("core:village"));}
    public ExtensionHandle specificPlayer(UUID participant,UUID target){return apply(participant,TrackingOverrideRequest.player(target));}
    public ExtensionHandle nearestHunter(UUID participant){return apply(participant,TrackingOverrideRequest.players(context->{
        WorldPosition viewer=context.viewer().position();
        return context.candidates().stream().filter(candidate->candidate.online()&&candidate.role().orElse(null)==TeamRole.HUNTER)
                .filter(candidate->!candidate.playerId().equals(participant)&&candidate.position().worldId().equals(viewer.worldId()))
                .min(Comparator.comparingDouble(candidate->distanceSquared(viewer,candidate.position())))
                .map(PlayerTrackingCandidate::playerId);
    }));}
    public ExtensionHandle pause(UUID participant){return apply(participant,TrackingOverrideRequest.disabled());}
    public void restore(UUID participant){apply(participant,TrackingOverrideRequest.defaults()).close();}
    /** Capture when accepting new task intent. Keep it with queued work; never refresh it to let old work win. */
    public TrackingControlMutation capture(UUID player){
        var match=api.matches().current().orElseThrow();var control=api.tracking().control().snapshot(player).orElseThrow();
        if(!match.id().equals(control.sessionId()))throw new IllegalStateException("Match changed");
        return new TrackingControlMutation(new TrackingMutation(new MutationContext(owner,match.id(),match.revision(),"example task"),player),control.revision());
    }
    public ExtensionResult<ExtensionHandle> commitCaptured(TrackingControlMutation captured,TrackingOverrideRequest request){
        return api.tracking().control().apply(captured,request);
    }
    private ExtensionHandle apply(UUID player,TrackingOverrideRequest request){
        var result=commitCaptured(capture(player),request);
        if(!result.successful())throw new IllegalStateException(result.status()+": "+result.message());return result.value().orElseThrow();
    }
    /** Task completion can simply close its original handle; an old handle cannot affect a replacement. */
    public void completed(ExtensionHandle taskHandle){taskHandle.close();}
    private static double distanceSquared(WorldPosition a,WorldPosition b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}
}
