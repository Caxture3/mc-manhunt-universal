package com.caxture.manhuntuniversal.extension.tracking;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.api.tracking.*;
import com.caxture.manhuntuniversal.extension.OwnedRegistry;
import java.util.*;
import java.util.function.*;

/** Owned override state. Match, directory and per-viewer generations are distinct guards. */
public final class TrackingControlService implements TrackingControl {
    public record Binding(ExtensionRegistration owner, TrackingOverrideRequest request, long revision) { }
    private final OwnedRegistry owned;
    private final Supplier<Optional<MatchSnapshot>> match;
    private final Predicate<UUID> eligible;
    private final Predicate<String> targetExists;
    private final LongSupplier directory;
    private final Consumer<UUID> changed;
    private final Map<UUID,Binding> bindings=new HashMap<>();
    private final Map<UUID,Long> revisions=new HashMap<>();
    private final Map<UUID,ExtensionHandle> handles=new HashMap<>();
    private UUID session;
    public TrackingControlService(OwnedRegistry owned, Supplier<Optional<MatchSnapshot>> match, Predicate<UUID> eligible,
                                  Predicate<String> targetExists, LongSupplier directory, Consumer<UUID> changed) {
        this.owned=owned;this.match=match;this.eligible=eligible;this.targetExists=targetExists;this.directory=directory;this.changed=changed;
    }
    @Override public Optional<TrackingControlSnapshot> snapshot(UUID player) {
        owned.requireThread();var current=match.get().orElse(null);
        if(current==null || !current.participants().containsKey(player))return Optional.empty();
        synchronize(current.id());var binding=binding(player).orElse(null);var request=binding==null?TrackingOverrideRequest.defaults():binding.request;
        return Optional.of(new TrackingControlSnapshot(current.id(),player,revisions.getOrDefault(player,0L),request.mode(),request.targetId(),request.playerId(),
                Optional.ofNullable(binding).map(value->value.owner.id())));
    }
    @Override public ExtensionResult<ExtensionHandle> apply(TrackingControlMutation mutation,TrackingOverrideRequest request) {
        if(mutation==null||request==null)return rejected(OperationStatus.INVALID_ARGUMENT,"Mutation and request required");
        var context=mutation.tracking().context();var player=mutation.tracking().playerId();
        return owned.execute(context.extension(),()->{
            if(!context.extension().controls().contains(ControlDomain.TRACKING))return rejected(OperationStatus.CONTROL_CONFLICT,"TRACKING ownership required");
            var current=match.get().orElse(null);
            if(current==null)return rejected(OperationStatus.NO_ACTIVE_MATCH,"No active match");
            if(!current.id().equals(context.sessionId()))return rejected(OperationStatus.STALE_SESSION,"Match changed");
            synchronize(current.id());
            if(context.expectedRevision()!=current.revision() || mutation.expectedControlRevision()!=revisions.getOrDefault(player,0L)
                    || mutation.tracking().expectedDirectoryRevision()>=0 && mutation.tracking().expectedDirectoryRevision()!=directory.getAsLong())
                return rejected(OperationStatus.STALE_REVISION,"Match, directory or tracking control changed");
            var participant=current.participants().get(player);
            if(current.state()!=MatchState.RUNNING)return rejected(OperationStatus.INVALID_STATE,"Tracking requires a running match");
            if(participant==null||participant.eliminated())return rejected(OperationStatus.PLAYER_UNAVAILABLE,"Viewer unavailable");
            boolean available=eligible.test(player);
            // Feature callbacks may disable their plugin or synchronously change the core match.
            if(!owned.valid(context.extension()))return rejected(OperationStatus.UNREGISTERED_EXTENSION,"Owner closed during eligibility check");
            var after=match.get().orElse(null);
            if(after==null||!after.id().equals(current.id()))return rejected(OperationStatus.STALE_SESSION,"Match changed during eligibility check");
            if(after.revision()!=context.expectedRevision()||after.state()!=MatchState.RUNNING||mutation.expectedControlRevision()!=revisions.getOrDefault(player,0L)
                    || mutation.tracking().expectedDirectoryRevision()>=0&&mutation.tracking().expectedDirectoryRevision()!=directory.getAsLong())
                return rejected(OperationStatus.STALE_REVISION,"State changed during eligibility check");
            if(!available)return rejected(OperationStatus.PLAYER_UNAVAILABLE,"Viewer unavailable");
            if(request.targetId().filter(id->!targetExists.test(id)).isPresent())return rejected(OperationStatus.INVALID_ARGUMENT,"Unknown target ID");
            // Remove the old binding first: disposing its handle must not advance this replacement's revision.
            bindings.remove(player);ExtensionHandle old=handles.remove(player);if(old!=null)owned.retire(old);
            long revision=revisions.merge(player,1L,Long::sum);
            if(request.mode()==TrackingOverrideMode.DEFAULT){owned.callback(()->changed.accept(player));return ExtensionResult.ok(new ExtensionHandle(){public boolean active(){owned.requireThread();return false;}public void close(){owned.requireThread();}});}
            var binding=new Binding(context.extension(),request,revision);bindings.put(player,binding);
            var handle=owned.add(context.extension(),()->bindings.get(player)==binding,()->{
                if(bindings.remove(player,binding)){handles.remove(player);revisions.merge(player,1L,Long::sum);changed.accept(player);}
            });handles.put(player,handle);owned.callback(()->changed.accept(player));return ExtensionResult.ok(handle);
        });
    }
    public Optional<Binding> binding(UUID player){owned.requireThread();var value=bindings.get(player);return value!=null&&owned.valid(value.owner)?Optional.of(value):Optional.empty();}
    public void clearPlayer(UUID player){owned.requireThread();if(bindings.remove(player)!=null){revisions.merge(player,1L,Long::sum);
        var handle=handles.remove(player);if(handle!=null)owned.retire(handle);owned.callback(()->changed.accept(player));}}
    public void clear(){owned.requireThread();for(UUID player:List.copyOf(bindings.keySet()))clearPlayer(player);}
    public void catalogChanged(){owned.requireThread();for(var entry:List.copyOf(bindings.entrySet()))if(entry.getValue().request.targetId().filter(id->!targetExists.test(id)).isPresent())clearPlayer(entry.getKey());}
    public Optional<PlayerTrackingCandidate> resolvePlayer(UUID player, PlayerTrackingContext context) {
        var before=binding(player).orElse(null);if(before==null)return Optional.empty();
        var request=before.request;if(request.playerId().isEmpty()&&request.selector().isEmpty())return Optional.empty();
        Optional<UUID> selected;
        try { selected=request.playerId().isPresent()?request.playerId():Objects.requireNonNull(owned.invoke(()->request.selector().orElseThrow().select(context))); }
        catch(RuntimeException|LinkageError error){owned.report(error);return Optional.empty();}
        if(binding(player).orElse(null)!=before)return Optional.empty();
        return selected.flatMap(id->context.candidates().stream().filter(candidate->candidate.playerId().equals(id)).findFirst());
    }
    public <T> T invoke(Supplier<T> action){return owned.invoke(action);}
    public void report(Throwable error){owned.report(error);}
    private void synchronize(UUID current){if(!current.equals(session)){clear();revisions.clear();session=current;}}
    private static <T> ExtensionResult<T> rejected(OperationStatus status,String detail){return ExtensionResult.rejected(status,detail);}
}
