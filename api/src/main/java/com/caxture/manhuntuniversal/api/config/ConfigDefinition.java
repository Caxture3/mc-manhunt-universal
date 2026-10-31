package com.caxture.manhuntuniversal.api.config;
import com.caxture.manhuntuniversal.api.LocalizedText;
import java.util.Objects;
/** Metadata and storage ownership of one root value (possibly an atomic group). */
public record ConfigDefinition<T>(String key, LocalizedText name, LocalizedText description, T defaultValue,
                                  ConfigType<T> type, ConfigStorage<T> storage, ConfigTiming timing, String unit) {
    @SuppressWarnings("unchecked") public ConfigDefinition {
        Objects.requireNonNull(key); Objects.requireNonNull(name); Objects.requireNonNull(description);
        Objects.requireNonNull(type); Objects.requireNonNull(storage); Objects.requireNonNull(timing); Objects.requireNonNull(unit);
        if (!key.matches("[a-z0-9_-]+(?::[a-z0-9_./-]+)?")) throw new IllegalArgumentException("Invalid config key");
        defaultValue = (T) ConfigTypes.immutable(type.validate(defaultValue));
    }
}
