package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.progression.Groups;
import io.github.rkaushikdev.staplewright.progression.Incidentals;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * Every cut the shop will not issue, with the reason it will not.
 *
 * <p>A rekey retires a cut. The old key still exists somewhere, in a drawer or on a ring or in a
 * pocket in another state, so putting that cut back into service on a different door hands somebody
 * a key they did not know they had. There are two kinds of retirement, and the difference is the
 * whole point. A cut retired because the system was reorganised is only superseded, and after long
 * enough the shop will use it again rather than shrink the array forever. A cut retired because a
 * key went missing is compromised and never comes back, no matter how many years pass or who asks.
 * The quarantine walks its entries in cut order.
 */
public final class Quarantine implements Iterable<RetiredBitting> {

    /** How many years a cut that is not out for good sits out, unless told otherwise. */
    public static final int RELEASE_AFTER_YEARS = 7;

    private static final Comparator<RetiredBitting> BY_DAY_THEN_CUT = Comparator.comparing(RetiredBitting::retiredOn)
            .thenComparing(entry -> entry.bitting().render());

    private final int releaseAfterYears;
    private final Map<List<Integer>, RetiredBitting> retired = new LinkedHashMap<>();

    /** A quarantine with the standing seven year window. */
    public Quarantine() {
        this(RELEASE_AFTER_YEARS);
    }

    /**
     * A quarantine releasing cuts that are not out for good after this many years.
     *
     * @throws IllegalArgumentException for a window shorter than a year
     */
    public Quarantine(int releaseAfterYears) {
        if (releaseAfterYears < 1) {
            throw new IllegalArgumentException("a release window is at least a year");
        }
        this.releaseAfterYears = releaseAfterYears;
    }

    /** How many years a cut that is not out for good sits out. */
    public int releaseAfterYears() {
        return releaseAfterYears;
    }

    /** Take a cut out of service with no symbol and nothing written about it. */
    public RetiredBitting retire(Bitting bitting, RetirementReason reason, LocalDate when) {
        return retire(bitting, reason, when, "", "");
    }

    /** Take a cut out of service, naming the symbol it was cut for. */
    public RetiredBitting retire(Bitting bitting, RetirementReason reason, LocalDate when, String symbol) {
        return retire(bitting, reason, when, symbol, "");
    }

    /**
     * Take a cut out of service. A compromise wins over any softer retirement already held; any
     * other second retirement is refused.
     *
     * @throws ConflictError when the cut is already retired and this is not a compromise over a
     *     softer reason, naming the reason held
     */
    public RetiredBitting retire(Bitting bitting, RetirementReason reason, LocalDate when, String symbol, String note) {
        RetiredBitting held = retired.get(bitting.depths());
        if (held != null && (held.isPermanent() || !reason.isPermanent())) {
            throw new ConflictError(
                    "that cut is already retired",
                    Facts.of(
                            "bitting", bitting.render(), "reason", held.reason().value()));
        }
        RetiredBitting entry = new RetiredBitting(bitting, reason, when, symbol, note);
        retired.put(bitting.depths(), entry);
        return entry;
    }

    /**
     * The entry for one cut.
     *
     * @throws NotFoundError when the cut is not retired
     */
    public RetiredBitting entry(Bitting bitting) {
        RetiredBitting found = retired.get(bitting.depths());
        if (found == null) {
            throw new NotFoundError("that cut is not retired", Facts.of("bitting", bitting.render()));
        }
        return found;
    }

    /**
     * Put a cut back deliberately. Refused for anything compromised.
     *
     * @throws NotFoundError when the cut is not retired
     * @throws ConflictError when it is out for good
     */
    public RetiredBitting release(Bitting bitting) {
        RetiredBitting found = entry(bitting);
        if (found.isPermanent()) {
            throw new ConflictError(
                    "a compromised cut does not come back",
                    Facts.of(
                            "bitting",
                            bitting.render(),
                            "retired_on",
                            found.retiredOn().toString()));
        }
        retired.remove(bitting.depths());
        return found;
    }

    /** Whether the cut is retired at all, however long ago. */
    public boolean isRetired(Bitting bitting) {
        return retired.containsKey(bitting.depths());
    }

    /** Whether the shop would refuse this cut today, letting through one that has sat out its window. */
    public boolean isRetired(Bitting bitting, LocalDate today) {
        RetiredBitting found = retired.get(bitting.depths());
        return found != null && !found.releasedBy(today, releaseAfterYears);
    }

    /** A run of candidate cuts with every retired one dropped, in the order given. */
    public List<Bitting> available(Iterable<Bitting> bittings) {
        return StreamSupport.stream(bittings.spliterator(), false)
                .filter(bitting -> !isRetired(bitting))
                .toList();
    }

