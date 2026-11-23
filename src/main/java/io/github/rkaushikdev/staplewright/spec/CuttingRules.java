package io.github.rkaushikdev.staplewright.spec;

import java.util.Optional;

/**
 * Shop policy about what a cut sequence is allowed to look like.
 *
 * <p>{@code maxRun} is how many chambers in a row may carry the same depth. {@code minDistinct} is
 * how many different depths a key has to use overall. {@code shallowTipLimit} caps the depth of the
 * last chamber, because a deep cut at the tip on a worn key lets the plug turn a hair early, and
 * {@code deepBowLimit} caps the first, because the first cut sits right against the shoulder where
 * the blade is already narrowest and a deep one there is where keys snap off in a lock. The shop
 * will not issue a key that is one long run of the same depth, because it looks like a blank
 * to a student and gets thrown away, and these rules live apart from the chart so that changing
 * policy does not mean editing a manufacturer's numbers.
 */
public record CuttingRules(
        int maxRun,
        int minDistinct,
        Optional<Integer> shallowTipLimit,
        Optional<Integer> deepBowLimit,
        boolean forbidAllShallowest,
        boolean forbidAllDeepest) {

    /** The shop's standing rules. */
    public static final CuttingRules DEFAULT = new CuttingRules();

    public CuttingRules {
        if (maxRun < 1) {
            throw new IllegalArgumentException("max_run of zero forbids every key");
        }
        if (minDistinct < 1) {
            throw new IllegalArgumentException("a key uses at least one depth");
        }
    }

    /**
     * Runs of three at most, two depths at least, no limit at either end, and neither end of the
     * chart alone.
     */
    public CuttingRules() {
        this(3, 2, Optional.empty(), Optional.empty(), true, true);
    }

    public CuttingRules withMaxRun(int value) {
        return new CuttingRules(
                value, minDistinct, shallowTipLimit, deepBowLimit, forbidAllShallowest, forbidAllDeepest);
    }

    public CuttingRules withMinDistinct(int value) {
        return new CuttingRules(maxRun, value, shallowTipLimit, deepBowLimit, forbidAllShallowest, forbidAllDeepest);
    }

    public CuttingRules withShallowTipLimit(int value) {
        return new CuttingRules(
                maxRun, minDistinct, Optional.of(value), deepBowLimit, forbidAllShallowest, forbidAllDeepest);
    }

    /** The same rules with the first cut, the one against the shoulder, held to this depth or less. */
    public CuttingRules withDeepBowLimit(int value) {
        return new CuttingRules(
                maxRun, minDistinct, shallowTipLimit, Optional.of(value), forbidAllShallowest, forbidAllDeepest);
    }

    public CuttingRules withForbidAllShallowest(boolean value) {
        return new CuttingRules(maxRun, minDistinct, shallowTipLimit, deepBowLimit, value, forbidAllDeepest);
    }

    public CuttingRules withForbidAllDeepest(boolean value) {
        return new CuttingRules(maxRun, minDistinct, shallowTipLimit, deepBowLimit, forbidAllShallowest, value);
    }
}
