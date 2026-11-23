package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.Optional;

/**
 * What a deposit costs, by what the key opens, in whole cents.
 *
 * <p>Policy rather than arithmetic, and it lives here so the counter and the settings can both
 * reach it without either of them depending on the other. A shop that wants a grand master to cost
 * more to walk away with than a building master names a third rate; without one the grand masters
 * go at the master rate.
 *
 * @param changeKeyCents the deposit on a change key
 * @param masterCents the deposit on anything that masters
 * @param grandMasterCents the deposit on a grand master or the top key, when it differs
 */
public record DepositRates(int changeKeyCents, int masterCents, Optional<Integer> grandMasterCents) {

    public DepositRates {
        if (changeKeyCents < 0
                || masterCents < 0
                || grandMasterCents.filter(cents -> cents < 0).isPresent()) {
            throw new InputError("a deposit is not negative");
        }
    }

    /** A change key rate and one rate for every master. */
    public DepositRates(int changeKeyCents, int masterCents) {
        this(changeKeyCents, masterCents, Optional.empty());
    }

    /** Twenty five dollars a change key, fifty for anything that masters. */
    public DepositRates() {
        this(2500, 5000);
    }

    /** The same rates with the grand masters charged apart. */
    public DepositRates withGrandMasterCents(int value) {
        return new DepositRates(changeKeyCents, masterCents, Optional.of(value));
    }

    /** The rates in a line for the settings page: {@code 2500 and 5000 cents}, and the grand masters when apart. */
    public String describe() {
        return changeKeyCents + " and " + masterCents + " cents"
                + grandMasterCents.map(cents -> ", grand masters " + cents).orElse("");
    }

    /**
     * The deposit on one key cut to this symbol, issued as a master or not.
     *
     * <p>The request says whether a key masters, because a department sometimes takes a master
     * under a change key's symbol for a plant room. The symbol only decides which master rate.
     */
    public int forKey(KeySymbol symbol, boolean isMaster) {
        if (!isMaster) {
            return changeKeyCents;
        }
        return symbol.level() <= 1 ? grandMasterCents.orElse(masterCents) : masterCents;
    }
}
