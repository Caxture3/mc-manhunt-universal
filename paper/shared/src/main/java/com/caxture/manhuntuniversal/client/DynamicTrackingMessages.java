package com.caxture.manhuntuniversal.client;
import com.caxture.manhuntuniversal.api.tracking.TrackingMenuSnapshot;
import com.caxture.manhuntuniversal.protocol.*;
import java.util.*;
/** Internal API→wire mapping, keeping wire DTOs out of the extension SDK. */
public final class DynamicTrackingMessages {
    private DynamicTrackingMessages() { }
    public static Optional<DynamicTrackingOpen> tryOpen(UUID session,long authorization,TrackingMenuSnapshot menu){
        try{return Optional.of(open(session,authorization,menu));}catch(IllegalArgumentException error){report(error);return Optional.empty();}
    }
    public static Optional<DynamicTrackingUpdate> tryUpdate(UUID session,long authorization,TrackingMenuSnapshot menu){
        try{return Optional.of(update(session,authorization,menu));}catch(IllegalArgumentException error){report(error);return Optional.empty();}
    }
    private static void report(IllegalArgumentException error){java.util.logging.Logger.getLogger("ManhuntUniversal").warning("Dynamic menu exceeds wire limits; using Paper Dialog: "+error.getMessage());}
    public static DynamicTrackingOpen open(UUID session, long authorization, TrackingMenuSnapshot menu) {
        return new DynamicTrackingOpen(ProtocolConstants.VERSION, session, authorization, menu.revision(), menu.selection(), options(menu));
    }
    public static DynamicTrackingUpdate update(UUID session, long authorization, TrackingMenuSnapshot menu) {
        return new DynamicTrackingUpdate(ProtocolConstants.VERSION, session, authorization, menu.revision(), menu.selection(), options(menu));
    }
    private static List<TrackingTargetDescriptor> options(TrackingMenuSnapshot menu) {
        return menu.options().stream().filter(option -> option.availability().visible()).map(option -> new TrackingTargetDescriptor(
                option.id(), option.name(), option.secondaryName(), option.description(), option.order(),
                option.appearance().theme().name().toLowerCase(Locale.ROOT), option.appearance().color(),
                option.appearance().texture(), option.appearance().itemIcon(), Math.max(0, option.availability().remainingMillis()),
                option.availability().selectable(), option.availability().reason())).toList();
    }
}
