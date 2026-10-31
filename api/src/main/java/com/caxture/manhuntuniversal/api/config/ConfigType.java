package com.caxture.manhuntuniversal.api.config;
import java.util.List;
import java.util.Map;
/** Parser and validator for immutable configuration data. Throw IllegalArgumentException on invalid input. */
public interface ConfigType<T> {
    T parse(String input);
    T validate(Object value);
    default List<String> complete(String prefix) { return List.of(); }
    default Map<String, ConfigField<?>> fields() { return Map.of(); }
    default boolean booleanValue() { return false; }
}
