package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.InputError;
import java.util.function.UnaryOperator;

/**
 * How many of each kind of key the shop will have out at once.
 *
 * <p>Stamping a key do not duplicate achieves nothing on its own. Any hardware store will cut it,
 * the stamp is not a legal instruction, and the shop has a drawer full of copies to prove it. What
 * works is the restricted keyway, where the blank cannot be bought, and the count, where the shop
 * knows five keys were cut to AA1 and finds six.
 *
 * <p>A grand master is not a department master with more doors. It opens a whole precinct, so
 * the shop holds it to its own ceiling, usually one, while a building's masters go out in pairs.
 *
 * @param changeKeyLimit how many of one change key may be out
 * @param masterLimit how many of one master below the grand masters may be out
 * @param grandMasterLimit how many of one grand master may be out
 * @param topKeyLimit how many of the top key may be out
 * @param restrictedOnlyForMasters whether only masters have to be cut on a restricted blank
 */
public record DuplicationPolicy(
        int changeKeyLimit, int masterLimit, int grandMasterLimit, int topKeyLimit, boolean restrictedOnlyForMasters) {

    /** Four of a change key, two of a master, one grand master, one top key, restricted masters. */
    public static final DuplicationPolicy DEFAULT = new DuplicationPolicy();

    public DuplicationPolicy {
        if (changeKeyLimit < 1 || masterLimit < 1 || grandMasterLimit < 1 || topKeyLimit < 1) {
            throw new InputError("a limit of nothing means no keys at all");
        }
    }

    /** The shop's standing ceilings. */
    public DuplicationPolicy() {
        this(4, 2, 1, 1, true);
    }

    /** The same policy with some of it changed, checked again once all of it is. */
    public DuplicationPolicy with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(this)).build();
    }

    /** How many keys cut to this symbol policy allows out at once. */
    public int ceilingFor(KeySymbol symbol) {
        if (symbol.isTop()) {
            return topKeyLimit;
        }
        if (symbol.isChangeKey()) {
            return changeKeyLimit;
        }
        return symbol.level() == 1 ? grandMasterLimit : masterLimit;
    }

    /** Ceilings put together one at a time from an existing policy. */
    public static final class Builder {
        private int changeKeyLimit;
        private int masterLimit;
        private int grandMasterLimit;
        private int topKeyLimit;
        private boolean restrictedOnlyForMasters;

        private Builder(DuplicationPolicy from) {
            changeKeyLimit = from.changeKeyLimit;
            masterLimit = from.masterLimit;
            grandMasterLimit = from.grandMasterLimit;
            topKeyLimit = from.topKeyLimit;
            restrictedOnlyForMasters = from.restrictedOnlyForMasters;
        }

        public Builder changeKeyLimit(int value) {
            changeKeyLimit = value;
            return this;
        }

        public Builder masterLimit(int value) {
            masterLimit = value;
            return this;
        }

        public Builder grandMasterLimit(int value) {
            grandMasterLimit = value;
            return this;
        }

        public Builder topKeyLimit(int value) {
            topKeyLimit = value;
            return this;
        }

        public Builder restrictedOnlyForMasters(boolean value) {
            restrictedOnlyForMasters = value;
            return this;
        }

        public DuplicationPolicy build() {
            return new DuplicationPolicy(
                    changeKeyLimit, masterLimit, grandMasterLimit, topKeyLimit, restrictedOnlyForMasters);
        }
    }
}
