package com.caxture.manhuntuniversal.api.config;
import java.util.*;
/** Committed state notification. Removed keys appear in keys but not values. */
public record ConfigurationChange(long revision, Set<String> keys, Map<String, ConfigSnapshot> values) {
    public ConfigurationChange { keys = Set.copyOf(keys); values = Map.copyOf(values); }
}
