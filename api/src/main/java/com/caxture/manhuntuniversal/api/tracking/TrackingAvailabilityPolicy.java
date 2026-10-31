package com.caxture.manhuntuniversal.api.tracking;
/** Main-thread availability policy. Failure hides the option, preventing unauthorized selection. */
@FunctionalInterface public interface TrackingAvailabilityPolicy { TargetAvailability evaluate(TrackingViewerContext viewer); }
