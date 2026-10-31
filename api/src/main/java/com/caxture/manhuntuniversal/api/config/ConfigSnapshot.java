package com.caxture.manhuntuniversal.api.config;
import com.caxture.manhuntuniversal.api.LocalizedText;
import java.util.Optional;
/** Detached saved/effective values. NEXT_MATCH values do not mutate an already locked match. */
public record ConfigSnapshot(String key, LocalizedText name, LocalizedText description, Object savedValue,
                             Object effectiveValue, Object nextMatchValue, long revision, ConfigTiming timing,
                             String unit, boolean booleanValue, Optional<String> lockedBy, Optional<String> lockReason) {
    public ConfigSnapshot {
        savedValue = ConfigTypes.immutable(savedValue); effectiveValue = ConfigTypes.immutable(effectiveValue);
        nextMatchValue = ConfigTypes.immutable(nextMatchValue);
    }
}
