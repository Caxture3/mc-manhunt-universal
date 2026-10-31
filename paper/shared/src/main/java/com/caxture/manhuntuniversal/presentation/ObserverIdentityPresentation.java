package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.api.MatchSnapshot;
import com.caxture.manhuntuniversal.api.paper.presentation.*;
import com.caxture.manhuntuniversal.extension.presentation.PresentationRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;

/** Main-thread per-observer packet overlays; never assigns a new Bukkit scoreboard. */
public final class ObserverIdentityPresentation implements Listener {
    private final JavaPlugin plugin;
    private final PresentationRegistryService registry;
    private final Supplier<Optional<MatchSnapshot>> match;
    private final BooleanSupplier hidden;
    private final ObserverIdentityState state = new ObserverIdentityState();
    private final ObserverIdentityPackets packets = new ObserverIdentityPackets();
    private final Set<ObserverIdentityState.Pair> concealed = new HashSet<>(), warned = new HashSet<>();
    private final BukkitTask task;
    private boolean dirty = true;
    private int ticks;
    public ObserverIdentityPresentation(JavaPlugin plugin, PresentationRegistryService registry,
                                        Supplier<Optional<MatchSnapshot>> match, BooleanSupplier hidden) {
        this.plugin = plugin; this.registry = registry; this.match = match; this.hidden = hidden;
        registry.identityChanged(() -> { state.invalidate(); clearOverlays(); if (!registry.hasIdentityProviders()) packets.closeIfRestored(); dirty = true; });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty || ++ticks >= 20) { dirty = false; ticks = 0; refresh(); }
        }, 1L, 1L);
    }
    public void refreshSoon() { dirty = true; }
    public void privacyChanged() { state.invalidate(); clearOverlays(); dirty = false; refresh(); }
    private void refresh() {
        if (!registry.hasIdentityProviders()) { clearOverlays(); packets.closeIfRestored(); return; }
        Optional<MatchSnapshot> current = match.get(); long revision = state.revision();
        for (Player observer : plugin.getServer().getOnlinePlayers()) for (Player target : plugin.getServer().getOnlinePlayers()) {
            var pair = new ObserverIdentityState.Pair(observer.getUniqueId(), target.getUniqueId());
            if (!observer.canSee(target)&&!concealed.contains(pair)) continue;
            var team = observer.getScoreboard().getEntryTeam(target.getName());
            var fallback = new ObserverIdentityStyle(target.playerListName() == null ? Component.text(target.getName()) : target.playerListName(),
                    observer.isListed(target), target.getPlayerListOrder(), team == null ? Component.empty() : team.prefix(),
                    team == null ? Component.empty() : team.suffix(), team == null || team.color() == null ? NamedTextColor.WHITE : NamedTextColor.nearestTo(team.color()), Optional.empty());
            var style = registry.identity(new ObserverIdentityContext(observer, target.getUniqueId(), target.getName(), current, fallback));
            if (!state.put(observer.getUniqueId(), target.getUniqueId(), style, revision)) continue;
            try {
                ObserverIdentityRecovery.apply(concealed.contains(pair),()->packets.apply(observer,target,style),()->observer.showPlayer(plugin,target));
                concealed.remove(pair);
            } catch (RuntimeException | LinkageError error) {
                if (warned.add(pair)) plugin.getLogger().log(Level.WARNING, "Observer identity adapter failed for " + pair, error);
                if (!style.tabVisible() || !style.nameVisible() || hidden.getAsBoolean()) { concealed.add(pair); observer.hidePlayer(plugin, target); }
            }
        }
    }
    private void restore(ObserverIdentityState.Pair pair) {
        Player observer = plugin.getServer().getPlayer(pair.observer()), target = plugin.getServer().getPlayer(pair.target());
        if (observer == null || !observer.isOnline()) { packets.forgetObserver(pair.observer()); return; }
        try {
            packets.restore(observer, pair.target(), target);
            if (concealed.remove(pair) && target != null) observer.showPlayer(plugin, target);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "Observer identity restore failed", error);
            if (target != null && hidden.getAsBoolean()) {
                concealed.add(pair);
                observer.hidePlayer(plugin, target);
            }
        }
    }
    private void clearOverlays() {
        state.clear().keySet().forEach(this::restore);
        List.copyOf(concealed).forEach(this::restore); warned.clear();
    }
    @EventHandler public void joined(PlayerJoinEvent event) { refreshSoon(); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        state.removePlayer(event.getPlayer().getUniqueId()).keySet().forEach(this::restore);
        packets.forgetObserver(event.getPlayer().getUniqueId()); refreshSoon();
    }
    public void close() { task.cancel(); clearOverlays(); packets.close(); }
}
