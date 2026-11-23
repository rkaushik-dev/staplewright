package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arrays that have already been built, kept because they get rebuilt a lot.
 *
 * <p>An array is a few hundred keys and every one of them goes through the whole rule set. That is
 * cheap once. It stops being cheap when the widening report builds six of them to compare, the
 * capacity page builds another, and a web request does all of that again forty seconds later
 * because somebody refreshed.
 *
 * <p>So a plan and a policy identify an array, and an array that has been built is kept. The cache
 * is bounded and dumb: oldest out first, no timing, no weighting, and a counter of hits and misses
 * so the shop can tell whether it is earning its keep. Anything cleverer would need evidence this
 * one does not have yet. One cache is shared across the process, which is what the reports use, so
 * every method is safe to call from several requests at once.
 */
public final class ArrayCache {

    /** How many arrays a cache keeps unless told otherwise. */
    public static final int DEFAULT_LIMIT = 32;

    /**
     * Everything that changes the answer, and nothing that does not.
     *
     * <p>The chart is identified by manufacturer and family rather than by every number on it,
     * because two charts with the same name are the same chart. The policy is kept whole, so a rule
     * added to it reaches the key without anybody remembering to copy it across.
     *
     * @param chart the manufacturer and family, joined by a slash
     * @param master the master bitting, as the shop writes it
     * @param progressed the moving chambers, in chamber order
     * @param step the progression step
     * @param rules the cutting policy the array was built under
     */
    public record Key(String chart, String master, List<Integer> progressed, int step, CuttingRules rules) {

        public Key {
            progressed = List.copyOf(progressed);
        }
    }

    private static final ArrayCache SHARED = new ArrayCache();

    private final int limit;
    private final Map<Key, KeyBittingArray> held = new LinkedHashMap<>();
    private int hits;
    private int misses;
    private int evictions;

    /** A cache of the usual size. */
    public ArrayCache() {
        this(DEFAULT_LIMIT);
    }

    /**
     * A cache that keeps at most {@code limit} arrays.
     *
     * @throws InputError for a limit under one
     */
    public ArrayCache(int limit) {
        if (limit < 1) {
            throw new InputError("a cache of nothing is not a cache", Facts.of("size", limit));
        }
        this.limit = limit;
    }

    /** The key a plan and a policy are kept under. */
    public static Key cacheKey(ProgressionPlan plan, CuttingRules rules) {
        return new Key(
                plan.chart().manufacturer() + "/" + plan.chart().family(),
                plan.master().render(),
                plan.progressed().stream().sorted().toList(),
                plan.step(),
                rules);
    }

    /** One cache for the process, which is what the reports use. */
    public static ArrayCache sharedCache() {
        return SHARED;
    }

    /** The array for this plan under the standing rules, through the shared cache. */
    public static KeyBittingArray arrayFor(ProgressionPlan plan) {
        return SHARED.get(plan);
    }

    /** The array for this plan and policy, through the shared cache. */
    public static KeyBittingArray arrayFor(ProgressionPlan plan, CuttingRules rules) {
        return SHARED.get(plan, rules);
    }

    /** How many arrays this cache keeps before the oldest goes out. */
    public int limit() {
        return limit;
    }

    /** The counters as they stand. They survive {@link #clear()}. */
    public synchronized CacheStats stats() {
        return new CacheStats(hits, misses, evictions);
    }

    /** The array for this plan under the standing rules, built if nobody has built it yet. */
    public KeyBittingArray get(ProgressionPlan plan) {
        return get(plan, CuttingRules.DEFAULT);
    }

    /** The array for this plan and policy, built if nobody has built it yet. */
    public synchronized KeyBittingArray get(ProgressionPlan plan, CuttingRules rules) {
        Key key = cacheKey(plan, rules);
        KeyBittingArray found = held.remove(key);
        if (found != null) {
            held.put(key, found);
            hits++;
            return found;
        }
        misses++;
        KeyBittingArray built = new KeyBittingArray(plan, rules);
        held.put(key, built);
        evict();
        return built;
    }

    /** Whether this plan has been built under the standing rules. */
    public boolean holds(ProgressionPlan plan) {
        return holds(plan, CuttingRules.DEFAULT);
    }

    /** Whether this plan has been built under this policy. */
    public synchronized boolean holds(ProgressionPlan plan, CuttingRules rules) {
        return held.containsKey(cacheKey(plan, rules));
    }

    private void evict() {
        Iterator<Key> oldest = held.keySet().iterator();
        while (held.size() > limit) {
            oldest.next();
            oldest.remove();
            evictions++;
        }
    }

    /** Empty it. A chart changing is the only reason to, and it is rare. The counters stay. */
    public synchronized void clear() {
        held.clear();
    }

    /** The keys held, oldest first. */
    public synchronized List<Key> keys() {
        return List.copyOf(held.keySet());
    }

    /** How many arrays are held. */
    public synchronized int size() {
        return held.size();
    }
}
