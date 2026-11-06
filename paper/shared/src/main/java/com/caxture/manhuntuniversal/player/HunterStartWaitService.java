package com.caxture.manhuntuniversal.player;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.presentation.MatchPresentation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.time.*;
import java.util.*;
/** One per-match deadline; owned effect/render state never resets the game or role timer. */
public final class HunterStartWaitService {
    private final JavaPlugin plugin;private final GameManager games;private final PlayerStartService starts;private final PendingPlayerState pending;
    private final CountdownBlindnessService blindness;private final MatchPresentation presentation;private final Clock clock;private final Runnable released,failure;
    private final HunterStartCountdownDisplay countdownDisplay=new HunterStartCountdownDisplay();
    private final Set<UUID> affected=new HashSet<>();private BukkitTask ticker;private boolean active,internalTeleport;private int ticks;
    public HunterStartWaitService(JavaPlugin plugin,GameManager games,PlayerStartService starts,PendingPlayerState pending,CountdownBlindnessService blindness,
                                  MatchPresentation presentation,Clock clock,Runnable released,Runnable failure){
        this.plugin=plugin;this.games=games;this.starts=starts;this.pending=pending;this.blindness=blindness;this.presentation=presentation;this.clock=clock;this.released=released;this.failure=failure;
        plugin.getServer().getPluginManager().registerEvents(new HunterStartWaitListener(this::held,starts::anchor,()->internalTeleport),plugin);
    }
    public boolean held(UUID player){var game=games.activeSession().orElse(null);return active&&!pending.needsReset(player)&&HunterStartWaitPolicy.held(game,player,game==null?0:starts.waitSeconds(game),Instant.now(clock));}
    public void start(){stop(false);active=true;sync();var game=games.activeSession().orElse(null);
        if(HunterStartWaitPolicy.remaining(game,game==null?0:starts.waitSeconds(game),Instant.now(clock))<=0)return;
        ticker=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{try{
            sync();if(games.activeSession().isEmpty()||HunterStartWaitPolicy.remaining(games.activeSession().get(),starts.waitSeconds(games.activeSession().get()),Instant.now(clock))==0){stop(true);released.run();}
        }catch(RuntimeException|LinkageError error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Hunter start wait failed",error);failure.run();}},1L,1L);
    }
    public void sync(){
        for(UUID id:List.copyOf(affected))if(!held(id))release(id,true);
        var game=games.activeSession().orElse(null);if(game==null)return;long seconds=HunterStartWaitPolicy.remaining(game,starts.waitSeconds(game),Instant.now(clock));
        boolean refreshCountdown=countdownDisplay.update(seconds);
        for(Player player:plugin.getServer().getOnlinePlayers()){
            UUID id=player.getUniqueId();if(!held(id)||player.isDead())continue;
            if(starts.anchor(id)==null)starts.anchor(id,player.getLocation());
            boolean first=affected.add(id);Location anchor=starts.anchor(id);Location current=player.getLocation();
            if(!anchor.getWorld().equals(current.getWorld())||anchor.distanceSquared(current)>0.000001){
                Location fixed=anchor.clone();fixed.setYaw(current.getYaw());fixed.setPitch(current.getPitch());internalTeleport=true;
                try{if(!player.teleport(fixed,PlayerTeleportEvent.TeleportCause.PLUGIN))throw new IllegalStateException("Cannot restore hunter waiting position");}finally{internalTeleport=false;}
            }
            player.setVelocity(new Vector());player.setFallDistance(0);
            if(first||ticks%20==0)blindness.apply(player);
            if(first||refreshCountdown)presentation.showHunterStartCountdown(player,seconds);
        }
        ticks++;
    }
    public void joined(Player player){affected.remove(player.getUniqueId());sync();}
    public void release(UUID id,boolean removeAnchor){
        if(affected.remove(id)){Player player=plugin.getServer().getPlayer(id);if(player!=null&&player.isOnline()){blindness.restore(player);presentation.clearHunterStartCountdown(player);}}
        if(removeAnchor)starts.removeAnchor(id);
    }
    public void stop(boolean removeAnchors){if(ticker!=null){ticker.cancel();ticker=null;}active=false;for(UUID id:List.copyOf(affected))release(id,removeAnchors);ticks=0;countdownDisplay.reset();}
}
