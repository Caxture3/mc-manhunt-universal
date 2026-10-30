package com.caxture.manhuntuniversal.common;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountdownTextTest {
    @Test
    void choosesUnitsAtExactMillisecondBoundariesAndRoundsSecondsUp() {
        assertEquals("1s", CountdownText.format(1));
        assertEquals("1s", CountdownText.format(999));
        assertEquals("1s", CountdownText.format(1000));
        assertEquals("2s", CountdownText.format(1001));
        assertEquals("60s", CountdownText.format(59_999));
        assertEquals("1.0min", CountdownText.format(60_000));
        assertEquals("1.5min", CountdownText.format(90_000));
        assertEquals("60.0min", CountdownText.format(3_599_999));
        assertEquals("1.0h", CountdownText.format(3_600_000));
        assertEquals("1.5h", CountdownText.format(5_400_000));
    }

    @Test
    void usesRootLocaleRegardlessOfTheServersDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.5min", CountdownText.format(90_000));
            assertEquals("1.5h", CountdownText.format(5_400_000));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nonpositiveDurationsDisplayZeroAndPositiveDurationsNeverDo() {
        assertEquals("0s", CountdownText.format(-1));
        assertEquals("0s", CountdownText.format(0));
        assertEquals("1s", CountdownText.format(1));
        assertEquals("2562047788015.2h", CountdownText.format(Long.MAX_VALUE));
    }
}
