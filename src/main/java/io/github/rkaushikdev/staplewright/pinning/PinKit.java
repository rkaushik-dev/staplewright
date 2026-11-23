package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * The cabinet at the bench, counted.
 *
 * <p>Nothing here is clever. It is a count per bin, and the only interesting thing it does is
 * refuse a draw it cannot fill in full, because a half pinned cylinder on the bench is worse than
 * one that was never started. A kit is finite, and the shop runs out of the useful middle lengths
 * long before it runs out of the extremes, which is why drawing from a kit is a transaction that
 * can fail rather than a lookup.
 *
 * <p>A bin drawn down to nothing is still a bin. It keeps its place in the cabinet, holding
 * nought, because the empty bin is the first line on the reorder and a cabinet that forgot it ever
 * stocked a part would never order it again.
 */
public final class PinKit {

    /** How many of each part a new cabinet arrives with. */
    public static final int STANDARD_COUNT = 100;

    private final String name;
    private final Map<PinSegment, Integer> stock = new LinkedHashMap<>();

    /** An empty cabinet. */
    public PinKit(String name) {
        this(name, Map.of());
    }

    /**
     * A cabinet holding this stock. A bin stocked at nought is left out.
     *
     * @throws PinningError for a bin holding less than nothing
     */
    public PinKit(String name, Map<PinSegment, Integer> stock) {
        this.name = name;
        stock.forEach((segment, count) -> {
            if (count < 0) {
                throw new PinningError("a bin cannot hold less than nothing", Facts.of("segment", segment.toString()));
            }
            if (count > 0) {
                this.stock.put(segment, count);
            }
        });
    }

    /** A kit stocked the way a new cabinet arrives, a hundred in every bin. */
    public static PinKit standard(
            String name,
            Iterable<Integer> bottomLengths,
            Iterable<Integer> waferLengths,
            Iterable<Integer> driverLengths) {
        return standard(name, bottomLengths, waferLengths, driverLengths, STANDARD_COUNT);
    }

    /** A kit stocked the way a new cabinet arrives, even across every bin. */
    public static PinKit standard(
            String name,
            Iterable<Integer> bottomLengths,
            Iterable<Integer> waferLengths,
            Iterable<Integer> driverLengths,
            int count) {
        PinKit kit = new PinKit(name);
        List<Map.Entry<SegmentKind, Iterable<Integer>>> runs = List.of(
                Map.entry(SegmentKind.BOTTOM, bottomLengths),
                Map.entry(SegmentKind.WAFER, waferLengths),
                Map.entry(SegmentKind.DRIVER, driverLengths));
        runs.forEach(run -> run.getValue().forEach(length -> kit.add(new PinSegment(run.getKey(), length), count)));
        return kit;
    }

    public String name() {
        return name;
    }

    /** How many of one part the cabinet holds, nought for a bin nobody stocked. */
    public int stockOf(PinSegment segment) {
        return stock.getOrDefault(segment, 0);
    }

    /**
     * Put parts in. Restocking is the only way a kit grows.
     *
     * @throws PinningError for a count below one
     */
    public void add(PinSegment segment, int count) {
        if (count < 1) {
            throw new PinningError("restocking adds parts", Facts.of("count", count));
        }
        stock.merge(segment, count, Integer::sum);
    }

    /** Lengths of one kind the cabinet has any of, shallow to deep, whatever their profile. */
    public List<Integer> lengths(SegmentKind kind) {
        return lengthsOf(segment -> segment.kind() == kind);
    }

    /** Lengths of one kind and one profile the cabinet has any of, shallow to deep. */
    public List<Integer> lengths(SegmentKind kind, DriverProfile profile) {
        return lengthsOf(segment -> segment.kind() == kind && segment.profile() == profile);
    }

