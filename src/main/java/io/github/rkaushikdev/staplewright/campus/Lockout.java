package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * One lockout call, from the telephone ringing to the door opening.
 *
 * <p>Somebody is standing outside a door without a key. A technician goes, checks they belong
 * there, lets them in and writes it down. It sounds trivial and it is not: the log is the only
 * evidence the shop has when a department claims its people never get locked out, and the after
 * hours calls are the argument for a card reader on that door.
 *
 * @param number the call number
 * @param opening the door they were locked out of
 * @param keyholder who was locked out
 * @param calledAt when the telephone rang
 * @param state where the call has got to
 * @param attendedAt when a technician arrived, never before the call
 * @param closedAt when the call closed
 * @param technician who attended
 * @param identifiedBy how the person was identified
 * @param note what was written when it closed
 */
public record Lockout(
        String number,
        String opening,
        String keyholder,
        LocalDateTime calledAt,
        CallState state,
        Optional<LocalDateTime> attendedAt,
        Optional<LocalDateTime> closedAt,
        String technician,
        Identification identifiedBy,
        String note) {

    /** The time of day after hours begins. */
    public static final LocalTime AFTER_HOURS_START = LocalTime.of(17, 0);

    /** The time of day after hours ends. */
    public static final LocalTime AFTER_HOURS_END = LocalTime.of(7, 0);

    private static final DateTimeFormatter TO_THE_MINUTE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    /**
     * A call, refusing one with no number and one attended before it was made.
     *
     * @throws InputError for a blank number
     * @throws ConflictError when the attendance comes before the call
     */
    public Lockout {
        if (Text.isBlank(number)) {
            throw new InputError("a lockout call needs a number");
        }
        if (attendedAt.isPresent() && attendedAt.get().isBefore(calledAt)) {
            throw new ConflictError("nobody attended before the call", Facts.of("number", number));
        }
    }

    /** A call just logged, nobody on the way yet and nobody identified. */
    public Lockout(String number, String opening, String keyholder, LocalDateTime calledAt) {
        this(
                number,
                opening,
                keyholder,
                calledAt,
                CallState.LOGGED,
                Optional.empty(),
                Optional.empty(),
                "",
                Identification.NONE,
                "");
    }

    /** Whether the call is still waiting for somebody. */
    public boolean isOpen() {
        return state.isOpen();
    }

    /**
     * Whether the telephone rang outside the working day: at or after five in the evening, before
     * seven in the morning, or at any hour of a Saturday or a Sunday, when whoever goes is on call.
     */
    public boolean afterHours() {
        if (calledAt.getDayOfWeek() == DayOfWeek.SATURDAY || calledAt.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        LocalTime struck = calledAt.toLocalTime();
        return !struck.isBefore(AFTER_HOURS_START) || struck.isBefore(AFTER_HOURS_END);
    }

    /** How many whole minutes the person stood there, or nothing when nobody has been. */
    public Optional<Integer> responseMinutes() {
        return attendedAt.map(
                attended -> Math.toIntExact(Duration.between(calledAt, attended).toMinutes()));
    }

    /** One line for the log: {@code L-001 MOO-214 kmaki 2025-04-08 14:12 logged}. */
    public String describe() {
        return number + " " + opening + " " + keyholder + " " + calledAt.format(TO_THE_MINUTE) + " " + state.value();
    }

    /** A builder starting from this call, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A call put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String number;
        private String opening;
        private String keyholder;
        private LocalDateTime calledAt;
        private CallState state;
        private Optional<LocalDateTime> attendedAt;
        private Optional<LocalDateTime> closedAt;
        private String technician;
        private Identification identifiedBy;
        private String note;

        private Builder(Lockout from) {
            number = from.number;
            opening = from.opening;
            keyholder = from.keyholder;
            calledAt = from.calledAt;
            state = from.state;
            attendedAt = from.attendedAt;
            closedAt = from.closedAt;
            technician = from.technician;
            identifiedBy = from.identifiedBy;
            note = from.note;
        }

        public Builder number(String value) {
            number = value;
            return this;
        }

        public Builder opening(String value) {
            opening = value;
            return this;
        }

        public Builder keyholder(String value) {
            keyholder = value;
            return this;
        }

        public Builder calledAt(LocalDateTime value) {
            calledAt = value;
            return this;
        }

        public Builder state(CallState value) {
            state = value;
            return this;
        }

        public Builder attendedAt(LocalDateTime value) {
            attendedAt = Optional.of(value);
            return this;
        }

        public Builder attendedAt(Optional<LocalDateTime> value) {
            attendedAt = value;
            return this;
        }

        public Builder closedAt(LocalDateTime value) {
            closedAt = Optional.of(value);
            return this;
        }

        public Builder closedAt(Optional<LocalDateTime> value) {
            closedAt = value;
            return this;
        }

        public Builder technician(String value) {
            technician = value;
            return this;
        }

        public Builder identifiedBy(Identification value) {
            identifiedBy = value;
            return this;
        }

        public Builder note(String value) {
            note = value;
            return this;
        }

        public Lockout build() {
            return new Lockout(
                    number, opening, keyholder, calledAt, state, attendedAt, closedAt, technician, identifiedBy, note);
        }
    }
}
