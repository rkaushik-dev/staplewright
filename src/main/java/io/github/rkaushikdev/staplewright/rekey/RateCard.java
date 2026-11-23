package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Numbers;
import java.util.List;

/**
 * What the shop charges and how long things take.
 *
 * <p>Work at the door outside the working day, on a weekend or overnight in a residence hall, is
 * paid at the after hours rate, given as a percentage of the hourly one. Bench work is always done
 * in the shop in the day, so it never is.
 *
 * @param benchMinutesPerCylinder repinning one cylinder at the bench
 * @param doorMinutesPerCylinder pulling and refitting one cylinder at the door
 * @param coreDoorMinutes swapping an interchangeable core at the door
 * @param keyCutMinutes cutting one key
 * @param travelMinutesPerBuilding getting a crew to a building and back
 * @param labourCentsPerHour what an hour of a locksmith costs
 * @param blankCents one key blank
 * @param pinCents one pin
 * @param pinsPerCylinder pins a repin uses
 * @param afterHoursPercent what an hour outside the working day costs, as a percentage of an
 *     ordinary hour
 */
public record RateCard(
        int benchMinutesPerCylinder,
        int doorMinutesPerCylinder,
        int coreDoorMinutes,
        int keyCutMinutes,
        int travelMinutesPerBuilding,
        int labourCentsPerHour,
        int blankCents,
        int pinCents,
        int pinsPerCylinder,
        int afterHoursPercent) {

    /** The shop's standing rates. */
    public static final RateCard DEFAULT = new RateCard();

    public RateCard {
        List<Integer> fields = List.of(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
        if (fields.stream().anyMatch(value -> value < 0)) {
            throw new InputError("a rate card holds no negative numbers");
        }
        if (labourCentsPerHour == 0) {
            throw new InputError("labour is not free on this campus");
        }
        if (afterHoursPercent < 100) {
            throw new InputError(
                    "nobody works nights for less than the day rate",
                    Facts.of("after_hours_percent", afterHoursPercent));
        }
    }

    /** The shop's standing rates, with time and a half after hours. */
    public RateCard() {
        this(12, 20, 2, 3, 25, 5200, 180, 12, 12, 150);
    }

    /** What a number of minutes of labour costs in whole cents, at the after hours rate when asked. */
    public int labourCents(int minutes, boolean afterHours) {
        int percent = afterHours ? afterHoursPercent : 100;
        return (int) Numbers.round((long) minutes * labourCentsPerHour * percent / 6000.0);
    }

    /** A builder starting from this card, for a copy with some numbers changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A card put together a number at a time, checked once when it is built. */
    public static final class Builder {
        private int benchMinutesPerCylinder;
        private int doorMinutesPerCylinder;
        private int coreDoorMinutes;
        private int keyCutMinutes;
        private int travelMinutesPerBuilding;
        private int labourCentsPerHour;
        private int blankCents;
        private int pinCents;
        private int pinsPerCylinder;
        private int afterHoursPercent;

        private Builder(RateCard from) {
            benchMinutesPerCylinder = from.benchMinutesPerCylinder;
            doorMinutesPerCylinder = from.doorMinutesPerCylinder;
            coreDoorMinutes = from.coreDoorMinutes;
            keyCutMinutes = from.keyCutMinutes;
            travelMinutesPerBuilding = from.travelMinutesPerBuilding;
            labourCentsPerHour = from.labourCentsPerHour;
            blankCents = from.blankCents;
            pinCents = from.pinCents;
            pinsPerCylinder = from.pinsPerCylinder;
            afterHoursPercent = from.afterHoursPercent;
        }

        public Builder benchMinutesPerCylinder(int value) {
            benchMinutesPerCylinder = value;
            return this;
        }

        public Builder doorMinutesPerCylinder(int value) {
            doorMinutesPerCylinder = value;
            return this;
        }

        public Builder coreDoorMinutes(int value) {
            coreDoorMinutes = value;
            return this;
        }

        public Builder keyCutMinutes(int value) {
            keyCutMinutes = value;
            return this;
        }

        public Builder travelMinutesPerBuilding(int value) {
            travelMinutesPerBuilding = value;
            return this;
        }

        public Builder labourCentsPerHour(int value) {
            labourCentsPerHour = value;
            return this;
        }

        public Builder blankCents(int value) {
            blankCents = value;
            return this;
        }

        public Builder pinCents(int value) {
            pinCents = value;
            return this;
        }

        public Builder pinsPerCylinder(int value) {
            pinsPerCylinder = value;
            return this;
        }

        public Builder afterHoursPercent(int value) {
            afterHoursPercent = value;
            return this;
        }

        public RateCard build() {
            return new RateCard(
                    benchMinutesPerCylinder,
                    doorMinutesPerCylinder,
                    coreDoorMinutes,
                    keyCutMinutes,
                    travelMinutesPerBuilding,
                    labourCentsPerHour,
                    blankCents,
                    pinCents,
                    pinsPerCylinder,
                    afterHoursPercent);
        }
    }
}
