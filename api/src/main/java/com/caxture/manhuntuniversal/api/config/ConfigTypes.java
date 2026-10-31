package com.caxture.manhuntuniversal.api.config;

import java.util.*;
import java.util.function.Function;

/** Built-in strict parsers; custom types must normalize to immutable scalar/list/string-keyed map data. */
public final class ConfigTypes {
    private ConfigTypes() { }
    public static ConfigType<Boolean> booleans() {
        return new ConfigType<>() {
            public Boolean parse(String raw) {
                if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) throw invalid("Expected true or false");
                return Boolean.parseBoolean(raw);
            }
            public Boolean validate(Object value) { if (value instanceof Boolean b) return b; throw invalid("Expected boolean"); }
            public List<String> complete(String prefix) { return matching(List.of("true", "false"), prefix); }
            public boolean booleanValue() { return true; }
        };
    }
    public static ConfigType<Integer> integers(int min, int max) {
        return simple(Integer::parseInt, value -> {
            if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) throw invalid("Expected integer");
            long number = ((Number) value).longValue();
            if (number < min || number > max) throw invalid("Range: " + min + ".." + max);
            return (int) number;
        });
    }
    public static ConfigType<Double> decimals(double min, double max) {
        return simple(Double::parseDouble, value -> {
            if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
                throw invalid("Range: " + min + ".." + max);
            return n.doubleValue();
        });
    }
    public static ConfigType<String> text() { return simple(Function.identity(), value -> {
        if (value instanceof String text) return text; throw invalid("Expected text");
    }); }
    public static ConfigType<String> enumeration(List<String> choices) {
        List<String> values = List.copyOf(choices);
        return new ConfigType<>() {
            public String parse(String raw) { return validate(raw); }
            public String validate(Object value) { if (value instanceof String text && values.contains(text)) return text; throw invalid("Expected one of " + values); }
            public List<String> complete(String prefix) { return matching(values, prefix); }
        };
    }
    public static ConfigType<List<String>> strings() {
        return simple(raw -> raw.isBlank() ? List.of() : List.of(raw.split(",", -1)), value -> {
            if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) throw invalid("Expected string list");
            return list.stream().map(String.class::cast).toList();
        });
    }
    public static ConfigType<Map<String, Object>> group(Map<String, ConfigField<?>> fields) {
        Map<String, ConfigField<?>> schema = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        if (schema.isEmpty() || schema.keySet().stream().anyMatch(key -> !key.matches("[a-z0-9_-]+"))) throw invalid("Invalid group fields");
        return new ConfigType<>() {
            public Map<String, Object> parse(String raw) { throw invalid("Edit an individual group field"); }
            public Map<String, Object> validate(Object value) {
                if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(schema.keySet())) throw invalid("Expected fields " + schema.keySet());
                Map<String, Object> result = new LinkedHashMap<>();
                schema.forEach((key, field) -> result.put(key, immutable(field.type().validate(map.get(key)))));
                return Collections.unmodifiableMap(result);
            }
            public Map<String, ConfigField<?>> fields() { return schema; }
        };
    }
    public static Object immutable(Object value) {
        Objects.requireNonNull(value, "config value");
        if (value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
        if (value instanceof List<?> list) return list.stream().map(ConfigTypes::immutable).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw invalid("Map keys must be text");
                result.put(text, immutable(item));
            });
            return Collections.unmodifiableMap(result);
        }
        throw invalid("Unsupported configuration value");
    }
    private static <T> ConfigType<T> simple(Function<String, ?> parser, Function<Object, T> validator) {
        return new ConfigType<>() {
            public T parse(String raw) { return validate(parser.apply(raw)); }
            public T validate(Object value) { return validator.apply(value); }
        };
    }
    private static List<String> matching(List<String> values, String prefix) { return values.stream().filter(value -> value.startsWith(prefix)).toList(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
