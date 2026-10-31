package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.config.DecimalConfigKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecimalCommandArgumentsTest {
    private static final DecimalConfigKey KEY = DecimalConfigKey.TRACKING_DISTANCE_REFRESH_SECONDS;

    @Test void acceptsDecimalSecondsIncludingBothBounds() {
        assertEquals(0.1, CommandArguments.decimal(KEY, "0.1"));
        assertEquals(0.35, CommandArguments.decimal(KEY, "0.35"));
        assertEquals(1.0, CommandArguments.decimal(KEY, "1"));
        assertEquals(60.0, CommandArguments.decimal(KEY, "60.0"));
    }

    @Test void rejectsNonFiniteAndNonDecimalInputBeforeRangeValidation() {
        for (String raw : new String[]{"NaN", "Infinity", "-Infinity", "abc", "0x1.0p0", "1d", " 1"}) {
            assertEquals(CommandArguments.Error.NOT_DECIMAL, assertThrows(CommandArguments.InvalidArgument.class,
                    () -> CommandArguments.decimal(KEY, raw)).error(), raw);
        }
        for (String raw : new String[]{"0.09", "60.001", "-1"}) {
            assertEquals(CommandArguments.Error.OUT_OF_RANGE, assertThrows(CommandArguments.InvalidArgument.class,
                    () -> CommandArguments.decimal(KEY, raw)).error(), raw);
        }
    }
}
