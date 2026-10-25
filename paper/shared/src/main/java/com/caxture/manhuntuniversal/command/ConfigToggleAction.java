package com.caxture.manhuntuniversal.command;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Re-evaluates authority and state for every click, including clicks on old chat messages. */
public final class ConfigToggleAction {
    private ConfigToggleAction() {
    }

    public static Result toggle(BooleanSupplier permitted, BooleanSupplier currentValue, Predicate<Boolean> save) {
        if (!permitted.getAsBoolean()) return Result.DENIED;
        return save.test(!currentValue.getAsBoolean()) ? Result.CHANGED : Result.SAVE_FAILED;
    }

    public enum Result { CHANGED, DENIED, SAVE_FAILED }
}
