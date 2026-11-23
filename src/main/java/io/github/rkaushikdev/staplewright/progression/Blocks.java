package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The two ways a shop hands out blocks of an array, and what each costs.
 *
 * <p>The block method is the obvious one. Cut the array into runs and give one run to each master.
 * It is easy to read, easy to extend, and it wastes whatever is left at the end of a run when a
 * department turns out to need six keys rather than ten.
 *
 * <p>The rotating constant method holds one chamber still per master instead. Every key under
 * master AA carries the same cut in the constant chamber, which means a key can be read back to its
 * master by looking at one position, and it means the masters do not compete for the same block of
 * the array. It costs a chamber: that one cannot be progressed for change keys any more, so the
 * system gets narrower as it gets easier to read.
 *
 * <p>Neither is right. The block method suits a campus that grows by buildings and the rotating
 * constant suits one that grows by departments, and the shop has used both in the same decade.
 */
public final class Blocks {

    private Blocks() {}

    /**
     * Cut the array into runs and hand one to each master, in order.
     *
     * @throws CapacityError when there are more masters than blocks
     * @throws ProgressionError when a master is listed twice, or the size is under one
     */
    public static List<Allocation> byBlocks(KeyBittingArray array, List<KeySymbol> symbols, int size) {
        List<List<Bitting>> blocks = array.blocks(size);
        if (symbols.size() > blocks.size()) {
            throw new CapacityError(
                    "not enough blocks for the masters that want one",
                    Facts.of("wanted", symbols.size(), "blocks", blocks.size()));
        }
        requireDistinct(symbols, "a block");
        return IntStream.range(0, symbols.size())
                .mapToObj(index -> new Allocation(symbols.get(index), blocks.get(index)))
                .toList();
    }

    private static void requireDistinct(List<KeySymbol> symbols, String what) {
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new ProgressionError("a symbol was handed " + what + " twice", Facts.of("symbols", symbols.size()));
        }
    }

    /**
     * Every depth the array actually uses in one chamber, shallow first.
     *
     * @throws ProgressionError for a chamber outside the family
     */
    public static List<Integer> constantDepths(KeyBittingArray array, int chamber) {
        return List.copyOf(keysByDepth(array, chamber).keySet());
    }

    /** The usable keys sorted by their cut in one chamber, shallow first, each run in array order. */
    private static Map<Integer, List<Bitting>> keysByDepth(KeyBittingArray array, int chamber) {
        if (chamber < 1 || chamber > array.plan().chart().chambers()) {
            throw new ProgressionError("no such chamber in this family", Facts.of("chamber", chamber));
        }
        return array.usable().stream()
                .collect(Collectors.groupingBy(key -> key.at(chamber), TreeMap::new, Collectors.toList()));
    }

    /**
     * Hold one chamber still per master, one depth each.
     *
     * <p>A master gets every key in the array that carries its depth in the constant chamber, so the
     * allocations partition the array exactly and a key can be read back to its master from one cut.
     * Masters take the depths shallow first, in the order they were given.
     *
     * @throws ProgressionError when a master is listed twice, or the chamber is outside the family
     * @throws CapacityError when there are more masters than depths in the chamber
     */
    public static List<Allocation> byRotatingConstant(KeyBittingArray array, List<KeySymbol> symbols, int chamber) {
        requireDistinct(symbols, "a depth");
        Map<Integer, List<Bitting>> byDepth = keysByDepth(array, chamber);
        if (symbols.size() > byDepth.size()) {
            throw new CapacityError(
                    "the constant chamber does not hold that many masters",
                    Facts.of("wanted", symbols.size(), "depths", byDepth.size(), "chamber", chamber));
        }
        List<Integer> depths = List.copyOf(byDepth.keySet());
        return IntStream.range(0, symbols.size())
                .mapToObj(index ->
                        new Allocation(symbols.get(index), byDepth.get(depths.get(index)), chamber, depths.get(index)))
                .toList();
    }

    /** Which master a key belongs to, read off the allocations; empty when nobody was given it. */
    public static Optional<KeySymbol> masterOf(List<Allocation> allocations, Bitting key) {
        return allocations.stream()
                .filter(allocation -> allocation.keys().contains(key))
                .map(Allocation::symbol)
                .findFirst();
    }

    /**
     * Change keys on the schedule cut from somewhere other than what their master was given, in
     * schedule order.
     *
     * <p>A department that ran out of keys in its own block borrows the next one along, and from then
     * on a key in its hand reads back to the wrong master. Under the rotating constant method the
     * borrowed key carries the wrong cut in the held chamber, which is the one thing the method
     * promised would never happen. A key from the array that nobody was given is a smaller worry, and
     * a change key under a master that was given nothing at all says nothing either way.
     */
    public static List<Finding> strays(KeyingSchedule schedule, List<Allocation> allocations) {
        Set<KeySymbol> allocated = allocations.stream().map(Allocation::symbol).collect(Collectors.toSet());
        List<Finding> found = new ArrayList<>();
        for (KeySymbol symbol : schedule.changeKeys()) {
            Optional<Bitting> cut = schedule.bittingOf(symbol);
            KeySymbol master = symbol.parent().orElseThrow();
            if (cut.isEmpty() || !allocated.contains(master)) {
                continue;
            }
            Optional<Allocation> owner = allocations.stream()
                    .filter(allocation -> allocation.keys().contains(cut.get()))
                    .findFirst();
            if (owner.isEmpty()) {
                found.add(new Finding(
                        "unallocated_key",
                        Severity.NOTE,
                        symbol.render() + " is cut from keys nobody was given",
                        symbol.render()));
            } else if (!owner.get().symbol().equals(master)) {
                String held = owner.get()
                        .constantChamber()
                        .map(chamber -> ", and carries the "
                                + owner.get().constantDepth().orElseThrow() + " held in chamber " + chamber + " for it")
                        .orElse("");
                found.add(new Finding(
                        "borrowed_key",
                        Severity.SERIOUS,
                        symbol.render() + " is cut from the keys "
                                + owner.get().symbol().render() + " was given" + held,
                        symbol.render()));
            }
        }
        return List.copyOf(found);
    }

    /** Keys in the array nobody was given, which is the waste, in array order. */
    public static List<Bitting> unallocated(KeyBittingArray array, List<Allocation> allocations) {
        Set<Bitting> handed = new HashSet<>();
        allocations.forEach(allocation -> handed.addAll(allocation.keys()));
        return array.usable().stream().filter(key -> !handed.contains(key)).toList();
    }

    /** How many keys in the array nobody was given. */
    public static int waste(KeyBittingArray array, List<Allocation> allocations) {
        return unallocated(array, allocations).size();
    }

    /** Whether the allocations cover the array exactly once each. */
    public static boolean isPartition(KeyBittingArray array, List<Allocation> allocations) {
        List<Bitting> handed = allocations.stream()
                .flatMap(allocation -> allocation.keys().stream())
                .toList();
        return handed.size() == new HashSet<>(handed).size() && handed.size() == array.usableCount();
    }
}
