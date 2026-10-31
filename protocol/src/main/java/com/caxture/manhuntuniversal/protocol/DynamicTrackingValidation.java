package com.caxture.manhuntuniversal.protocol;
import java.util.*;
final class DynamicTrackingValidation {
    private DynamicTrackingValidation() { }
    static void text(String value, int points, boolean ascii) {
        Objects.requireNonNull(value);
        if (value.codePointCount(0, value.length()) > points || ascii && value.chars().anyMatch(character -> character > 127)) throw new IllegalArgumentException("String exceeds protocol bound");
        for (int index = 0; index < value.length(); index++) if (Character.isSurrogate(value.charAt(index))) {
            if (!Character.isHighSurrogate(value.charAt(index)) || index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
                throw new IllegalArgumentException("Invalid Unicode surrogate");
            index++;
        }
    }
    static void id(String id) { text(id, 128, true); if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("://")) throw new IllegalArgumentException("Invalid target ID"); }
    static void resource(String id) { text(id, 256, true); if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("..") || id.contains("://")) throw new IllegalArgumentException("Invalid resource ID"); }
    static void header(int version, UUID session, long authorization, long revision) {
        Objects.requireNonNull(session);
        if (version != 3 || authorization <= 0 || revision < 0) throw new IllegalArgumentException("Invalid dynamic tracking header");
    }
    static List<TrackingTargetDescriptor> catalog(Optional<String> selected, List<TrackingTargetDescriptor> targets) {
        Objects.requireNonNull(selected); selected.ifPresent(DynamicTrackingValidation::id);
        List<TrackingTargetDescriptor> result = List.copyOf(targets);
        if (result.size() > 32 || result.stream().map(TrackingTargetDescriptor::id).distinct().count() != result.size()) throw new IllegalArgumentException("Invalid or duplicate target catalog");
        return result;
    }
}
