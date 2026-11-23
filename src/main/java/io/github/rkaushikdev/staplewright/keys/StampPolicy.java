package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.InputError;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * What this shop stamps, and on what.
 *
 * <p>Masters going blind is the rule worth having. A change key with its symbol on it tells a
 * finder which office it opens, which is bad enough. A grand master with its symbol on it tells
 * them what they are holding. Some shops go further for the grand masters and the top key and put
 * nothing on them but the tag, so that even the blind code table, which the counter staff read
 * every day, cannot say which of the keys in the drawer is the one that opens a precinct.
 *
 * @param changeKeys what goes on a change key
 * @param masters what goes on a master below the grand masters
 * @param grandMasters what goes on a grand master or the top key, when it is not what goes on the
 *     masters
 * @param addDoNotDuplicate whether every key also carries the do not duplicate mark
 * @param bowCharacters how many characters fit on a bow
 */
public record StampPolicy(
        Marking changeKeys,
        Marking masters,
        Optional<Marking> grandMasters,
        boolean addDoNotDuplicate,
        int bowCharacters) {

    /** About how many characters a key bow has room for. */
    public static final int BOW_CHARACTERS = 8;

    public StampPolicy {
        if (bowCharacters < 4) {
            throw new InputError("nothing useful fits on a bow that small");
        }
    }

    /** Symbols on change keys, blind codes on every master, do not duplicate on everything. */
    public StampPolicy() {
        this(Marking.SYMBOL, Marking.BLIND, Optional.empty(), true, BOW_CHARACTERS);
    }

    /** The same policy with some of it changed, checked again once all of it is. */
    public StampPolicy with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(this)).build();
    }

    /** What goes on the brass of a key with this symbol. */
    public Marking markingFor(KeySymbol symbol) {
        if (symbol.isChangeKey()) {
            return changeKeys;
        }
        return symbol.level() <= 1 ? grandMasters.orElse(masters) : masters;
    }

    /** A stamping policy put together one decision at a time from an existing one. */
    public static final class Builder {
        private Marking changeKeys;
        private Marking masters;
        private Optional<Marking> grandMasters;
        private boolean addDoNotDuplicate;
        private int bowCharacters;

        private Builder(StampPolicy from) {
            changeKeys = from.changeKeys;
            masters = from.masters;
            grandMasters = from.grandMasters;
            addDoNotDuplicate = from.addDoNotDuplicate;
            bowCharacters = from.bowCharacters;
        }

        public Builder changeKeys(Marking value) {
            changeKeys = value;
            return this;
        }

        public Builder masters(Marking value) {
            masters = value;
            return this;
        }

        /** What goes on the grand masters and the top key, apart from the masters below them. */
        public Builder grandMasters(Marking value) {
            grandMasters = Optional.of(value);
            return this;
        }

        public Builder addDoNotDuplicate(boolean value) {
            addDoNotDuplicate = value;
            return this;
        }

        public Builder bowCharacters(int value) {
            bowCharacters = value;
            return this;
        }

        public StampPolicy build() {
            return new StampPolicy(changeKeys, masters, grandMasters, addDoNotDuplicate, bowCharacters);
        }
    }
}
