package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.config.IntConfigKey;
import com.caxture.manhuntuniversal.config.DecimalConfigKey;

import java.util.regex.Pattern;

public final class CommandArguments {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private CommandArguments() {
    }

    public static int countdown(String raw) {
        int value = parseInteger(raw);
        if (value < 1 || value > 60) {
            throw new InvalidArgument(Error.OUT_OF_RANGE);
        }
        return value;
    }

    public static int integer(IntConfigKey key, String raw) {
        int value = parseInteger(raw);
        if (!key.valid(value)) {
            throw new InvalidArgument(Error.OUT_OF_RANGE);
        }
        return value;
    }

    public static boolean bool(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new InvalidArgument(Error.INVALID_BOOLEAN);
    }

    public static double decimal(DecimalConfigKey key, String raw) {
        if (raw == null || !DECIMAL.matcher(raw).matches()) {
            throw new InvalidArgument(Error.NOT_DECIMAL);
        }
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            throw new InvalidArgument(Error.NOT_DECIMAL);
        }
        if (!Double.isFinite(value)) {
            throw new InvalidArgument(Error.NOT_DECIMAL);
        }
        if (!key.valid(value)) {
            throw new InvalidArgument(Error.OUT_OF_RANGE);
        }
        return value;
    }

    private static int parseInteger(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new InvalidArgument(Error.NOT_INTEGER);
        }
    }

    public enum Error {
        NOT_INTEGER,
        NOT_DECIMAL,
        OUT_OF_RANGE,
        INVALID_BOOLEAN
    }

    public static final class InvalidArgument extends IllegalArgumentException {
        private final Error error;

        public InvalidArgument(Error error) {
            super(error.name());
            this.error = error;
        }

        public Error error() {
            return error;
        }
    }
}
