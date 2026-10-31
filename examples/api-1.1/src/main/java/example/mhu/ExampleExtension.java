package example.mhu;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.config.*;
import com.caxture.manhuntuniversal.api.feature.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.api.paper.PaperManhuntUniversalApi;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Compile-checked demonstration. The example's setting is intentionally memory-only. */
public final class ExampleExtension extends JavaPlugin {
    private PaperManhuntUniversalApi api;
    private ExtensionRegistration registration;
    private int checkpointX=100;
    @Override public void onEnable(){
        api=getServer().getServicesManager().load(PaperManhuntUniversalApi.class);
        if(api==null||!api.apiVersion().startsWith("1.")||!api.capabilities().containsAll(Set.of(
                Capability.CONFIGURATION_REGISTRY,Capability.DYNAMIC_TRACKING,Capability.PRESENTATION_CONTROL))){
            getLogger().severe("Manhunt Universal API 1.1 capabilities unavailable");getServer().getPluginManager().disablePlugin(this);return;
        }
        var result=api.register(this,new ExtensionOptions("example:checkpoint",Set.of()));
        if(result.status()!=OperationStatus.OK)throw new IllegalStateException(result.message());
        registration=result.registration().orElseThrow();
        require(api.configuration().register(registration,new ConfigDefinition<>("example:checkpoint-x",
                LocalizedText.bilingual("Checkpoint X","检查点 X 坐标"),LocalizedText.bilingual("Memory-only demonstration setting","示例配置只保存在内存"),100,
                ConfigTypes.integers(-1000000,1000000),new ConfigStorage<Integer>(){
                    public Integer read(){return checkpointX;}public boolean save(Integer value){checkpointX=value;return true;}
                },ConfigTiming.IMMEDIATE,"blocks")));
        require(api.tracking().register(registration,new TrackingTargetDefinition("example:checkpoint",
                LocalizedText.bilingual("Checkpoint","检查点"),TrackingWorld.current(),(request,cancellation)->{
                    int x=checkpointX; // provider runs on the main thread; capture before any background work
                    return CompletableFuture.completedFuture(LocateResult.found(new WorldPosition(request.origin().worldId(),x,request.origin().y(),request.origin().z()),"example:checkpoint"));
                })));
        require(api.configuration().listen(registration,change->{
            if(change.keys().contains("example:checkpoint-x"))getServer().getScheduler().runTask(this,()->{
                if(registration!=null&&registration.active())api.tracking().invalidate(registration,TrackingInvalidation.target("example:checkpoint"));
            });
        }));
        require(api.texts().override(registration,"prefix",10,context->context.locale().startsWith("zh")?"§6[检查点追杀] §r":"§6[Checkpoint Manhunt] §r"));
        require(api.presentation().appendResult(registration,new ResultSection("example:checkpoint-result",10,false,
                context->List.of(Component.text("Checkpoint X: "+checkpointX)))));
        require(api.presentation().identity(registration,10,false,context->{
            var base=context.fallback();return new ObserverIdentityStyle(base.tabName(),base.tabVisible(),base.tabOrder(),
                    Component.text("[Demo] ").append(base.namePrefix()),base.nameSuffix(),base.nameColor(),base.nameVisibility());
        }));
        // To replace a core effect, register a policy returning SKIP_CORE, then implement the replacement in your own Paper listener.
        require(api.features().policy(registration,CoreFeatureIds.BLAZE_ROD_LOOTING,10,context->FeatureDecision.DEFAULT));
    }
    private static void require(ExtensionResult<?> result){if(!result.successful())throw new IllegalStateException(result.status()+": "+result.message());}
    @Override public void onDisable(){if(registration!=null){registration.close();registration=null;}}
}
