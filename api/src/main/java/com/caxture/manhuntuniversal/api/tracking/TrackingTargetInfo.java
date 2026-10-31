package com.caxture.manhuntuniversal.api.tracking;
import com.caxture.manhuntuniversal.api.LocalizedText;
/** Detached public catalog metadata; no provider objects are exposed. */
public record TrackingTargetInfo(String id, String ownerId, LocalizedText name, LocalizedText secondaryName,
                                 LocalizedText description, int order, TrackingWorld world, TrackingAppearance appearance) { }
