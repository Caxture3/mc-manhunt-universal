package com.caxture.manhuntuniversal.api.feature;
/** Fast main-thread callback. Throwing/null decisions preserve the core rule. */
@FunctionalInterface public interface FeaturePolicy { FeatureDecision decide(FeatureContext context); }
