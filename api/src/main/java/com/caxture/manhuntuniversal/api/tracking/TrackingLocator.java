package com.caxture.manhuntuniversal.api.tracking;
import java.util.concurrent.CompletionStage;
/** Invoked on the main thread; workers may only use detached request data or platform-safe async APIs. */
@FunctionalInterface public interface TrackingLocator { CompletionStage<LocateResult> locate(LocateRequest request, CancellationToken cancellation); }
