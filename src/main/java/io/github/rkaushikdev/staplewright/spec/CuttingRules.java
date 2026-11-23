package io.github.rkaushikdev.staplewright.spec;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Shop policy about what a cut sequence is allowed to look like.
 *
 * <p>{@code maxRun} is how many chambers in a row may carry the same depth. {@code minDistinct} is
 * how many different depths a key has to use overall. {@code shallowTipLimit} caps the depth of the
 * last chamber, because a deep cut at the tip on a worn key lets the plug turn a hair early, and
 * {@code deepBowLimit} caps the first, because the first cut sits right against the shoulder where
 * the blade is already narrowest and a deep one there is where keys snap off in a lock.
 * {@code maxRepeats} caps how often one depth turns up anywhere on the key, which catches the key
 * that dodges the run rule with 3a3a3a and reads at the counter like every other key. The shop
 * will not issue a key that is one long run of the same depth, because it looks like a blank
 * to a student and gets thrown away, and these rules live apart from the chart so that changing
 * policy does not mean editing a manufacturer's numbers.
 */
public record CuttingRules(
        int maxRun,
        int minDistinct,
        Optional<Integer> shallowTipLimit,
        Optional<Integer> deepBowLimit,
        Optional<Integer> maxRepeats,
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
        if (maxRepeats.isPresent() && maxRepeats.get() < 1) {
            throw new IllegalArgumentException("a repeat limit of zero forbids every key");
        }
    }

    /**
     * Runs of three at most, two depths at least, no limit at either end or on repeats, and neither
     * end of the chart alone.
     */
    public CuttingRules() {
        this(3, 2, Optional.empty(), Optional.empty(), Optional.empty(), true, true);
    }

    /** The same rules with some of them changed, checked again once all of them are. */
    public CuttingRules with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(this)).build();
    }

    /** Rules put together one at a time from an existing set. */
    public static final class Builder {
        private int maxRun;
        private int minDistinct;
        private Optional<Integer> shallowTipLimit;
        private Optional<Integer> deepBowLimit;
        private Optional<Integer> maxRepeats;
        private boolean forbidAllShallowest;
        private boolean forbidAllDeepest;

        private Builder(CuttingRules from) {
            maxRun = from.maxRun;
            minDistinct = from.minDistinct;
            shallowTipLimit = from.shallowTipLimit;
            deepBowLimit = from.deepBowLimit;
            maxRepeats = from.maxRepeats;
            forbidAllShallowest = from.forbidAllShallowest;
            forbidAllDeepest = from.forbidAllDeepest;
        }

        public Builder maxRun(int value) {
            maxRun = value;
            return this;
        }

        public Builder minDistinct(int value) {
            minDistinct = value;
            return this;
        }

        public Builder shallowTipLimit(int value) {
            shallowTipLimit = Optional.of(value);
            return this;
        }

        /** The first cut, the one against the shoulder, held to this depth or less. */
        public Builder deepBowLimit(int value) {
            deepBowLimit = Optional.of(value);
            return this;
        }

        /** No depth cut in more than this many chambers anywhere on the key. */
        public Builder maxRepeats(int value) {
            maxRepeats = Optional.of(value);
            return this;
        }

        public Builder forbidAllShallowest(boolean value) {
            forbidAllShallowest = value;
            return this;
        }

        public Builder forbidAllDeepest(boolean value) {
            forbidAllDeepest = value;
            return this;
        }

        public CuttingRules build() {
            return new CuttingRules(
                    maxRun,
                    minDistinct,
                    shallowTipLimit,
                    deepBowLimit,
                    maxRepeats,
                    forbidAllShallowest,
                    forbidAllDeepest);
        }
    }
}
