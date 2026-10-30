/**
 * Synchronous lifecycle notifications and cancellable pre-change events. Snapshot collections are immutable.
 * Each concrete event owns an independent Bukkit handler list. API writes from callbacks return BUSY.
 */
package com.caxture.manhuntuniversal.api.paper.event;
