package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.TerminationReason;
import org.bukkit.command.CommandSender;

import java.time.Instant;

public interface MatchPresentation {
    default void showHunterStartCountdown(org.bukkit.entity.Player player,long seconds){}
    default void clearHunterStartCountdown(org.bukkit.entity.Player player){player.sendActionBar(net.kyori.adventure.text.Component.empty());}
    void showCountdown(GameSession session, int seconds);

    void showStart(GameSession session);

    default void showRolesChanged(GameSession session) {}

    void showStatus(CommandSender recipient, GameSession session, int countdownSeconds, Instant now);

    void showIdleStatus(CommandSender recipient);

    void showResult(GameResult result);

    void showStopped(TerminationReason reason);
}
