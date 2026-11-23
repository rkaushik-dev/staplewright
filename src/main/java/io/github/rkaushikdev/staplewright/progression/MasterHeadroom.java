package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;

/**
 * What one master has left of the change keys it was handed.
 *
 * @param master the master the keys were handed to
 * @param room its allocation counted the way a whole array is: given, cut, and the reserve
 */
public record MasterHeadroom(KeySymbol master, Headroom room) {

    /** Whether the master is into the keys it should be keeping back. */
    public boolean underReserve() {
        return room.underReserve();
    }

    /** One line for the capacity page, led by the master. */
    public String describe() {
        return master.render() + ": " + room.describe();
    }
}
