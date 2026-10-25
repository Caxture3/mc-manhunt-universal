package com.caxture.manhuntuniversal.game;

public sealed interface CountdownStep {
    record Number(int value) implements CountdownStep {
    }

    enum Start implements CountdownStep {
        INSTANCE
    }

    enum Cancelled implements CountdownStep {
        INSTANCE
    }
}
