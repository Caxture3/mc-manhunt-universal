package com.caxture.randomanhunt.player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AllOrNothingAction {
    private AllOrNothingAction() {
    }

    public static <T> boolean execute(List<T> entries, Predicate<T> action, Consumer<T> rollback) {
        List<T> completed = new ArrayList<>(entries.size());
        for (T entry : entries) {
            if (!action.test(entry)) {
                for (int index = completed.size() - 1; index >= 0; index--) {
                    rollback.accept(completed.get(index));
                }
                return false;
            }
            completed.add(entry);
        }
        return true;
    }
}
