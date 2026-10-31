package com.caxture.manhuntuniversal.api.feature;
import java.util.Set;
/** Stable identifiers; do not infer feature IDs from configuration keys. */
public final class CoreFeatureIds {
    private CoreFeatureIds() { }
    public static final String ORE_FORTUNE = "core:ore-fortune";
    public static final String BLAZE_ROD_LOOTING = "core:blaze-rod-looting";
    public static final String PIGLIN_BARTERING = "core:piglin-bartering";
    public static final String PIGLIN_BRUTE_SUPPRESSION = "core:piglin-brute-suppression";
    public static final String BLAZE_SPAWNER_PROTECTION = "core:blaze-spawner-protection";
    public static final String NETHER_PORTAL_PROTECTION = "core:nether-portal-protection";
    public static final String HUNTER_END_RESTRICTION = "core:hunter-end-restriction";
    public static final String TRACKING_COMPASS_GRANT = "core:tracking-compass-grant";
    public static final String TRACKING_COMPASS_USE = "core:tracking-compass-use";
    public static Set<String> all() { return Set.of(ORE_FORTUNE, BLAZE_ROD_LOOTING, PIGLIN_BARTERING, PIGLIN_BRUTE_SUPPRESSION,
            BLAZE_SPAWNER_PROTECTION, NETHER_PORTAL_PROTECTION, HUNTER_END_RESTRICTION, TRACKING_COMPASS_GRANT, TRACKING_COMPASS_USE); }
}
