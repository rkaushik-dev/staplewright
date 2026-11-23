package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One opening as a surveyor found it.
 *
 * <p>Every opening on a walk gets a condition, a manufacturer if the lock is old enough to have lost
 * its label, and a note about anything that will make the rekey take longer than twenty minutes.
 * Some deficiencies block a rekey outright: worn out hardware, a frame or strike that needs work,
 * and a keyed opening with nothing in it all have to be dealt with before a rekey is worth booking.
 *
 * @param opening the opening number
 * @param surveyedOn the day it was looked at
 * @param surveyor who walked it
 * @param condition how the hardware is doing
 * @param manufacturer who made the lock, when anybody could tell
 * @param cylinderSerial the serial on the cylinder, when it could be read
 * @param deficiencies what was found, in the order it was written down, each once
 * @param note anything else worth knowing
 */
public record SurveyLine(
        String opening,
        LocalDate surveyedOn,
        String surveyor,
        Condition condition,
        String manufacturer,
        String cylinderSerial,
        List<Deficiency> deficiencies,
        String note) {

    /**
     * A line, refusing one with no opening or no surveyor, and one listing a deficiency twice.
     *
     * @throws InputError for a blank opening or surveyor
     * @throws ConflictError for a repeated deficiency
     */
    public SurveyLine {
        deficiencies = List.copyOf(deficiencies);
        if (Text.isBlank(opening)) {
            throw new InputError("a survey line names an opening");
        }
        if (Text.isBlank(surveyor)) {
            throw new InputError("a survey line names who walked it", Facts.of("opening", opening));
        }
        if (new HashSet<>(deficiencies).size() != deficiencies.size()) {
            throw new ConflictError("a deficiency is listed twice", Facts.of("opening", opening));
        }
    }

    /** A line with nothing found and nothing else written down. */
    public SurveyLine(String opening, LocalDate surveyedOn, String surveyor, Condition condition) {
        this(opening, surveyedOn, surveyor, condition, "", "", List.of(), "");
    }

    /** Whether something has to happen before a rekey is worth booking. */
    public boolean needsWorkFirst() {
        return deficiencies.stream().anyMatch(Deficiency::blocksRekey);
    }

    /** Whether the door breaks the life safety code, which outranks every other thing found. */
    public boolean breaksLifeSafety() {
        return deficiencies.stream().anyMatch(Deficiency::isLifeSafety);
    }

    /** Whether the hardware is good or merely worn, with nothing found. */
    public boolean isSound() {
        return condition.value() <= Condition.WORN.value() && deficiencies.isEmpty();
    }

    /** One line for the survey: {@code MOO-214 good: nothing to report}. */
    public String describe() {
        String found = deficiencies.isEmpty()
                ? "nothing to report"
                : deficiencies.stream().map(Deficiency::value).collect(Collectors.joining(", "));
        return opening + " " + condition.label() + ": " + found;
    }

    /** A builder starting from this line, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A line put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String opening;
        private LocalDate surveyedOn;
        private String surveyor;
        private Condition condition;
        private String manufacturer;
        private String cylinderSerial;
        private List<Deficiency> deficiencies;
        private String note;

        private Builder(SurveyLine from) {
            opening = from.opening;
            surveyedOn = from.surveyedOn;
            surveyor = from.surveyor;
            condition = from.condition;
            manufacturer = from.manufacturer;
            cylinderSerial = from.cylinderSerial;
            deficiencies = from.deficiencies;
            note = from.note;
        }

        public Builder opening(String value) {
            opening = value;
            return this;
        }

        public Builder surveyedOn(LocalDate value) {
            surveyedOn = value;
            return this;
        }

        public Builder surveyor(String value) {
            surveyor = value;
            return this;
        }

        public Builder condition(Condition value) {
            condition = value;
            return this;
        }

        public Builder manufacturer(String value) {
            manufacturer = value;
            return this;
        }

        public Builder cylinderSerial(String value) {
            cylinderSerial = value;
            return this;
        }

        public Builder deficiencies(List<Deficiency> value) {
            deficiencies = value;
            return this;
        }

        /** The deficiencies found, in the order they are given. */
        public Builder deficiencies(Deficiency... value) {
            deficiencies = List.of(value);
            return this;
        }

        public Builder note(String value) {
            note = value;
            return this;
        }

        public SurveyLine build() {
            return new SurveyLine(
                    opening, surveyedOn, surveyor, condition, manufacturer, cylinderSerial, deficiencies, note);
        }
    }
}
