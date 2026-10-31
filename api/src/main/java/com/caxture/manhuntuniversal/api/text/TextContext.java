package com.caxture.manhuntuniversal.api.text;
import java.util.*;
/** Detached lookup context. Providers return templates; placeholders are expanded by the core afterwards. */
public record TextContext(String key, String locale, Optional<UUID> recipientId, Map<String, String> placeholders) {
    public TextContext { Objects.requireNonNull(key); Objects.requireNonNull(locale); Objects.requireNonNull(recipientId); placeholders = Map.copyOf(placeholders); }
}
