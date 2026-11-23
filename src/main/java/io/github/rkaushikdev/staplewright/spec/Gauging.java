package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.ArrayList;
import java.util.List;

/**
 * Where along the blade a cut sits, and which end it was measured from.
 *
 * <p>Most charts space the cuts from the shoulder: the flat step where the blade meets the bow,
 * which the machine jaw sits against. Some space them from the tip instead, and a handful of
 * families changed from one to the other between catalogue editions.
 *
 * <p>That matters because the two only agree when the blank is exactly the length the chart
 * assumes. A blank a hundredth long, which happens, moves every tip gauged cut a hundredth along the
 * blade and leaves every shoulder gauged cut where it was. So a shop working from the wrong end on a
 * long blank cuts a key that enters, turns nothing, and looks perfect against the chart.
 *
 * <p>The conversion is one subtraction. Getting the subtraction the wrong way round is the mistake,
 * and it is invisible until somebody stands at a door.
 */
public final class Gauging {

    /** How far a tip gauged cut may drift before the blank is refused, in mils. */
    public static final int ALLOWANCE_MILS = 4;

    /** How much metal the last chamber has to leave before the tip, in mils. */
    public static final int CLEARANCE_MILS = 60;

    private Gauging() {}

    /** Where the cuts sit measured from the shoulder, which is the chart. */
    public static List<Integer> shoulderPositions(DepthAndSpaceChart chart) {
        return chart.spaces();
    }

    /**
     * The same cuts measured back from the tip of this blank.
     *
     * <p>Chamber one is nearest the shoulder, so from the tip it is the furthest away, and the list
     * comes back in chamber order rather than reversed.
     */
    public static List<Integer> tipPositions(DepthAndSpaceChart chart, Blade blade) {
        return shoulderPositions(chart).stream()
                .map(space -> blade.lengthMils() - space)
                .toList();
    }

    /** Turn tip gauged positions into shoulder gauged ones for this blank. */
    public static List<Integer> fromTipToShoulder(Blade blade, List<Integer> positions) {
        return positions.stream().map(position -> blade.lengthMils() - position).toList();
    }

    /**
     * Where the machine goes on this blank, measured from the shoulder, given which end this family
     * gauges from.
     *
     * <p>A tip gauged chart prints its spaces for a blank of nominal length. Gauged off the tip of a
     * blank that is long, every cut sits that much further from the shoulder, so the positions move
     * by the blank's error. A shoulder gauged chart is where it says whatever the blank does.
     */
    public static List<Integer> positionsFor(DepthAndSpaceChart chart, Blade blade) {
        int drift = driftOn(chart, blade);
        return shoulderPositions(chart).stream().map(space -> space + drift).toList();
    }

    /**
     * How far a tip gauged cut moves on a blank that is off nominal.
     *
     * <p>Nought for a shoulder gauged family however wrong the blank is, which is the whole reason
     * shops prefer shoulder gauging.
     */
    public static int driftOn(DepthAndSpaceChart chart, Blade blade) {
        return chart.gaugedFromTip() ? blade.errorMils() : 0;
    }

    /** Whether this blank can be cut on this chart without moving the cuts, at the usual allowance. */
    public static boolean withinTolerance(DepthAndSpaceChart chart, Blade blade) {
        return withinTolerance(chart, blade, ALLOWANCE_MILS);
    }

    /**
     * Whether this blank can be cut on this chart without moving the cuts.
     *
     * @throws InputError for a negative allowance
     */
    public static boolean withinTolerance(DepthAndSpaceChart chart, Blade blade, int allowanceMils) {
        if (allowanceMils < 0) {
            throw new InputError("an allowance is not negative", Facts.of("allowance", allowanceMils));
        }
        return Math.abs(driftOn(chart, blade)) <= allowanceMils;
    }

    /** Whether the last chamber leaves the usual clearance of metal between it and the tip. */
    public static boolean bladeLongEnough(DepthAndSpaceChart chart, Blade blade) {
        return bladeLongEnough(chart, blade, CLEARANCE_MILS);
    }

    /**
     * Whether the last chamber leaves this much metal between it and the tip, and the first cut
     * still lands on the blade. A tip gauged family on a short blank keeps its clearance at the tip
     * and runs its first cut into the shoulder instead.
     */
    public static boolean bladeLongEnough(DepthAndSpaceChart chart, Blade blade, int clearanceMils) {
        return positionsFor(chart, blade).get(0) > 0
                && blade.lengthMils() - lastPosition(chart, blade) >= clearanceMils;
    }

    /** Every reason this blank is wrong for this chart, at the usual allowance, in reading order. */
    public static List<String> checkBlade(DepthAndSpaceChart chart, Blade blade) {
        return checkBlade(chart, blade, ALLOWANCE_MILS);
    }

    /** Every reason this blank is wrong for this chart, in reading order. Empty for a good blank. */
    public static List<String> checkBlade(DepthAndSpaceChart chart, Blade blade, int allowanceMils) {
        List<String> found = new ArrayList<>();
        int first = positionsFor(chart, blade).get(0);
        if (first <= 0) {
            found.add(blade.partNumber() + " is too short for " + chart.family()
                    + ", the cut against the shoulder would sit at " + first);
        } else if (!bladeLongEnough(chart, blade)) {
            found.add(blade.partNumber() + " is too short for " + chart.family() + ", the tip cut sits at "
                    + lastPosition(chart, blade));
        }
        if (!withinTolerance(chart, blade, allowanceMils)) {
            found.add(blade.partNumber() + " is " + blade.errorMils() + " off nominal and " + chart.family()
                    + " gauges from the tip");
        }
        return List.copyOf(found);
    }

    /**
     * Where one chamber sits on this blank, from whichever end applies.
     *
     * @throws BittingError for a chamber the family does not have
     */
    public static int positionOf(DepthAndSpaceChart chart, int chamber, Blade blade) {
        if (chamber < 1 || chamber > chart.chambers()) {
            throw new BittingError(
                    "no such chamber in this family", Facts.of("chamber", chamber, "chambers", chart.chambers()));
        }
        return positionsFor(chart, blade).get(chamber - 1);
    }

    private static int lastPosition(DepthAndSpaceChart chart, Blade blade) {
        return positionsFor(chart, blade).get(chart.chambers() - 1);
    }
}
