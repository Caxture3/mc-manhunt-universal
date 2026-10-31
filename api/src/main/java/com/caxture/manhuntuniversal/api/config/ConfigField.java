package com.caxture.manhuntuniversal.api.config;
import com.caxture.manhuntuniversal.api.LocalizedText;
import java.util.Objects;
/** One field of an atomic group definition. */
public record ConfigField<T>(LocalizedText name, ConfigType<T> type, String unit) {
    public ConfigField { Objects.requireNonNull(name); Objects.requireNonNull(type); Objects.requireNonNull(unit); }
}
