package com.caxture.manhuntuniversal.presentation;
import com.caxture.manhuntuniversal.api.paper.presentation.ObserverIdentityStyle;
import java.util.*;
/** No asynchronous adapter may reinstall a style computed for a superseded registry revision. */
final class ObserverIdentityState {
    record Pair(UUID observer, UUID target) { }
    private final Map<Pair, ObserverIdentityStyle> values = new LinkedHashMap<>();
    private long revision;
    long invalidate() { return ++revision; }
    long revision() { return revision; }
    boolean put(UUID observer, UUID target, ObserverIdentityStyle style, long expected) {
        if (expected != revision) return false;
        values.put(new Pair(observer, target), style); return true;
    }
    Optional<ObserverIdentityStyle> get(UUID observer, UUID target) { return Optional.ofNullable(values.get(new Pair(observer, target))); }
    Map<Pair, ObserverIdentityStyle> clear() { var old = Map.copyOf(values); values.clear(); return old; }
    Map<Pair, ObserverIdentityStyle> removePlayer(UUID player) {
        Map<Pair, ObserverIdentityStyle> result = new LinkedHashMap<>();
        values.entrySet().removeIf(entry -> { boolean match = entry.getKey().observer.equals(player) || entry.getKey().target.equals(player);
            if (match) result.put(entry.getKey(), entry.getValue()); return match; }); return Map.copyOf(result);
    }
}
