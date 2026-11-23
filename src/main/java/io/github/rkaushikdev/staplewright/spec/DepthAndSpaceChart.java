package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.SpecificationError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * One manufacturer's cutting chart for one family of cylinders, the only thing that makes a cut
 * real.
 *
 * <p>A chart says three things: which depth numbers exist at all, how far down the blade each one
 * cuts, and how far along the blade each chamber sits. Everything downstream is arithmetic on those
 * three. Dimensions are held in mils, thousandths of an inch, as whole numbers, so a depth never
 * prints with a rounding tail on a chart somebody has to read at a bench.
 *
 * <p>{@code depths} is the set of depth numbers the chart publishes, in order. It is not always a
 * plain range: two of the charts the shop uses skip a depth the factory withdrew after a run of
 * broken keys. {@code rootMils} is the blade dimension at the shallowest published depth, and each
 * further depth step removes {@code incrementMils} more metal. A key is measured from the shoulder
 * unless {@code gaugedFromTip} says otherwise. Two charts are equal whatever their notes say.
 */
public record DepthAndSpaceChart(
        String manufacturer,
        String family,
        int chambers,
        List<Integer> depths,
        int rootMils,
        int incrementMils,
        int firstSpaceMils,
        int spaceIncrementMils,
        int macs,
        boolean gaugedFromTip,
        int depthToleranceMils,
        String notes) {

    public DepthAndSpaceChart {
        depths = List.copyOf(depths);
        if (chambers < 1) {
            throw new SpecificationError("a chart needs at least one chamber", Facts.of("family", family));
        }
        if (depths.isEmpty()) {
            throw new SpecificationError("a chart with no depths cuts nothing", Facts.of("family", family));
        }
        if (!depths.equals(List.copyOf(new TreeSet<>(depths)))) {
            throw new SpecificationError(
                    "chart depths are listed once each and in order", Facts.of("family", family, "depths", depths));
        }
        if (incrementMils <= 0) {
            throw new SpecificationError("depths get deeper, not shallower", Facts.of("family", family));
        }
        if (spaceIncrementMils <= 0) {
            throw new SpecificationError("chambers are spaced apart", Facts.of("family", family));
        }
        if (macs < 1) {
            throw new SpecificationError("a macs of zero forbids every key", Facts.of("family", family));
        }
        int deepest = depths.get(depths.size() - 1);
        if (rootMils - incrementMils * deepest <= 0) {
            throw new SpecificationError(
                    "the deepest depth on this chart cuts through the blade",
                    Facts.of("family", family, "root_mils", rootMils, "deepest", deepest));
        }
    }

    /** A chart gauged from the shoulder with the usual two mil tolerance and no notes. */
    public DepthAndSpaceChart(
            String manufacturer,
            String family,
            int chambers,
            List<Integer> depths,
            int rootMils,
            int incrementMils,
            int firstSpaceMils,
            int spaceIncrementMils,
            int macs) {
        this(
                manufacturer,
                family,
                chambers,
                depths,
                rootMils,
                incrementMils,
                firstSpaceMils,
                spaceIncrementMils,
                macs,
                false,
                2,
                "");
    }

    /** A builder starting from this chart, for a copy with some numbers changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public int shallowest() {
        return depths.get(0);
    }

    public int deepest() {
        return depths.get(depths.size() - 1);
    }

    public int depthCount() {
        return depths.size();
    }

    public boolean allowsDepth(int depth) {
        return depths.contains(depth);
    }

    /** How far down the blade a cut of this depth reaches, in mils. */
    public int dimensionFor(int depth) {
        if (!depths.contains(depth)) {
            throw new BittingError(
                    "the chart does not publish that depth",
                    Facts.of("depth", depth, "family", family, "depths", depths));
        }
        return rootMils - incrementMils * (depth - shallowest());
    }

    /**
     * Read a measured key back into a depth number.
     *
     * <p>Anything further than the chart tolerance from a published depth is a worn key or a bad
     * cut, and the shop wants to hear about it rather than get a plausible answer.
     */
    public int depthForDimension(int mils) {
        return nearestDepth(mils)
                .orElseThrow(() -> new BittingError(
                        "that measurement is not near any depth on the chart",
                        Facts.of("mils", mils, "family", family, "tolerance", depthToleranceMils)));
    }

    /**
     * The one published depth a measurement reads as, or nothing.
     *
     * <p>Nothing for a measurement further than the tolerance from every depth, and nothing for one
     * exactly as near two depths as each other. A wide tolerance on a fine increment makes that
     * possible, and a reading that could be either cut is not a reading.
     */
    public Optional<Integer> nearestDepth(int mils) {
        List<Integer> nearest = List.of();
        int bestGap = depthToleranceMils + 1;
        for (int depth : depths) {
            int gap = Math.abs(dimensionFor(depth) - mils);
            if (gap < bestGap) {
                nearest = List.of(depth);
                bestGap = gap;
            } else if (gap == bestGap) {
                nearest = List.of(nearest.get(0), depth);
            }
        }
        return nearest.size() == 1 ? Optional.of(nearest.get(0)) : Optional.empty();
    }

    /** How far along the blade a chamber sits, in mils from the gauge. */
    public int spaceFor(int chamber) {
        if (chamber < 1 || chamber > chambers) {
            throw new BittingError(
                    "no such chamber in this family",
                    Facts.of("chamber", chamber, "chambers", chambers, "family", family));
        }
        return firstSpaceMils + spaceIncrementMils * (chamber - 1);
    }

    public List<Integer> spaces() {
        return IntStream.rangeClosed(1, chambers).map(this::spaceFor).boxed().toList();
    }

    /** Refuse a key the chart cannot cut. Says nothing about masters. */
    public void validate(Bitting bitting) {
        if (bitting.chambers() != chambers) {
            throw new BittingError(
                    "the key is not as long as the family is",
                    Facts.of("family", family, "chambers", chambers, "got", bitting.chambers()));
        }
        int chamber = 1;
        for (int depth : bitting.depths()) {
            if (!depths.contains(depth)) {
                throw new BittingError(
                        "the chart does not publish that depth",
                        Facts.of("chamber", chamber, "depth", depth, "family", family));
            }
            chamber++;
        }
    }

    public boolean isCuttable(Bitting bitting) {
        try {
            validate(bitting);
            return true;
        } catch (BittingError refused) {
            return false;
        }
    }

    /** One line naming the chart, for the top of a page. */
    public String describe() {
        return manufacturer + " " + family + ", " + chambers + " chambers, depths " + shallowest() + " to " + deepest()
                + ", macs " + macs;
    }

    /** Equal when every number is, whatever the notes say. */
    @Override
    public boolean equals(Object other) {
        return other instanceof DepthAndSpaceChart that
                && manufacturer.equals(that.manufacturer)
                && family.equals(that.family)
                && chambers == that.chambers
                && depths.equals(that.depths)
                && rootMils == that.rootMils
                && incrementMils == that.incrementMils
                && firstSpaceMils == that.firstSpaceMils
                && spaceIncrementMils == that.spaceIncrementMils
                && macs == that.macs
                && gaugedFromTip == that.gaugedFromTip
                && depthToleranceMils == that.depthToleranceMils;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                manufacturer,
                family,
                chambers,
                depths,
                rootMils,
                incrementMils,
                firstSpaceMils,
                spaceIncrementMils,
                macs,
                gaugedFromTip,
                depthToleranceMils);
    }

    /** A chart put together a number at a time, checked once when it is built. */
    public static final class Builder {
        private String manufacturer;
        private String family;
        private int chambers;
        private List<Integer> depths;
        private int rootMils;
        private int incrementMils;
        private int firstSpaceMils;
        private int spaceIncrementMils;
        private int macs;
        private boolean gaugedFromTip;
        private int depthToleranceMils;
        private String notes;

        private Builder(DepthAndSpaceChart from) {
            manufacturer = from.manufacturer;
            family = from.family;
            chambers = from.chambers;
            depths = from.depths;
            rootMils = from.rootMils;
            incrementMils = from.incrementMils;
            firstSpaceMils = from.firstSpaceMils;
            spaceIncrementMils = from.spaceIncrementMils;
            macs = from.macs;
            gaugedFromTip = from.gaugedFromTip;
            depthToleranceMils = from.depthToleranceMils;
            notes = from.notes;
        }

        public Builder manufacturer(String value) {
            manufacturer = value;
            return this;
        }

        public Builder family(String value) {
            family = value;
            return this;
        }

        public Builder chambers(int value) {
            chambers = value;
            return this;
        }

        public Builder depths(List<Integer> value) {
            depths = value;
            return this;
        }

        public Builder rootMils(int value) {
            rootMils = value;
            return this;
        }

        public Builder incrementMils(int value) {
            incrementMils = value;
            return this;
        }

        public Builder firstSpaceMils(int value) {
            firstSpaceMils = value;
            return this;
        }

        public Builder spaceIncrementMils(int value) {
            spaceIncrementMils = value;
            return this;
        }

        public Builder macs(int value) {
            macs = value;
            return this;
        }

        public Builder gaugedFromTip(boolean value) {
            gaugedFromTip = value;
            return this;
        }

        public Builder depthToleranceMils(int value) {
            depthToleranceMils = value;
            return this;
        }

        public Builder notes(String value) {
            notes = value;
            return this;
        }

        public DepthAndSpaceChart build() {
            return new DepthAndSpaceChart(
                    manufacturer,
                    family,
                    chambers,
                    depths,
                    rootMils,
                    incrementMils,
                    firstSpaceMils,
                    spaceIncrementMils,
                    macs,
                    gaugedFromTip,
                    depthToleranceMils,
                    notes);
        }
    }
}
