package com.caxture.manhuntuniversal.api.paper;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.plugin.Plugin;

/** Paper service registered under this interface and the pure Java API interface. */
public interface PaperManhuntUniversalApi extends ManhuntUniversalApi {
    /**
     * Registers a plugin-owned extension on the main thread. Domains are exclusive;
     * observers with an empty domain set may coexist. Wrong-thread calls return WRONG_THREAD.
     * Plugin disable automatically closes successful registrations.
     * @param owner plugin owning the registration
     * @param options identifier, requested domains, and optional policies
     * @return the requested value or operation outcome
     */
    RegistrationResult register(Plugin owner, ExtensionOptions options);
}
