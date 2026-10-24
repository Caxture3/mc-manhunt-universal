package com.caxture.randomanhunt.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class RoleAssigner {
    private final RandomGenerator random;

    public RoleAssigner(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public List<UUID> select(List<UUID> participants, int runnerCount) {
        List<UUID> stable = List.copyOf(participants);
        if (stable.stream().distinct().count() != stable.size()) {
            throw new IllegalArgumentException("Participants must be unique");
        }
        if (runnerCount < 1 || runnerCount >= stable.size()) {
            throw new IllegalArgumentException("Selection must leave at least one hunter");
        }
        List<UUID> shuffled = new ArrayList<>(stable);
        for (int index = shuffled.size() - 1; index > 0; index--) {
            int swapWith = random.nextInt(index + 1);
            UUID value = shuffled.get(index);
            shuffled.set(index, shuffled.get(swapWith));
            shuffled.set(swapWith, value);
        }
        Set<UUID> selected = new LinkedHashSet<>(shuffled.subList(0, runnerCount));
        return stable.stream().filter(selected::contains).toList();
    }
}
