package com.caxture.manhuntuniversal.api.tracking;
/** Safe to poll on worker threads. Cancellation is a request, not proof underlying I/O stopped. */
@FunctionalInterface public interface CancellationToken { boolean cancelled(); }
