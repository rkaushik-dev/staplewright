package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/**
 * What a surveyor found on a door that somebody has to deal with.
 *
 * <p>Some of it stops a rekey: there is no point pinning a cylinder into hardware that is coming
 * off the door. One of it stops more than that. A door on a way out of the building that needs a
 * key or special knowledge to open from the inside, a double cylinder deadbolt on a stair door
 * being the usual one, breaks the life safety code's rule that egress doors open from the egress
 * side without a key, and it goes to the top of the report whatever else is wrong with the
 * building.
 */
public enum Deficiency implements Valued {
    UNIDENTIFIED("cylinder cannot be identified", false, false),
    WRONG_FUNCTION("function does not suit the room", false, false),
    NO_CYLINDER("keyed opening with nothing in it", true, false),
    WORN_OUT("hardware needs replacing before it is rekeyed", true, false),
    NO_STRIKE("frame or strike needs work first", true, false),
    UNKNOWN_KEY("opens to a key nobody has a record of", false, false),
    KEYED_EGRESS("needs a key to get out", true, true);

    private final String value;
    private final boolean blocksRekey;
    private final boolean lifeSafety;

    Deficiency(String value, boolean blocksRekey, boolean lifeSafety) {
        this.value = value;
        this.blocksRekey = blocksRekey;
        this.lifeSafety = lifeSafety;
    }

    /** Whether a door with this wrong has to have other work done before it is worth rekeying. */
    public boolean blocksRekey() {
        return blocksRekey;
    }

    /** Whether this is a life safety problem rather than a hardware one. */
    public boolean isLifeSafety() {
        return lifeSafety;
    }

    @Override
    public String value() {
        return value;
    }

    /** The deficiency written as {@code text}, when there is one. */
    public static Optional<Deficiency> find(String text) {
        return Choices.find(Deficiency.class, text);
    }

    /** The deficiency written as {@code text}, refusing anything else. */
    public static Deficiency fromValue(String text) {
        return Choices.fromValue(Deficiency.class, text, "deficiency");
    }

    @Override
    public String toString() {
        return value;
    }
}
