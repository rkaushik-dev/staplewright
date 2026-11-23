package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading keys cut against one chart as the depths of another.
 *
 * <p>Two charts for the same cylinders turn up more often than they should. A manufacturer reissues
 * a family with a new root depth, a catalogue edition renumbers the depths after withdrawing one,
 * or a building comes over from another campus with keys cut to a chart the shop has never stocked.
 * The metal in the ground does not change, so the question is what each cut on the old chart reads
 * as on the new one, and whether the keys line up along the blade at all.
 *
 * <p>The answer is worked from dimensions, never from depth numbers, because a five on one chart
 * and a five on another can be thirty thousandths apart. A cut whose dimension lands between two
 * depths on the new chart has no reading and is refused rather than rounded, since a rounded cut is
 * a key that enters and does not turn.
 *
 * @param from the chart the keys were cut against
 * @param to the chart they are to be read on
 */
public record Translation(DepthAndSpaceChart from, DepthAndSpaceChart to) {

    /**
     * Why keys cut on the first chart do not sit under the chambers of the second, or nothing when
     * they do.
     *
     * <p>The chamber count has to agree, and every space has to agree to within the gauging
     * allowance, measured from the same end of the blade.
     */
    public Optional<String> spacingProblem() {
        if (from.chambers() != to.chambers()) {
            return Optional.of("the charts have " + from.chambers() + " and " + to.chambers() + " chambers");
        }
        if (from.gaugedFromTip() != to.gaugedFromTip()) {
            return Optional.of("one chart gauges from the tip and the other from the shoulder");
        }
        for (int chamber = 1; chamber <= from.chambers(); chamber++) {
            int drift = Math.abs(from.spaceFor(chamber) - to.spaceFor(chamber));
            if (drift > Gauging.ALLOWANCE_MILS) {
                return Optional.of("chamber " + chamber + " sits " + drift + " mils apart on the two charts");
            }
        }
        return Optional.empty();
    }

    /** Whether the cuts on the first chart land under the chambers of the second. */
    public boolean linesUp() {
        return spacingProblem().isEmpty();
    }

    /**
     * Every depth on the first chart and what it reads as on the second, in chart order.
     *
     * <p>A depth with no reading is there with nothing against it, which is the line on the
     * conversion sheet the bench needs to see most.
     */
    public Map<Integer, Optional<Integer>> depthTable() {
        Map<Integer, Optional<Integer>> table = new LinkedHashMap<>();
        for (int depth : from.depths()) {
            table.put(depth, to.nearestDepth(from.dimensionFor(depth)));
        }
        return Collections.unmodifiableMap(table);
    }

    /** The depths on the first chart that read as nothing on the second, in chart order. */
    public List<Integer> unreadableDepths() {
        return depthTable().entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The key as the second chart writes it.
     *
     * @throws BittingError for a key the first chart cannot cut, charts whose chambers do not line
     *     up, or a cut that reads as no single depth on the second chart, naming the chamber
     */
    public Bitting translate(Bitting bitting) {
        from.validate(bitting);
        Optional<String> problem = spacingProblem();
        if (problem.isPresent()) {
            throw new BittingError(
                    "keys cut on one chart do not sit under the chambers of the other",
                    Facts.of("from", from.family(), "to", to.family(), "reason", problem.get()));
        }
        List<Integer> cuts = new ArrayList<>();
        for (int chamber = 1; chamber <= bitting.chambers(); chamber++) {
            int depth = bitting.at(chamber);
            int at = chamber;
            cuts.add(to.nearestDepth(from.dimensionFor(depth))
                    .orElseThrow(() -> new BittingError(
                            "that cut reads as no single depth on the other chart",
                            Facts.of("chamber", at, "depth", depth, "to", to.family()))));
        }
        return new Bitting(cuts);
    }

    /** Whether the key reads cleanly on the second chart, chambers and every cut. */
    public boolean carriesOver(Bitting bitting) {
        return from.isCuttable(bitting)
                && linesUp()
                && bitting.depths().stream().allMatch(depth -> to.nearestDepth(from.dimensionFor(depth))
                        .isPresent());
    }
}
