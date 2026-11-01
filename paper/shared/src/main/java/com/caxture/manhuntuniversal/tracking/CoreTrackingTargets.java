package com.caxture.manhuntuniversal.tracking;

import com.caxture.manhuntuniversal.api.LocalizedText;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.config.IntConfigKey;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

/** Shared native catalog metadata; locating remains in the existing structure engine. */
final class CoreTrackingTargets {
    private CoreTrackingTargets() { }
    static List<TrackingTargetId> ids() { return List.of(TrackingTargetId.VILLAGE,TrackingTargetId.NETHER_FORTRESS,TrackingTargetId.BASTION_REMNANT,TrackingTargetId.TRIAL_CHAMBERS); }
    static String id(TrackingTargetId target) { return "core:"+target.name().toLowerCase(Locale.ROOT); }
    static IntConfigKey unlockKey(TrackingTargetId target) {
        return switch(target){
            case VILLAGE->IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_VILLAGE;
            case NETHER_FORTRESS->IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_NETHER_FORTRESS;
            case BASTION_REMNANT->IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_BASTION_REMNANT;
            case TRIAL_CHAMBERS->IntConfigKey.RUNNER_STRUCTURE_TRACKER_UNLOCK_TIME_TRIAL_CHAMBERS;
            case NONE->throw new IllegalArgumentException("NONE is not a catalog target");
        };
    }
    static TrackingTargetDefinition definition(TrackingTargetId target,LocalizedText name,TrackingAvailabilityPolicy availability) {
        TrackingAppearance.Theme theme=switch(target){
            case VILLAGE->TrackingAppearance.Theme.VILLAGE;case NETHER_FORTRESS->TrackingAppearance.Theme.FORTRESS;
            case BASTION_REMNANT->TrackingAppearance.Theme.BASTION;case TRIAL_CHAMBERS->TrackingAppearance.Theme.GENERIC;
            case NONE->throw new IllegalArgumentException("NONE is not a catalog target");
        };
        Optional<String> texture=target==TrackingTargetId.TRIAL_CHAMBERS
                ?Optional.of("randomanhunt:textures/background/compass_selector/trial_chambers.png"):Optional.empty();
        return new TrackingTargetDefinition(id(target),name,LocalizedText.of(target.name().replace('_',' ')),LocalizedText.of(""),target.ordinal(),
                target.targetWorldKind().orElseThrow()==TrackingWorldKind.OVERWORLD?TrackingWorld.overworld():TrackingWorld.nether(),
                new TrackingAppearance(theme,0xff303840,texture,Optional.empty()),availability,
                (request,cancel)->CompletableFuture.completedFuture(LocateResult.core()),true,Optional.empty());
    }
    static TargetAvailability availability(TrackingTargetId target,boolean compassEnabled,ToIntFunction<IntConfigKey> configuration,TrackingViewerContext viewer) {
        if(!compassEnabled)return TargetAvailability.hidden();
        long remaining=StructureUnlockPolicy.remainingMillis(configuration.applyAsInt(unlockKey(target)),viewer.match().flatMap(com.caxture.manhuntuniversal.api.MatchSnapshot::startedAt).orElse(viewer.now()),viewer.now());
        return remaining<0?TargetAvailability.hidden():remaining==0?TargetAvailability.available():TargetAvailability.locked(remaining,"");
    }
}
