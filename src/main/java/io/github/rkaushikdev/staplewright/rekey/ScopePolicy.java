package io.github.rkaushikdev.staplewright.rekey;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * What the shop has decided to do about missing keys.
 *
 * @param recutAllOnRepin whether repinning a cylinder means recutting every key that opens it
 * @param alwaysRekeyExterior whether an exterior door is always rekeyed, however few keys it takes
 * @param alwaysRekeyMasters whether a lost master always means a rekey
 * @param ignoreBelowOpenings a loss opening fewer doors than this is not rekeyed
 * @param cylinderCap the most cylinders a rekey may touch before somebody signs for it
 * @param rekeyUnreturned whether a key a leaver never handed back is treated like a stolen one
 */
public record ScopePolicy(
        boolean recutAllOnRepin,
        boolean alwaysRekeyExterior,
        boolean alwaysRekeyMasters,
        int ignoreBelowOpenings,
        Optional<Integer> cylinderCap,
        boolean rekeyUnreturned) {

    /** The shop's standing policy. */
    public static final ScopePolicy DEFAULT = new ScopePolicy();

    public ScopePolicy {
        if (ignoreBelowOpenings < 0) {
            throw new IllegalArgumentException("a threshold is a count of openings");
        }
        if (cylinderCap.isPresent() && cylinderCap.get() < 1) {
            throw new IllegalArgumentException("a cap of nothing rekeys nothing");
        }
    }

    /** Exteriors and masters always rekeyed, no threshold, no cap, and an unreturned key only lost. */
    public ScopePolicy() {
        this(false, true, true, 0, Optional.empty(), false);
    }

    /** The same policy with some of it changed, checked again once all of it is. */
    public ScopePolicy with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(this)).build();
    }

    /** A policy put together one decision at a time from an existing one. */
    public static final class Builder {
        private boolean recutAllOnRepin;
        private boolean alwaysRekeyExterior;
        private boolean alwaysRekeyMasters;
        private int ignoreBelowOpenings;
        private Optional<Integer> cylinderCap;
        private boolean rekeyUnreturned;

        private Builder(ScopePolicy from) {
            recutAllOnRepin = from.recutAllOnRepin;
            alwaysRekeyExterior = from.alwaysRekeyExterior;
            alwaysRekeyMasters = from.alwaysRekeyMasters;
            ignoreBelowOpenings = from.ignoreBelowOpenings;
            cylinderCap = from.cylinderCap;
            rekeyUnreturned = from.rekeyUnreturned;
        }

        public Builder recutAllOnRepin(boolean value) {
            recutAllOnRepin = value;
            return this;
        }

        public Builder alwaysRekeyExterior(boolean value) {
            alwaysRekeyExterior = value;
            return this;
        }

        public Builder alwaysRekeyMasters(boolean value) {
            alwaysRekeyMasters = value;
            return this;
        }

        public Builder ignoreBelowOpenings(int value) {
            ignoreBelowOpenings = value;
            return this;
        }

        public Builder cylinderCap(int value) {
            cylinderCap = Optional.of(value);
            return this;
        }

        public Builder rekeyUnreturned(boolean value) {
            rekeyUnreturned = value;
            return this;
        }

        public ScopePolicy build() {
            return new ScopePolicy(
                    recutAllOnRepin,
                    alwaysRekeyExterior,
                    alwaysRekeyMasters,
                    ignoreBelowOpenings,
                    cylinderCap,
                    rekeyUnreturned);
        }
    }
}
