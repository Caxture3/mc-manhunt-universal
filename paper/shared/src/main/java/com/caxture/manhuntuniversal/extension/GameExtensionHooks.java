package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.game.GameResult;
import com.caxture.manhuntuniversal.game.GameSession;
import com.caxture.manhuntuniversal.game.TerminationReason;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Internal lifecycle boundary. The default implementation preserves the base game. */
public interface GameExtensionHooks {
    GameExtensionHooks NONE = new GameExtensionHooks() {};

    default <T> T inMutation(Supplier<T> action) { return action.get(); }
    default boolean mutationActive() { return false; }
    default boolean preparing(GameSession session, int countdownSeconds) { return true; }
    default void countdown(GameSession session, int countdownSeconds) {}
    default void started(GameSession session) {}
    default void ended(GameSession session, TerminationReason reason, GameResult result) {}
    default boolean shouldEliminate(GameSession session, UUID playerId, String cause) {
        return session.isRunnerAlive(playerId)
                || ("API".equals(cause) && session.participant(playerId).isPresent()
                && !session.isEliminatedParticipant(playerId));
    }
    default void eliminated(GameSession session, UUID playerId, String cause) {}
    default void revived(GameSession session, UUID playerId) {}
    default void endEntered(GameSession session, UUID playerId, UUID worldId) {}
    default Optional<GameResult> decidingVictory(GameSession session, GameResult result) {
        return Optional.of(result);
    }
}
