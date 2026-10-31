package com.caxture.manhuntuniversal.api.paper;

import com.caxture.manhuntuniversal.api.*;
import org.bukkit.plugin.Plugin;

/** Paper service registered under this interface and the pure Java API interface. */
public interface PaperManhuntUniversalApi extends ManhuntUniversalApi {
    default com.caxture.manhuntuniversal.api.paper.presentation.PresentationRegistry presentation() {
        throw new UnsupportedOperationException("Presentation registry unavailable");
    }
    @Override default com.caxture.manhuntuniversal.api.paper.text.PaperTextRegistry texts() {
        throw new UnsupportedOperationException("Paper text registry unavailable");
    }
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
