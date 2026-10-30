package com.caxture.manhuntuniversal.extension;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.game.GameSession;

import java.util.LinkedHashMap;
import java.util.UUID;

final class MatchSnapshots {
    private MatchSnapshots() {}

    static MatchSnapshot of(GameSession session) {
        var participants = new LinkedHashMap<UUID, ParticipantSnapshot>();
        session.participants().forEach(player -> participants.put(player.id(), new ParticipantSnapshot(
                player.id(), player.name(), TeamRole.valueOf(session.roleOf(player.id()).orElseThrow().name()),
                session.isEliminatedParticipant(player.id()))));
        return new MatchSnapshot(session.id(), session.revision(), MatchState.valueOf(session.state().name()),
                session.createdAt(), session.startedAt(), participants, session.targetEndWorldId());
    }
}
