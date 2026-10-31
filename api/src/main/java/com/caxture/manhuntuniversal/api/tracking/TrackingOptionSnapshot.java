package com.caxture.manhuntuniversal.api.tracking;
/** Resolved for one player's locale and availability. */
public record TrackingOptionSnapshot(String id, String name, String secondaryName, String description,
                                     int order, TrackingAppearance appearance, TargetAvailability availability) { }
