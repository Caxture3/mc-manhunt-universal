package compatibility;
import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import java.util.*;
/** Compiled against the shipped 1.1.0 SDK, not rebuilt with the current SDK. */
public final class LegacyTrackingConsumer implements TrackingRegistry {
    public List<TrackingTargetInfo> list(){return List.of();}
    public Optional<TrackingMenuSnapshot> menu(UUID id){return Optional.empty();}
    public Optional<TrackingSnapshot> snapshot(UUID id){return Optional.empty();}
    public ExtensionResult<ExtensionHandle> register(ExtensionRegistration owner,TrackingTargetDefinition value){return ExtensionResult.ok(null);}
    public ExtensionResult<ExtensionHandle> replaceLocator(ExtensionRegistration owner,String id,int priority,TrackingLocator locator){return ExtensionResult.ok(null);}
    public ExtensionResult<ExtensionHandle> availability(ExtensionRegistration owner,String id,int priority,TrackingAvailabilityPolicy policy){return ExtensionResult.ok(null);}
    public ExtensionResult<TrackingSnapshot> select(TrackingMutation mutation,String id){return ExtensionResult.ok(null);}
    public ExtensionResult<TrackingSnapshot> clear(TrackingMutation mutation){return ExtensionResult.ok(null);}
    public ExtensionResult<TrackingSnapshot> refresh(TrackingMutation mutation){return ExtensionResult.ok(null);}
    public ExtensionResult<Void> invalidate(ExtensionRegistration owner,TrackingInvalidation scope){return ExtensionResult.ok(null);}
    public static boolean probe(){TrackingRegistry instance=new LegacyTrackingConsumer();return instance.list().isEmpty()&&instance.menu(UUID.randomUUID()).isEmpty();}
}
