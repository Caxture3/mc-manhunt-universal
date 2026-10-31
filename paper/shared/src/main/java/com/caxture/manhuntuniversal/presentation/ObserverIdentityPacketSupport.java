package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.api.paper.presentation.ObserverIdentityStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;

/** Shared packet construction; native color representation is supplied by each version adapter. */
abstract class ObserverIdentityPacketSupport {
    private record Pair(UUID observer, UUID target) { }
    private record TeamState(Object nativeTeam, String realName) { }
    private final ObserverTeamStore<Pair, TeamState> teams = new ObserverTeamStore<>();
    private final Map<UUID, NetworkView> views = new HashMap<>();
    private long sequence;
    protected abstract void color(Object nativeTeam, NamedTextColor color) throws ReflectiveOperationException;
    final void apply(Player observer, Player target, ObserverIdentityStyle style) {
        try {
            Bindings nms = Lazy.INSTANCE;
            Pair key = new Pair(observer.getUniqueId(), target.getUniqueId());
            TeamState state = teams.get(key);
            if (state == null) {
                String name;
                do { name = "mh" + Long.toUnsignedString(++sequence, 36); } while (observer.getScoreboard().getTeam(name) != null);
                state = new TeamState(nms.team.newInstance(nms.scoreboard.newInstance(), name), target.getName());
                teams.put(key, state);
            }
            Object team = state.nativeTeam;
            NetworkView view = networkView(observer);
            Map<UUID, ObserverPacketSnapshot.PlayerPatch> patches = new HashMap<>(view.snapshot.players());
            String teamName = (String) team.getClass().getMethod("getName").invoke(team);
            patches.put(target.getUniqueId(), new ObserverPacketSnapshot.PlayerPatch(target.getName(), teamName,
                    nms.component.invoke(null, style.tabName()), style.tabVisible() && observer.canSee(target), style.tabOrder()));
            view.snapshot = new ObserverPacketSnapshot(patches);
            Object original = originalTeam(observer, target.getName());
            copyRules(team,original,nms.defaultTeam,nms.teamType);
            nms.prefix.invoke(team, nms.component.invoke(null, style.namePrefix()));
            nms.suffix.invoke(team, nms.component.invoke(null, style.nameSuffix()));
            color(team, style.nameColor());
            if (style.nameVisibility().isPresent()) nms.visibility.invoke(team,
                    enumValue(nms.visibilityType, style.nameVisible() ? "ALWAYS" : "NEVER"));
            @SuppressWarnings("unchecked") Collection<String> players = (Collection<String>) nms.players.invoke(team);
            players.add(target.getName());
            teams.send(key, (entry, first) -> send(observer, nms.teamPacket.invoke(null, entry.nativeTeam, first)));
            send(observer, nms.memberPacket.invoke(null, team, target.getName(), enumValue(nms.memberAction, "ADD")));
            tab(observer, target, style);
        } catch (ReflectiveOperationException | LinkageError error) { throw new IllegalStateException("Cannot update observer identity packets", error); }
    }
    final void restore(Player observer, UUID targetId, Player target) {
        NetworkView view = views.get(observer.getUniqueId());
        ObserverPacketSnapshot previous = view == null ? null : view.snapshot;
        if (view != null) {
            Map<UUID, ObserverPacketSnapshot.PlayerPatch> patches = new HashMap<>(previous.players());
            patches.remove(targetId); view.snapshot = new ObserverPacketSnapshot(patches);
        }
        Pair key = new Pair(observer.getUniqueId(), targetId);
        try {
            Bindings nms = Lazy.INSTANCE;
            if (teams.get(key) == null) restoreTab(observer, target);
            else teams.restore(key, state -> send(observer, nms.removeTeam.invoke(null, state.nativeTeam)), state -> {
                Object original = originalTeam(observer, state.realName);
                if (original != null) {
                    send(observer, nms.teamPacket.invoke(null, original, false));
                    send(observer, nms.memberPacket.invoke(null, original, state.realName, enumValue(nms.memberAction, "ADD")));
                }
                restoreTab(observer, target);
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (view != null) view.snapshot = previous;
            throw new IllegalStateException("Cannot restore observer identity packets", error);
        }
    }
    private void restoreTab(Player observer, Player target) throws ReflectiveOperationException {
        if (target != null && observer.canSee(target)) tab(observer, target, new ObserverIdentityStyle(
                target.playerListName() == null ? Component.text(target.getName()) : target.playerListName(),
                observer.isListed(target), target.getPlayerListOrder(), Component.empty(), Component.empty(), NamedTextColor.WHITE, Optional.empty()));
    }
    final void forgetObserver(UUID observer) {
        teams.forget(key -> key.observer.equals(observer));
        NetworkView view = views.remove(observer);
        if (view != null) {
            view.snapshot = ObserverPacketSnapshot.empty();
            try {
                Object current = NetworkBindings.GET.invoke(view.pipeline, "mhu_identity");
                if (current == view.handler) NetworkBindings.REMOVE.invoke(view.pipeline, "mhu_identity");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot remove identity packet handler", error); }
        }
    }
    final void close() { List.copyOf(views.keySet()).forEach(this::forgetObserver); }
    final void closeIfRestored() { teams.closeIfRestored(this::close); }
    private NetworkView networkView(Player observer) throws ReflectiveOperationException {
        NetworkView existing = views.get(observer.getUniqueId()); if (existing != null) return existing;
        Object handle = observer.getClass().getMethod("getHandle").invoke(observer);
        Object listener = handle.getClass().getField("connection").get(handle);
        Object connection = field(listener.getClass(), "connection").get(listener);
        Object channel = field(connection.getClass(), "channel").get(connection);
        Object pipeline = NetworkBindings.PIPELINE.invoke(channel);
        NetworkView view = new NetworkView(pipeline);
        view.handler = Proxy.newProxyInstance(NetworkBindings.OUTBOUND.getClassLoader(), new Class<?>[]{NetworkBindings.OUTBOUND}, (proxy, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return switch (name) {
                case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0]; default -> "ManhuntUniversal identity mirror";
            };
            if (name.equals("handlerAdded") || name.equals("handlerRemoved")) return null;
            Object context = args[0];
            if (name.equals("exceptionCaught")) { NetworkBindings.CONTEXT.getMethod("fireExceptionCaught", Throwable.class).invoke(context, args[1]); return null; }
            if (name.equals("write")) {
                try {
                    Object replacement = rewrite(args[1], view.snapshot);
                    NetworkBindings.WRITE.invoke(context, replacement, args[2]);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    // Dropping this update is safer than sending unredacted identity data after a mirror failure.
                    NetworkBindings.PROMISE.getMethod("tryFailure", Throwable.class).invoke(args[2], error);
                    if (view.warned.compareAndSet(false, true)) java.util.logging.Logger.getLogger("ManhuntUniversal")
                            .log(java.util.logging.Level.WARNING, "Observer identity mirror rejected an update", error);
                }
                return null;
            }
            Class<?>[] types = Arrays.copyOfRange(method.getParameterTypes(), 1, method.getParameterCount());
            NetworkBindings.CONTEXT.getMethod(name, types).invoke(context, Arrays.copyOfRange(args, 1, args.length));
            return null;
        });
        if (NetworkBindings.GET.invoke(pipeline, "mhu_identity") != null) throw new IllegalStateException("Identity channel handler already exists");
        NetworkBindings.ADD.invoke(pipeline, "packet_handler", "mhu_identity", view.handler);
        views.put(observer.getUniqueId(), view);
        return view;
    }
    private static Object rewrite(Object packet, ObserverPacketSnapshot snapshot) throws ReflectiveOperationException {
        if (snapshot.players().isEmpty()) return packet;
        Bindings nms = Lazy.INSTANCE;
        if (nms.infoType.isInstance(packet)) {
            @SuppressWarnings("unchecked") EnumSet<?> actions = (EnumSet<?>) nms.infoActions.invoke(packet);
            List<Object> replacements = new ArrayList<>(); boolean changed = false;
            for (Object entry : (List<?>) nms.entries.get(packet)) {
                UUID id = (UUID) nms.profileId.invoke(entry); var patch = snapshot.players().get(id);
                if (patch == null) { replacements.add(entry); continue; }
                changed = true; Object[] values = new Object[nms.entryFields.length];
                for (int index = 0; index < values.length; index++) {
                    RecordComponent component = nms.entryFields[index];
                    values[index] = switch (component.getName()) {
                        case "displayName" -> patch.displayName(); case "listed" -> patch.listed(); case "listOrder" -> patch.order();
                        default -> component.getAccessor().invoke(entry);
                    };
                }
                replacements.add(nms.entryConstructor.newInstance(values));
            }
            if (!changed) return packet;
            Object copy = nms.infoPacket.newInstance(actions.clone(), List.of()); nms.entries.set(copy, List.copyOf(replacements)); return copy;
        }
        if (nms.teamPacketType.isInstance(packet)) {
            String name = (String) nms.packetTeamName.invoke(packet);
            @SuppressWarnings("unchecked") Collection<String> original = (Collection<String>) nms.packetTeamPlayers.invoke(packet);
            List<String> filtered = snapshot.filterTeam(name, original);
            if (filtered.size() == original.size()) return packet;
            return nms.copyTeamPacket.newInstance(name, nms.packetTeamMethod.getInt(packet), nms.packetTeamParameters.invoke(packet), filtered);
        }
        return packet;
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field result = current.getDeclaredField(name); if (!result.trySetAccessible()) throw new IllegalStateException("Inaccessible field: " + name); return result;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static void copyRules(Object target,Object original,Object defaults,Class<?> teamType)throws ReflectiveOperationException{
        Object source=original==null?defaults:original;
        for(String property:List.of("AllowFriendlyFire","SeeFriendlyInvisibles","CollisionRule","DeathMessageVisibility","NameTagVisibility")){
            String getter=property.equals("AllowFriendlyFire")?"is"+property:property.equals("SeeFriendlyInvisibles")?"can"+property:"get"+property;
            Method read=teamType.getMethod(getter);teamType.getMethod("set"+property,read.getReturnType()).invoke(target,read.invoke(source));
        }
    }
    private static final class NetworkView {
        final Object pipeline;
        Object handler;
        volatile ObserverPacketSnapshot snapshot = ObserverPacketSnapshot.empty();
        final java.util.concurrent.atomic.AtomicBoolean warned = new java.util.concurrent.atomic.AtomicBoolean();
        NetworkView(Object pipeline) { this.pipeline = pipeline; }
    }
    private static final class NetworkBindings {
        static final Class<?> OUTBOUND, CONTEXT, PROMISE;
        static final Method PIPELINE, GET, REMOVE, ADD, WRITE;
        static {
            try {
                OUTBOUND = Class.forName("io.netty.channel.ChannelOutboundHandler"); CONTEXT = Class.forName("io.netty.channel.ChannelHandlerContext");
                PROMISE = Class.forName("io.netty.channel.ChannelPromise");
                Class<?> handler = Class.forName("io.netty.channel.ChannelHandler"), pipeline = Class.forName("io.netty.channel.ChannelPipeline");
                PIPELINE = Class.forName("io.netty.channel.Channel").getMethod("pipeline");
                GET = pipeline.getMethod("get", String.class); REMOVE = pipeline.getMethod("remove", String.class);
                ADD = pipeline.getMethod("addBefore", String.class, String.class, handler); WRITE = CONTEXT.getMethod("write", Object.class, PROMISE);
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
    }
    private Object originalTeam(Player observer, String name) throws ReflectiveOperationException {
        Object board = observer.getScoreboard().getClass().getMethod("getHandle").invoke(observer.getScoreboard());
        return Lazy.INSTANCE.teamForPlayer.invoke(board, name);
    }
    private void tab(Player observer, Player target, ObserverIdentityStyle style) throws ReflectiveOperationException {
        Bindings nms = Lazy.INSTANCE;
        Object handle = target.getClass().getMethod("getHandle").invoke(target);
        @SuppressWarnings({"rawtypes", "unchecked"}) EnumSet actions = EnumSet.noneOf((Class) nms.infoAction);
        for (String action : List.of("UPDATE_DISPLAY_NAME", "UPDATE_LISTED", "UPDATE_LIST_ORDER")) actions.add(enumValue(nms.infoAction, action));
        Object packet = nms.infoPacket.newInstance(actions, List.of(handle));
        List<?> oldEntries = (List<?>) nms.entries.get(packet);
        if (oldEntries.size() != 1) throw new IllegalStateException("Unexpected player info entry count");
        Object entry = oldEntries.getFirst(); Object[] values = new Object[nms.entryFields.length];
        for (int index = 0; index < values.length; index++) {
            RecordComponent field = nms.entryFields[index];
            values[index] = switch (field.getName()) {
                case "displayName" -> nms.component.invoke(null, style.tabName());
                case "listed" -> style.tabVisible() && observer.canSee(target);
                case "listOrder" -> style.tabOrder();
                default -> field.getAccessor().invoke(entry);
            };
        }
        nms.entries.set(packet, List.of(nms.entryConstructor.newInstance(values)));
        send(observer, packet);
    }
    private static void send(Player observer, Object packet) throws ReflectiveOperationException {
        Object handle = observer.getClass().getMethod("getHandle").invoke(observer);
        Object connection = handle.getClass().getField("connection").get(handle);
        connection.getClass().getMethod("send", Lazy.INSTANCE.packetType).invoke(connection, packet);
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static Object enumValue(Class<?> type, String value) { return Enum.valueOf((Class) type, value); }
    private static final class Lazy { static final Bindings INSTANCE = new Bindings(); }
    private static final class Bindings {
        final Class<?> packetType, teamType, visibilityType, memberAction, infoAction, infoType, teamPacketType;
        final Constructor<?> scoreboard, team, infoPacket, entryConstructor, copyTeamPacket;
        final Method prefix, suffix, visibility, players, teamPacket, removeTeam, memberPacket, component, teamForPlayer;
        final Field entries;
        final RecordComponent[] entryFields;
        final Object defaultTeam;
        final Method infoActions, profileId, packetTeamName, packetTeamPlayers, packetTeamParameters;
        final Field packetTeamMethod;
        Bindings() {
            try {
                packetType = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> board = Class.forName("net.minecraft.world.scores.Scoreboard");
                teamType = Class.forName("net.minecraft.world.scores.PlayerTeam");
                Class<?> text = Class.forName("net.minecraft.network.chat.Component");
                scoreboard = board.getConstructor(); team = teamType.getConstructor(board, String.class);
                defaultTeam=team.newInstance(scoreboard.newInstance(),"mhu_default");
                teamForPlayer = board.getMethod("getPlayersTeam", String.class);
                prefix = teamType.getMethod("setPlayerPrefix", text); suffix = teamType.getMethod("setPlayerSuffix", text);
                visibilityType = Class.forName("net.minecraft.world.scores.Team$Visibility");
                visibility = teamType.getMethod("setNameTagVisibility", visibilityType); players = teamType.getMethod("getPlayers");
                teamPacketType = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
                memberAction = Class.forName(teamPacketType.getName() + "$Action");
                teamPacket = teamPacketType.getMethod("createAddOrModifyPacket", teamType, boolean.class);
                removeTeam = teamPacketType.getMethod("createRemovePacket", teamType);
                memberPacket = teamPacketType.getMethod("createPlayerPacket", teamType, String.class, memberAction);
                copyTeamPacket = teamPacketType.getDeclaredConstructor(String.class, int.class, Optional.class, Collection.class);
                if (!copyTeamPacket.trySetAccessible()) throw new IllegalStateException("Team packet copy inaccessible");
                packetTeamName = teamPacketType.getMethod("getName"); packetTeamPlayers = teamPacketType.getMethod("getPlayers");
                packetTeamParameters = teamPacketType.getMethod("getParameters"); packetTeamMethod = field(teamPacketType, "method");
                component = Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asVanilla", Component.class);
                Class<?> info = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket"); infoType = info;
                infoAction = Class.forName(info.getName() + "$Action"); infoPacket = info.getConstructor(EnumSet.class, Collection.class);
                entries = info.getDeclaredField("entries"); if (!entries.trySetAccessible()) throw new IllegalStateException("Player info entries inaccessible");
                Class<?> entry = Class.forName(info.getName() + "$Entry"); entryFields = entry.getRecordComponents();
                infoActions = info.getMethod("actions"); profileId = entry.getMethod("profileId");
                entryConstructor = entry.getConstructor(Arrays.stream(entryFields).map(RecordComponent::getType).toArray(Class<?>[]::new));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported observer identity packet runtime", error); }
        }
    }
}