    private List<Integer> lengthsOf(Predicate<PinSegment> wanted) {
        return stock.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && wanted.test(entry.getKey()))
                .map(entry -> entry.getKey().lengthMils())
                .distinct()
                .sorted()
                .toList();
    }

    /** Whether there is at least one of this part. */
    public boolean holds(PinSegment segment) {
        return holds(segment, 1);
    }

    public boolean holds(PinSegment segment, int count) {
        return stockOf(segment) >= count;
    }

    /**
     * What a draw would be short by, without taking anything: each part it could not fill and how
     * many it is missing, in the order the parts were first asked for. Empty when the draw fits.
     */
    public Map<PinSegment, Integer> shortfall(Collection<PinSegment> wanted) {
        Map<PinSegment, Integer> needed = new LinkedHashMap<>();
        for (PinSegment segment : wanted) {
            needed.merge(segment, 1, Integer::sum);
        }
        Map<PinSegment, Integer> missing = new LinkedHashMap<>();
        needed.forEach((segment, count) -> {
            int gap = count - stockOf(segment);
            if (gap > 0) {
                missing.put(segment, gap);
            }
        });
        return Collections.unmodifiableMap(missing);
    }

    /**
     * Take a whole list of parts, or take nothing and say why.
     *
     * @throws CapacityError naming the kit and, by bin code, every part it is short of
     */
    public void draw(Collection<PinSegment> wanted) {
        List<PinSegment> parts = List.copyOf(wanted);
        Map<PinSegment, Integer> missing = shortfall(parts);
        if (!missing.isEmpty()) {
            Map<String, Integer> byBin = new LinkedHashMap<>();
            missing.forEach((segment, count) -> byBin.put(segment.toString(), count));
            throw new CapacityError("the kit cannot fill that", Facts.of("kit", name, "short", byBin));
        }
        parts.forEach(segment -> stock.merge(segment, -1, Integer::sum));
    }

    /** Parts off a cylinder that came apart go back in the drawer. */
    public void putBack(Collection<PinSegment> segments) {
        for (PinSegment segment : segments) {
            stock.merge(segment, 1, Integer::sum);
        }
    }

    /** Every part in the cabinet. */
    public int total() {
        return stock.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Every bin with something in it, in bin code order. */
    public List<Bin> bins() {
        return binsWhere(count -> count > 0);
    }

    /** Every bin the cabinet stocks that has been drawn down to nothing, in bin code order. */
    public List<Bin> emptyBins() {
        return binsWhere(count -> count == 0);
    }

    /**
     * Every bin the cabinet stocks holding fewer than {@code par}, empty ones included, in bin code
     * order.
     *
     * @throws PinningError for a par under one
     */
    public List<Bin> belowPar(int par) {
        if (par < 1) {
            throw new PinningError("a par level is at least one", Facts.of("par", par));
        }
        return binsWhere(count -> count < par);
    }

    /**
     * What to order to bring every bin under par back up to {@code fillTo}, by bin, in bin code
     * order. A bin at or over par is left alone however far it is under the fill level, because an
     * order for three wafers costs more in postage than the wafers.
     *
     * @throws PinningError for a par under one, or a fill level under the par
     */
    public Map<PinSegment, Integer> reorder(int par, int fillTo) {
        if (fillTo < par) {
            throw new PinningError("a bin is filled to at least its par", Facts.of("par", par, "fill_to", fillTo));
        }
        Map<PinSegment, Integer> order = new LinkedHashMap<>();
        belowPar(par).forEach(bin -> order.put(bin.segment(), fillTo - bin.count()));
        return Collections.unmodifiableMap(order);
    }

    private List<Bin> binsWhere(IntPredicate counted) {
        return stock.entrySet().stream()
                .filter(entry -> counted.test(entry.getValue()))
                .map(entry -> new Bin(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(bin -> bin.segment().binCode()))
                .toList();
    }

    /** A second cabinet holding the same stock, which can be drawn from without touching this one. */
    public PinKit copy() {
        PinKit copy = new PinKit(name);
        copy.stock.putAll(stock);
        return copy;
    }

    /**
     * One bin and how many parts are in it.
     *
     * @param segment the part the bin holds
     * @param count how many of it are there
     */
    public record Bin(PinSegment segment, int count) {}
}
