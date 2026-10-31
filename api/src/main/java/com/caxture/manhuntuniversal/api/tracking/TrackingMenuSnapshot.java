package com.caxture.manhuntuniversal.api.tracking;
import java.util.*;
public record TrackingMenuSnapshot(long revision, Optional<String> selection, List<TrackingOptionSnapshot> options) {
    public TrackingMenuSnapshot { Objects.requireNonNull(selection); options = List.copyOf(options); }
}
