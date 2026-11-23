package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.Relations;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.spec.Rules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Incidental keys, which are the ones the shop never meant to make.
 *
 * <p>A cylinder does not know what a key symbol is. It knows that in chamber one it breaks at depth
 * 3 and at depth 7, and in chamber two at 4 and 6, and so on. Any key carrying one of the break
 * depths in every chamber turns the plug, whether or not anybody drew it on a schedule.
 *
 * <p>So a cylinder pinned for a master and three change keys accepts every combination of its break
 * depths, and the difference between those combinations and the keys on the schedule is the set of
 * incidental keys. They are real. They open the door. Nobody has one, which is the only thing that
 * makes them tolerable, and a system that generates hundreds of them is a system where somebody
 * eventually cuts one by accident and cannot explain why it works.
 *
 * <p>Cross keying is the usual cause. Put two unrelated change keys on one cylinder and the
 * incidental count goes up hard, which is why the shop asks what a request is for before it says
 * yes.
 */
public final class Incidentals {

    private Incidentals() {}

    /**
     * Every depth each chamber has to break at to take all of these keys, keyed by chamber, each
     * run shallow to deep.
     *
     * @throws InputError for no keys at all, or keys of different lengths
     */
    public static Map<Integer, List<Integer>> breakDepths(List<Bitting> keys) {
        if (keys.isEmpty()) {
            throw new InputError("a cylinder with no keys breaks nowhere");
        }
        return Relations.wafersFor(keys.get(0), keys.subList(1, keys.size()));
    }

    /**
     * Every key that turns a cylinder pinned to take the ones given.
     *
     * <p>Produced in chamber order with the last chamber turning over fastest, which puts the
     * intended keys near each other in the listing. The keys are made as the stream is read.
     */
    public static Stream<Bitting> operatingKeys(List<Bitting> keys) {
        return Bitting.everyCombination(List.copyOf(breakDepths(keys).values()));
    }

    /** How many keys turn the plug, without building any of them. */
    public static int operatingCount(List<Bitting> keys) {
        return breakDepths(keys).values().stream().mapToInt(List::size).reduce(1, Math::multiplyExact);
    }

    /** Keys the cylinder accepts that nobody put on the schedule, in the operating key order. */
    public static List<Bitting> incidentalKeys(List<Bitting> keys) {
        Set<Bitting> intended = new HashSet<>(keys);
        return operatingKeys(keys).filter(key -> !intended.contains(key)).toList();
    }

    /** How many keys the cylinder accepts beyond the ones it was pinned for. */
    public static int incidentalCount(List<Bitting> keys) {
        return operatingCount(keys) - new HashSet<>(keys).size();
    }

    /** The incidental keys somebody could walk out with under the standing rules. */
    public static List<Bitting> cuttableIncidentals(DepthAndSpaceChart chart, List<Bitting> keys) {
        return cuttableIncidentals(chart, keys, CuttingRules.DEFAULT);
    }

    /**
     * The incidental keys somebody could actually walk out with.
     *
     * <p>An incidental key that breaks macs still opens the door, but no machine in the shop will
     * cut it and no factory blank arrives that way. The ones that pass the rules are the ones worth
     * losing sleep over.
     */
    public static List<Bitting> cuttableIncidentals(DepthAndSpaceChart chart, List<Bitting> keys, CuttingRules rules) {
        return incidentalKeys(keys).stream()
                .filter(key -> Rules.checkCuts(chart, key, rules).isEmpty())
                .toList();
    }

    /** Whether a cylinder takes its keys and nothing else. */
    public static boolean isClean(List<Bitting> keys) {
        return incidentalCount(keys) == 0;
    }

    /** The chamber carrying the most breaks, which is where to look first; the shallower wins a tie. */
    public static int worstChamber(List<Bitting> keys) {
        Map<Integer, List<Integer>> breaks = breakDepths(keys);
        return breaks.keySet().stream()
                .max(Comparator.<Integer>comparingInt(
                                chamber -> breaks.get(chamber).size())
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    /** How many new incidental keys adding one more key to a cylinder makes. */
    public static int crossKeyingCost(List<Bitting> intended, Bitting extra) {
        int before = incidentalCount(intended);
        List<Bitting> widened = new ArrayList<>(intended);
        widened.add(extra);
        return incidentalCount(widened) - before;
    }

    /**
     * Every key that turns every one of a run of cylinders, each given as the keys it was pinned
     * for, in the operating key order.
     *
     * <p>Walk a corridor and the question is not what opens one door but what opens all of them. A
     * key does when, in every chamber, it carries a depth every cylinder breaks at. The master over
     * the corridor ought to, and so should anything above it. Anything else is a key that was never
     * drawn as reaching that far, made out of the overlap between doors that were each pinned
     * correctly on their own.
     *
     * @throws InputError for no cylinders, a cylinder with no keys, or keys of different lengths
     */
    public static List<Bitting> commonKeys(List<List<Bitting>> cylinders) {
        if (cylinders.isEmpty()) {
            throw new InputError("a run of no cylinders has nothing in common");
        }
        List<Map<Integer, List<Integer>>> breaks =
                cylinders.stream().map(Incidentals::breakDepths).toList();
        int chambers = breaks.get(0).size();
        if (breaks.stream().anyMatch(cylinder -> cylinder.size() != chambers)) {
            throw new InputError("the cylinders are not all the same length", Facts.of("chambers", chambers));
        }
        List<List<Integer>> shared = new ArrayList<>();
        for (int chamber = 1; chamber <= chambers; chamber++) {
            int at = chamber;
            List<Integer> depths = breaks.get(0).get(chamber).stream()
                    .filter(depth -> breaks.stream()
                            .allMatch(cylinder -> cylinder.get(at).contains(depth)))
                    .toList();
            if (depths.isEmpty()) {
                return List.of();
            }
            shared.add(depths);
        }
        return Bitting.everyCombination(shared).toList();
    }

    /** The keys that turn every cylinder in the run and are not among the ones named, in order. */
    public static List<Bitting> unintendedCommonKeys(List<List<Bitting>> cylinders, Collection<Bitting> intended) {
        Set<Bitting> named = Set.copyOf(intended);
        return commonKeys(cylinders).stream()
                .filter(key -> !named.contains(key))
                .toList();
    }
}
