package com.caxture.manhuntuniversal.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registration options. Policies require ownership of their corresponding control domain.
 * @param id stable identifier
 * @param controls exclusive domains requested; defensively copied
 * @param eliminationPolicy optional elimination callback requiring ELIMINATION ownership
 * @param victoryPolicy optional victory callback requiring VICTORY ownership
 */
public record ExtensionOptions(String id, Set<ControlDomain> controls, Optional<EliminationPolicy> eliminationPolicy, Optional<VictoryPolicy> victoryPolicy) {
    /** Creates a validated value; null components are rejected. */
    public ExtensionOptions {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        controls = Set.copyOf(controls);
        Objects.requireNonNull(eliminationPolicy, "eliminationPolicy");
        Objects.requireNonNull(victoryPolicy, "victoryPolicy");
        if (eliminationPolicy.isPresent() && !controls.contains(ControlDomain.ELIMINATION))
            throw new IllegalArgumentException("elimination policy requires ELIMINATION control");
        if (victoryPolicy.isPresent() && !controls.contains(ControlDomain.VICTORY))
            throw new IllegalArgumentException("victory policy requires VICTORY control");
    }

    /**
     * Creates a registration without policy callbacks.
     * @param id stable identifier
     * @param controls exclusive domains requested; defensively copied
     */
    public ExtensionOptions(String id, Set<ControlDomain> controls) {
        this(id, controls, Optional.empty(), Optional.empty());
    }
}
