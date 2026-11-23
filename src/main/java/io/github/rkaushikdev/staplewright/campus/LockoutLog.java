package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Tallies;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every lockout call, open and closed, in the order the calls came in.
 *
 * <p>Lockout calls are half the shop's telephone traffic. Two rules do the work. Somebody who
 * cannot be identified does not get let in. And a door that generates lockouts every week is a door
 * with the wrong hardware on it, so the count by opening is the report that gets taken to a budget
 * meeting.
 */
public final class LockoutLog implements Iterable<Lockout> {

    /** How many calls make somebody a repeat caller unless told otherwise. */
    public static final int REPEAT_CALLS = 3;

    private final Registry<Lockout> calls = new Registry<>("lockout call", "number");

    /**
     * Log a call just in.
     *
     * @throws ConflictError when the call is not freshly logged, or the number is taken
     */
    public Lockout log(Lockout call) {
        if (call.state() != CallState.LOGGED) {
            throw new ConflictError("a call starts logged", Facts.of("number", call.number()));
        }
        return calls.add(call.number(), call);
    }

    /**
     * The call with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is none
     */
    public Lockout call(String number) {
        return calls.get(number);
    }

    /** Put a call back as it was stored, in whatever state it reached. */
    public Lockout load(Lockout call) {
        return calls.put(call.number(), call);
    }

    /**
     * A technician arrives.
     *
     * @throws ConflictError when the call is not logged, or the arrival comes before the call
     * @throws InputError when nobody is named as attending
     */
    public Lockout attend(String number, String technician, LocalDateTime when) {
        Lockout call = mustBe(number, List.of(CallState.LOGGED));
        if (Text.isBlank(technician)) {
            throw new InputError("somebody attends a call", Facts.of("number", number));
        }
        if (when.isBefore(call.calledAt())) {
            throw new ConflictError("nobody attended before the call", Facts.of("number", number));
        }
        return store(call.toBuilder()
                .state(CallState.ATTENDED)
                .technician(technician)
                .attendedAt(when)
                .build());
    }

    /**
     * Let them in, refusing to record an unidentified entry as opened.
     *
     * @throws ConflictError when nobody has attended yet, or the person was not identified
     */
    public Lockout openDoor(String number, Identification how, LocalDateTime when) {
        Lockout call = mustBe(number, List.of(CallState.ATTENDED));
        if (how == Identification.NONE) {
            throw new ConflictError("an unidentified person does not get let in", Facts.of("number", number));
        }
        return store(call.toBuilder()
                .state(CallState.OPENED)
                .identifiedBy(how)
                .closedAt(when)
                .build());
    }

    /**
     * The technician will not open the door. A refusal says why.
     *
     * @throws ConflictError when nobody has attended yet
     * @throws InputError when the note is blank
     */
    public Lockout refuse(String number, LocalDateTime when, String note) {
        Lockout call = mustBe(number, List.of(CallState.ATTENDED));
        if (Text.isBlank(note)) {
            throw new InputError("a refusal says why", Facts.of("number", number));
        }
        return store(call.toBuilder()
                .state(CallState.REFUSED)
                .closedAt(when)
                .note(note)
                .build());
    }

    /** They found their key while the technician was on the way. */
    public Lockout withdraw(String number, LocalDateTime when) {
        return withdraw(number, when, "");
    }

    /**
     * They found their key while the technician was on the way, with a word about it.
     *
     * @throws ConflictError when the call is already closed
     */
    public Lockout withdraw(String number, LocalDateTime when, String note) {
        Lockout call = mustBe(number, List.of(CallState.LOGGED, CallState.ATTENDED));
        return store(call.toBuilder()
                .state(CallState.WITHDRAWN)
                .closedAt(when)
                .note(note)
                .build());
    }

    private Lockout mustBe(String number, List<CallState> states) {
        Lockout call = calls.get(number);
        Lifecycle.requireState(call.state(), states, "the call is not in a state for that", Facts.of("number", number));
        return call;
    }

    private Lockout store(Lockout call) {
        return calls.put(call.number(), call);
    }

    /** The calls nobody has closed, in the order they came in. */
    public List<Lockout> openCalls() {
        return calls.where(Lockout::isOpen);
    }

    /** Every call about one door, in the order they came in. */
    public List<Lockout> forOpening(String opening) {
        return calls.where(call -> call.opening().equals(opening));
    }

    /** Every call from one person, in the order they came in. */
    public List<Lockout> forKeyholder(String keyholder) {
        return calls.where(call -> call.keyholder().equals(keyholder));
    }

    /** The calls made after hours, in the order they came in. */
    public List<Lockout> afterHours() {
        return calls.where(Lockout::afterHours);
    }

    /** Which doors generate the calls, worst first and a tie in number order. */
    public Map<String, Integer> countsByOpening() {
        return Tallies.count(calls.values(), Lockout::opening);
    }

    /**
     * Which doors generated the calls made on or after a day, worst first and a tie in number
     * order. A door that was fixed in the summer should stop showing up in the autumn's report.
     */
    public Map<String, Integer> countsByOpening(LocalDate since) {
        return Tallies.count(callsSince(since), Lockout::opening);
    }

    /** The people who have called three times or more, in identifier order. */
    public List<String> repeatCallers() {
        return repeatCallers(REPEAT_CALLS);
    }

    /** The people who have called at least {@code atLeast} times, in identifier order. */
    public List<String> repeatCallers(int atLeast) {
        return repeatCallers(atLeast, LocalDate.MIN);
    }

    /**
     * The people who have called at least {@code atLeast} times on or after a day, in identifier
     * order.
     *
     * <p>The repeat caller rule is about a habit, and three calls across four years is not one. The
     * shop counts over the term, so a student locked out twice in first year and once in final year
     * is not sent the letter.
     */
    public List<String> repeatCallers(int atLeast, LocalDate since) {
        return Tallies.count(callsSince(since), Lockout::keyholder).entrySet().stream()
                .filter(entry -> entry.getValue() >= atLeast)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private List<Lockout> callsSince(LocalDate since) {
        return calls.where(call -> !call.calledAt().toLocalDate().isBefore(since));
    }

    /** Mean whole minutes to attend, across the calls anybody has attended, or nothing when none. */
    public Optional<Integer> averageResponse() {
        List<Integer> minutes = calls.values().stream()
                .map(Lockout::responseMinutes)
                .flatMap(Optional::stream)
                .toList();
        if (minutes.isEmpty()) {
            return Optional.empty();
        }
        long total = minutes.stream().mapToLong(Integer::longValue).sum();
        return Optional.of((int) (total / (double) minutes.size()));
    }

    /** Whether a call with this number is in the log. */
    public boolean contains(Object number) {
        return calls.contains(number);
    }

    /** Every call, in the order they came in. */
    @Override
    public Iterator<Lockout> iterator() {
        return calls.iterator();
    }

    /** How many calls the log holds. */
    public int size() {
        return calls.size();
    }
}
