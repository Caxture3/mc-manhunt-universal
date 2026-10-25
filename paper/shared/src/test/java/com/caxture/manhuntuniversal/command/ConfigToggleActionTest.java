package com.caxture.manhuntuniversal.command;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ConfigToggleActionTest {
    @Test
    void repeatedClicksReadTheCurrentValueInsteadOfTheDisplayedSnapshot() {
        AtomicBoolean value = new AtomicBoolean(true);
        assertEquals(ConfigToggleAction.Result.CHANGED, ConfigToggleAction.toggle(() -> true, value::get, next -> {
            value.set(next); return true;
        }));
        assertFalse(value.get());
        assertEquals(ConfigToggleAction.Result.CHANGED, ConfigToggleAction.toggle(() -> true, value::get, next -> {
            value.set(next); return true;
        }));
        assertTrue(value.get());
    }

    @Test
    void revokedPermissionCannotReadOrPersistTheValue() {
        assertEquals(ConfigToggleAction.Result.DENIED, ConfigToggleAction.toggle(() -> false,
                () -> { fail("Denied clicks must not read values"); return true; },
                next -> { fail("Denied clicks must not persist"); return true; }));
    }

    @Test
    void failedSaveReportsFailureWithoutChangingTheValue() {
        AtomicBoolean value = new AtomicBoolean(true);
        assertEquals(ConfigToggleAction.Result.SAVE_FAILED,
                ConfigToggleAction.toggle(() -> true, value::get, next -> false));
        assertTrue(value.get());
    }
}
