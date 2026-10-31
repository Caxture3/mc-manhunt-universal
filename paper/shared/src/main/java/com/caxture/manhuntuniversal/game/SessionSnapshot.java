package com.caxture.manhuntuniversal.game;

import java.time.*;
import java.util.*;

/** Version-independent persisted game values; no callbacks, scheduler or platform objects. */
public record SessionSnapshot(UUID id, Instant createdAt, List<Participant> participants, MatchSettings settings,
                              List<UUID> runners, Set<UUID> eliminated, SessionState state, Optional<Instant> startedAt,
                              Optional<Instant> roundStartedAt, Optional<UUID> targetEndWorldId, long revision, List<RunnerRound> rounds) {
    public SessionSnapshot {
        Objects.requireNonNull(id);Objects.requireNonNull(createdAt);Objects.requireNonNull(settings);Objects.requireNonNull(state);
        participants=List.copyOf(participants);runners=List.copyOf(runners);eliminated=Set.copyOf(eliminated);rounds=List.copyOf(rounds);
        Objects.requireNonNull(startedAt);Objects.requireNonNull(roundStartedAt);Objects.requireNonNull(targetEndWorldId);
        Set<UUID> roster=new HashSet<>();
        for(var participant:participants)if(participant.id()==null||participant.name()==null||participant.name().isBlank()
                ||participant.name().length()>64||!roster.add(participant.id()))throw new IllegalArgumentException("Invalid participant roster");
        if(participants.size()<2||participants.size()>10000||runners.isEmpty()||runners.size()>=participants.size()
                ||new HashSet<>(runners).size()!=runners.size()||!roster.containsAll(runners)||!roster.containsAll(eliminated)
                ||revision<0||state==SessionState.SETTLING)throw new IllegalArgumentException("Invalid persistent session");
        if(state==SessionState.COUNTDOWN&&(startedAt.isPresent()||roundStartedAt.isPresent()||!rounds.isEmpty()||!eliminated.isEmpty()||targetEndWorldId.isPresent()))
            throw new IllegalArgumentException("Countdown has active-game state");
        if(state==SessionState.RUNNING&&(startedAt.isEmpty()||roundStartedAt.isEmpty()
                ||startedAt.get().isBefore(createdAt)||roundStartedAt.get().isBefore(startedAt.get())))throw new IllegalArgumentException("Invalid session time axis");
        for(var round:rounds)if(!roster.containsAll(round.runners())||round.runners().isEmpty()
                ||startedAt.isEmpty()||round.startedAt().isBefore(startedAt.get())||round.endedAt().isAfter(roundStartedAt.orElseThrow()))
            throw new IllegalArgumentException("Invalid round history");
    }
    public SessionSnapshot shift(Duration offline) {
        return new SessionSnapshot(id,createdAt.plus(offline),participants,settings,runners,eliminated,state,
                startedAt.map(time->time.plus(offline)),roundStartedAt.map(time->time.plus(offline)),targetEndWorldId,revision,
                rounds.stream().map(round->new RunnerRound(round.runners(),round.startedAt().plus(offline),round.endedAt().plus(offline),round.reason())).toList());
    }
}
