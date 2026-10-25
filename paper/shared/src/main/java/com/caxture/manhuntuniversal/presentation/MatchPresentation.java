package com.caxture.manhuntuniversal.presentation;

import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.TerminationReason;
import org.bukkit.command.CommandSender;

import java.time.Instant;

public interface MatchPresentation {
    void showCountdown(GameSession session, int seconds);

    void showStart(GameSession session);

    void showStatus(CommandSender recipient, GameSession session, int countdownSeconds, Instant now);

    void showIdleStatus(CommandSender recipient);

    void showResult(GameResult result);

    void showStopped(TerminationReason reason);
}
