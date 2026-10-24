package com.caxture.randomanhunt.presentation;

import com.caxture.randomanhunt.game.GameResult;
import com.caxture.randomanhunt.game.GameSession;
import com.caxture.randomanhunt.game.TerminationReason;
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
