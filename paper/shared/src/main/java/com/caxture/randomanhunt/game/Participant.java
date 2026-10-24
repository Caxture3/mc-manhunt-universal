package com.caxture.randomanhunt.game;

import java.util.Objects;
import java.util.UUID;

public record Participant(UUID id, String name) {
    public Participant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
