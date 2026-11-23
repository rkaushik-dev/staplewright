package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Cutting a key by code, and proving afterwards that it came out right.
 *
 * <p>Two machines in the shop and they are not interchangeable. The duplicator traces a key it is
 * given, which is fast and inherits every bit of wear that key has. The code machine cuts to numbers
 * off a card, which is slower and is the only thing that produces a key identical to what the
 * schedule says.
 *
 * <p>Everything the code machine needs comes off the chart. The carriage goes to the space for a
 * chamber, the cutter comes down to the dimension for a depth, and the operator does that once per
 * cut. The two things that go wrong are a carriage out of calibration, which walks every cut along
 * the blade by the same amount, and a cutter worn small, which makes every cut shallow. Both are
 * constant offsets, which is why measuring one key catches them.
 */
public final class Machines {

    private Machines() {}

    /** Everything a code machine cutting true needs to cut one key. */
    public static CutCard cutCard(DepthAndSpaceChart chart, Bitting bitting) {
        return cutCard(chart, bitting, Calibration.TRUE);
    }

    /**
     * Everything the code machine needs to cut one key, with today's offsets folded in.
     *
     * @throws BittingError for a key the chart cannot cut
     */
    public static CutCard cutCard(DepthAndSpaceChart chart, Bitting bitting, Calibration calibration) {
        chart.validate(bitting);
        List<CutInstruction> instructions = IntStream.rangeClosed(1, chart.chambers())
                .mapToObj(chamber -> new CutInstruction(
                        chamber,
                        bitting.at(chamber),
                        chart.spaceFor(chamber) + calibration.spaceOffsetMils(),
                        chart.dimensionFor(bitting.at(chamber)) + calibration.depthOffsetMils()))
                .toList();
        return new CutCard(chart.family(), bitting, instructions, calibration);
    }

    /**
     * Read a cut key back into a bitting, refusing anything off the chart.
     *
     * @param dimensions the measured blade dimension in each chamber, bow first, in mils
     * @throws InputError for a key that is not as long as the family
     * @throws BittingError for a cut nowhere near a published depth
     */
    public static Bitting measure(DepthAndSpaceChart chart, List<Integer> dimensions) {
        requireLength(chart, dimensions);
        List<Integer> depths = new ArrayList<>();
        for (int chamber = 1; chamber <= dimensions.size(); chamber++) {
            int at = chamber;
            int mils = dimensions.get(chamber - 1);
            depths.add(chart.nearestDepth(mils)
                    .orElseThrow(() -> new BittingError(
                            "that measurement does not read as one depth on the chart",
                            Facts.of("chamber", at, "mils", mils, "family", chart.family()))));
        }
        return new Bitting(depths);
    }

    private static void requireLength(DepthAndSpaceChart chart, List<Integer> dimensions) {
        if (dimensions.size() != chart.chambers()) {
            throw new InputError(
                    "that is not a key from this family",
                    Facts.of("chambers", chart.chambers(), "got", dimensions.size()));
        }
    }

    /**
     * Which chambers came out wrong, counted from one.
     *
     * <p>Anything not near a published depth is refused, because that is a broken key rather than a
     * wrong one and the operator needs to hear it differently.
     */
    public static List<Integer> verify(DepthAndSpaceChart chart, Bitting wanted, List<Integer> dimensions) {
        Bitting cut = measure(chart, dimensions);
        return wanted.differsFrom(cut);
    }

    /**
     * The constant depth error across a key, if there is one.
     *
     * <p>A machine out of calibration is wrong by the same amount everywhere. One chamber wrong and
     * the rest right is an operator error, not a machine, and this comes back empty rather than
     * pretending otherwise. A key cut true has no drift either.
     */
    public static Optional<Integer> drift(DepthAndSpaceChart chart, Bitting wanted, List<Integer> dimensions) {
        requireLength(chart, dimensions);
        return constantOffset(IntStream.rangeClosed(1, dimensions.size())
                .mapToObj(chamber -> dimensions.get(chamber - 1) - chart.dimensionFor(wanted.at(chamber)))
                .toList());
    }

    /**
     * The constant error along the blade, if there is one: how far from its chart space every cut
     * landed, measured in mils from the gauge.
     *
     * <p>A carriage out of calibration walks every cut the same way by the same amount. Cuts that
     * wander by different amounts are a loose key in the jaw, which the operator fixes by clamping it
     * again rather than by touching the carriage, so that comes back empty.
     *
     * @throws InputError for a key that is not as long as the family
     */
    public static Optional<Integer> spacingDrift(DepthAndSpaceChart chart, List<Integer> positions) {
        requireLength(chart, positions);
        return constantOffset(IntStream.rangeClosed(1, positions.size())
                .mapToObj(chamber -> positions.get(chamber - 1) - chart.spaceFor(chamber))
                .toList());
    }

    /**
     * The offsets to dial in after measuring a test key, given the offsets it was cut with.
     *
     * <p>The test key was cut to {@code wanted} under {@code current}, so whatever constant error is
     * left on it is the part the current offsets did not take out. Only a constant error moves an
     * offset; a key that came out wrong in one place leaves the machine alone.
     *
     * @param dimensions the measured blade dimension in each chamber, bow first, in mils
     * @param positions the measured position of each cut from the gauge, bow first, in mils
     */
    public static Calibration recalibrate(
            DepthAndSpaceChart chart,
            Calibration current,
            Bitting wanted,
            List<Integer> dimensions,
            List<Integer> positions) {
        int depthError = drift(chart, wanted, dimensions).orElse(0);
        int spaceError = spacingDrift(chart, positions).orElse(0);
        return current.correctedBy(spaceError, depthError);
    }

    private static Optional<Integer> constantOffset(List<Integer> errors) {
        Set<Integer> distinct = Set.copyOf(errors);
        if (distinct.size() != 1) {
            return Optional.empty();
        }
        int only = distinct.iterator().next();
        return only == 0 ? Optional.empty() : Optional.of(only);
    }

    /**
     * How much shallower a traced copy came out than what it was traced from, at its worst chamber.
     *
     * <p>A duplicator follows the key it traces, so a worn key gives a shallow copy, and the copy of
     * that copy is shallower again. Three generations in and the key will not turn a tight cylinder.
     *
     * @throws InputError for two keys of different lengths, or nothing to compare
     */
    public static int duplicateWear(List<Integer> original, List<Integer> copy) {
        if (original.size() != copy.size()) {
            throw new InputError(
                    "those are not the same key", Facts.of("original", original.size(), "copy", copy.size()));
        }
        if (original.isEmpty()) {
            throw new InputError("nothing to compare");
        }
        return IntStream.range(0, copy.size())
                .map(index -> copy.get(index) - original.get(index))
                .max()
                .orElseThrow();
    }

    /** Whether every cut on a key is near enough to a published depth. */
    public static boolean withinTolerance(DepthAndSpaceChart chart, List<Integer> dimensions) {
        return dimensions.size() == chart.chambers()
                && dimensions.stream().allMatch(mils -> chart.nearestDepth(mils).isPresent());
    }
}