    /** A run of candidate cuts with the ones still refused today dropped, in the order given. */
    public List<Bitting> available(Iterable<Bitting> bittings, LocalDate today) {
        return StreamSupport.stream(bittings.spliterator(), false)
                .filter(bitting -> !isRetired(bitting, today))
                .toList();
    }

    /** The cuts out for good, by the day they were retired and then by cut. */
    public List<RetiredBitting> permanent() {
        return sortedWhere(RetiredBitting::isPermanent);
    }

    /** Cuts that have sat out their window and could be used again, by day retired and then by cut. */
    public List<RetiredBitting> releasable(LocalDate today) {
        return sortedWhere(entry -> entry.releasedBy(today, releaseAfterYears));
    }

    /**
     * Retired cuts that would still turn a cylinder pinned for these keys, by day retired and then
     * by cut, leaving out any that have sat out their window by today.
     *
     * <p>A cylinder turns for any key that carries one of its break depths in every chamber. A
     * rekey that recuts the change key and leaves the master alone keeps the master break in every
     * chamber, so the missing key still opens the door wherever it shared the master cut and the new
     * key covers the rest. Nobody notices, because the new key works and the old one is in a pocket
     * in another state. This is the check that catches it before the cylinder goes back in the door.
     *
     * @param keys every key the rebuilt cylinder is pinned for, masters included
     * @throws InputError for no keys, or keys of different lengths
     */
    public List<RetiredBitting> stillTurning(List<Bitting> keys, LocalDate today) {
        Map<Integer, List<Integer>> breaks = Incidentals.breakDepths(keys);
        return sortedWhere(entry -> !entry.releasedBy(today, releaseAfterYears)
                && entry.bitting().chambers() == breaks.size()
                && breaks.entrySet().stream().allMatch(chamber -> chamber.getValue()
                        .contains(entry.bitting().at(chamber.getKey()))));
    }

    /**
     * Every keying group on a schedule whose cylinder a retired cut would still turn, one finding a
     * cut, in the order the groups are given and then by day retired.
     *
     * <p>The finding grades are the schedule review's, not an incident's. A compromised cut turning a cylinder stops the job, because the rekey has not done the one
     * thing it was for. A superseded cut that still turns one is serious rather than blocking: the
     * key is somewhere the shop put it, not somewhere it lost it. A group is judged on the keys in it
     * that are cut so far.
     */
    public List<Finding> stillTurningGroups(KeyingSchedule schedule, List<KeyingGroup> groups, LocalDate today) {
        List<Finding> found = new ArrayList<>();
        for (KeyingGroup group : groups) {
            List<Bitting> keys = Groups.cutSoFar(schedule, group);
            if (keys.isEmpty()) {
                continue;
            }
            for (RetiredBitting entry : stillTurning(keys, today)) {
                found.add(new Finding(
                        "retired_cut_turns",
                        entry.isPermanent()
                                ? io.github.rkaushikdev.staplewright.progression.Severity.BLOCKING
                                : io.github.rkaushikdev.staplewright.progression.Severity.SERIOUS,
                        "group " + group.name() + " still turns for "
                                + entry.bitting().render() + ", retired "
                                + entry.reason().value() + " on " + entry.retiredOn()));
            }
        }
        return List.copyOf(found);
    }

    /** Whether no retired cut still in quarantine today turns a cylinder pinned for these keys. */
    public boolean isSafeToInstall(List<Bitting> keys, LocalDate today) {
        return stillTurning(keys, today).isEmpty();
    }

    private List<RetiredBitting> sortedWhere(Predicate<RetiredBitting> keep) {
        return retired.values().stream().filter(keep).sorted(BY_DAY_THEN_CUT).toList();
    }

    /** How many cuts are out for each reason, keyed by the reason as written, in reason order. */
    public Map<String, Integer> byReason() {
        Map<String, Integer> counts = new TreeMap<>();
        retired.values().forEach(entry -> counts.merge(entry.reason().value(), 1, Integer::sum));
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /** Whether this is a cut the quarantine holds. Anything that is not a cut is never held. */
    public boolean contains(Object bitting) {
        return bitting instanceof Bitting cut && retired.containsKey(cut.depths());
    }

    /** Every entry, in cut order. */
    @Override
    public Iterator<RetiredBitting> iterator() {
        return retired.values().stream()
                .sorted(Comparator.comparing(entry -> entry.bitting().render()))
                .toList()
                .iterator();
    }

    /** How many cuts are out of service. */
    public int size() {
        return retired.size();
    }
}
