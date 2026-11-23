package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * One master and the room underneath it, which decides how big a system can ever get.
 *
 * <p>Pick a master. Pick the chambers that are allowed to move. In each of those, the depths a key
 * under the master may use are the chart depths that sit a whole number of steps away from the
 * master cut. Everything else in the key stays where the master put it, and those chambers are the
 * constants.
 *
 * <p>Multiply the counts together and that is the capacity of the system, before macs throws some
 * of them away. The number is usually smaller than people expect. A six pin system on a ten depth
 * chart progressed two steps in four chambers holds a couple of hundred keys, not thousands, and a
 * department that wants six hundred offices on one master is going to hear the word no.
 *
 * @param chart the chart every key is cut against
 * @param master the key everything in the plan sits under
 * @param progressed the chambers that move, counted from one; anything not named stays at the
 *     master cut
 * @param step how many depths apart two cuts in a moving chamber are
 */
public record ProgressionPlan(DepthAndSpaceChart chart, Bitting master, List<Integer> progressed, int step) {

    /** The step the shop progresses by unless told otherwise, which keeps every wafer buildable. */
    public static final int DEFAULT_STEP = 2;

    /**
     * A plan, checked whole.
     *
     * @throws ProgressionError for a master of the wrong length, no moving chamber, a chamber named
     *     twice or off the end, or a chamber with nowhere to go at this step
     */
    public ProgressionPlan {
        progressed = List.copyOf(progressed);
        if (master.chambers() != chart.chambers()) {
            throw new ProgressionError(
                    "the master is not the length of the family",
                    Facts.of("chambers", chart.chambers(), "got", master.chambers()));
        }
        if (progressed.isEmpty()) {
            throw new ProgressionError("a plan with no moving chambers holds one key");
        }
        if (new HashSet<>(progressed).size() != progressed.size()) {
            throw new ProgressionError("a chamber is named twice", Facts.of("progressed", progressed));
        }
        for (int chamber : progressed) {
            if (chamber < 1 || chamber > chart.chambers()) {
                throw new ProgressionError(
                        "no such chamber in this family", Facts.of("chamber", chamber, "chambers", chart.chambers()));
            }
        }
        for (int chamber : progressed) {
            if (availableDepths(chart, master.at(chamber), step).isEmpty()) {
                throw new ProgressionError(
                        "that chamber has nowhere to go at this step",
                        Facts.of("chamber", chamber, "master", master.at(chamber), "step", step));
            }
        }
    }

    /** A plan progressed two steps at a time. */
    public ProgressionPlan(DepthAndSpaceChart chart, Bitting master, List<Integer> progressed) {
        this(chart, master, progressed, DEFAULT_STEP);
    }

    /** The same plan with other chambers moving, checked again. */
    public ProgressionPlan withProgressed(List<Integer> value) {
        return new ProgressionPlan(chart, master, value, step);
    }

    /** The same plan at another step, checked again. */
    public ProgressionPlan withStep(int value) {
        return new ProgressionPlan(chart, master, progressed, value);
    }

    /**
     * Depths a key under this master may use in one chamber, in chart order.
     *
     * <p>The master cut itself is not in the list. A chamber cut at the master depth is a maintained
     * chamber and needs no wafer, and letting it turn up here would quietly produce a key identical
     * to the master.
     *
     * @throws ProgressionError for a step under one, or a master depth the chart does not publish
     */
    public static List<Integer> availableDepths(DepthAndSpaceChart chart, int masterDepth, int step) {
        if (step < 1) {
            throw new ProgressionError("a progression step is at least one", Facts.of("step", step));
        }
        if (!chart.allowsDepth(masterDepth)) {
            throw new ProgressionError(
                    "the master is cut to a depth this chart does not publish",
                    Facts.of("depth", masterDepth, "family", chart.family()));
        }
        return chart.depths().stream()
                .filter(depth -> depth != masterDepth && Math.abs(depth - masterDepth) % step == 0)
                .toList();
    }

    /**
     * Build a plan from a schedule pattern, where an open chamber is a moving one.
     *
     * @param pattern one entry a chamber, empty where the schedule wrote an x
     * @throws ProgressionError for a pattern that is not the length of the family
     */
    public static ProgressionPlan planFromPattern(
            DepthAndSpaceChart chart, Bitting master, List<Optional<Integer>> pattern, int step) {
        if (pattern.size() != chart.chambers()) {
            throw new ProgressionError(
                    "the pattern is not the length of the family",
                    Facts.of("chambers", chart.chambers(), "got", pattern.size()));
        }
        List<Integer> moving = IntStream.rangeClosed(1, pattern.size())
                .filter(chamber -> pattern.get(chamber - 1).isEmpty())
                .boxed()
                .toList();
        return new ProgressionPlan(chart, master, moving, step);
    }

    /** Build a plan progressed two steps at a time from a schedule pattern. */
    public static ProgressionPlan planFromPattern(
            DepthAndSpaceChart chart, Bitting master, List<Optional<Integer>> pattern) {
        return planFromPattern(chart, master, pattern, DEFAULT_STEP);
    }

    /** The chambers that stay at the master cut, bow first. */
    public List<Integer> constants() {
        return IntStream.rangeClosed(1, chart.chambers())
                .filter(chamber -> !progressed.contains(chamber))
                .boxed()
                .toList();
    }

    /** Every depth a key under the master may carry in one chamber. */
    public List<Integer> depthsIn(int chamber) {
        if (progressed.contains(chamber)) {
            return availableDepths(chart, master.at(chamber), step);
        }
        return List.of(master.at(chamber));
    }

    /** How many distinct keys the plan holds, before macs is applied. */
    public int capacity() {
        return progressed.stream().mapToInt(chamber -> depthsIn(chamber).size()).reduce(1, Math::multiplyExact);
    }

    /** Which chamber turns over fastest, first. The rightmost one does. */
    public List<Integer> order() {
        return progressed.stream().sorted(Comparator.reverseOrder()).toList();
    }

    /**
     * Every key the plan holds, in the order the array prints them.
     *
     * <p>The leftmost progressed chamber changes slowest, which is what makes a printed array
     * readable, and it means blocks of consecutive keys share their early cuts. The keys are made as
     * the stream is read, so a large plan is never held whole.
     */
    public Stream<Bitting> combinations() {
        return Bitting.everyCombination(IntStream.rangeClosed(1, chart.chambers())
                .mapToObj(this::depthsIn)
                .toList());
    }

    /** Whether a key could have come out of this plan. */
    public boolean holds(Bitting bitting) {
        if (bitting.chambers() != chart.chambers()) {
            return false;
        }
        return IntStream.rangeClosed(1, chart.chambers())
                .allMatch(chamber -> depthsIn(chamber).contains(bitting.at(chamber)));
    }

    /** One line for the cover sheet: the master, what moves, the step and the capacity. */
    public String describe() {
        String moving = progressed.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "master " + master.render() + ", chambers " + moving + " progressed " + step + " steps, holds "
                + capacity();
    }
}
