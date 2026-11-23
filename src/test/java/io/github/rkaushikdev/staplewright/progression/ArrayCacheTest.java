package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayCacheTest {

    private static final List<Integer> PROGRESSED = List.of(2, 4);

    private static ProgressionPlan aPlan() {
        return aPlan(Bitting.parse("342156"), PROGRESSED);
    }

    private static ProgressionPlan aPlan(Bitting master) {
        return aPlan(master, PROGRESSED);
    }

    private static ProgressionPlan aPlan(List<Integer> progressed) {
        return aPlan(Bitting.parse("342156"), progressed);
    }

    private static ProgressionPlan aPlan(Bitting master, List<Integer> progressed) {
        return new ProgressionPlan(Charts.sixPinChart(chart -> chart.macs(5)), master, progressed, 2);
    }

    @Test
    void theKeyCoversThePlanAndThePolicy() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        ArrayCache.Key second = ArrayCache.cacheKey(aPlan(), new CuttingRules().with(policy -> policy.maxRun(1)));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void twoEqualPlansShareAKey() {
        assertThat(ArrayCache.cacheKey(aPlan(), new CuttingRules()))
                .isEqualTo(ArrayCache.cacheKey(aPlan(), new CuttingRules()));
    }

    @Test
    void theChamberOrderDoesNotChangeTheKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(List.of(2, 4)), new CuttingRules());
        ArrayCache.Key second = ArrayCache.cacheKey(aPlan(List.of(4, 2)), new CuttingRules());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void aDifferentMasterIsADifferentKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        ArrayCache.Key second = ArrayCache.cacheKey(aPlan(Bitting.parse("542156")), new CuttingRules());
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aDifferentChartIsADifferentKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        ArrayCache.Key second = ArrayCache.cacheKey(
                new ProgressionPlan(Charts.fivePinChart(), Bitting.parse("13024"), List.of(2), 2), new CuttingRules());
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theTipRuleReachesTheKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        ArrayCache.Key second =
                ArrayCache.cacheKey(aPlan(), new CuttingRules().with(policy -> policy.shallowTipLimit(4)));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theBowRuleReachesTheKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        ArrayCache.Key second = ArrayCache.cacheKey(aPlan(), new CuttingRules().with(policy -> policy.deepBowLimit(4)));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theEndOfChartRulesReachTheKey() {
        ArrayCache.Key first = ArrayCache.cacheKey(aPlan(), new CuttingRules());
        assertThat(first)
                .isNotEqualTo(ArrayCache.cacheKey(
                        aPlan(), new CuttingRules().with(policy -> policy.forbidAllShallowest(false))));
        assertThat(first)
                .isNotEqualTo(ArrayCache.cacheKey(
                        aPlan(), new CuttingRules().with(policy -> policy.forbidAllDeepest(false))));
    }

    @Test
    void aBowLimitBuildsItsOwnArray() {
        ArrayCache cache = new ArrayCache();
        ProgressionPlan plan = aPlan(List.of(1, 4));
        KeyBittingArray open = cache.get(plan, new CuttingRules());
        KeyBittingArray limited = cache.get(plan, new CuttingRules().with(policy -> policy.deepBowLimit(2)));
        assertThat(limited).isNotSameAs(open);
        assertThat(limited.rejectionReasons()).containsKey("bow");
        assertThat(limited.usable()).isNotEmpty().allSatisfy(key -> assertThat(key.at(1))
                .isLessThanOrEqualTo(2));
        assertThat(limited.usableCount()).isLessThan(open.usableCount());
    }

    @Test
    void theFirstLookBuildsAndTheSecondDoesNot() {
        ArrayCache cache = new ArrayCache();
        KeyBittingArray first = cache.get(aPlan());
        KeyBittingArray second = cache.get(aPlan());
        assertThat(first).isSameAs(second);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void aCacheReportsWhetherItHoldsSomething() {
        ArrayCache cache = new ArrayCache();
        assertThat(cache.holds(aPlan())).isFalse();
        cache.get(aPlan());
        assertThat(cache.holds(aPlan())).isTrue();
    }

    @Test
    void aDifferentPolicyBuildsAgain() {
        ArrayCache cache = new ArrayCache();
        KeyBittingArray first = cache.get(aPlan(), new CuttingRules());
        KeyBittingArray second = cache.get(aPlan(), new CuttingRules().with(policy -> policy.maxRun(1)));
        assertThat(first).isNotSameAs(second);
        assertThat(cache.stats().misses()).isEqualTo(2);
    }

    @Test
    void theOldestGoesOutWhenItIsFull() {
        ArrayCache cache = new ArrayCache(2);
        cache.get(aPlan());
        cache.get(aPlan(Bitting.parse("542156")));
        cache.get(aPlan(Bitting.parse("742156")));
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.stats().evictions()).isEqualTo(1);
        assertThat(cache.holds(aPlan())).isFalse();
    }

    @Test
    void aHitMovesSomethingBackToTheFront() {
        ArrayCache cache = new ArrayCache(2);
        cache.get(aPlan());
        cache.get(aPlan(Bitting.parse("542156")));
        cache.get(aPlan());
        cache.get(aPlan(Bitting.parse("742156")));
        assertThat(cache.holds(aPlan())).isTrue();
        assertThat(cache.holds(aPlan(Bitting.parse("542156")))).isFalse();
    }

    @Test
    void aCacheOfNothingIsRefused() {
        assertThatThrownBy(() -> new ArrayCache(0)).isInstanceOf(InputError.class);
    }

    @Test
    void clearingEmptiesItAndKeepsTheCounters() {
        ArrayCache cache = new ArrayCache();
        cache.get(aPlan());
        cache.clear();
        assertThat(cache.size()).isZero();
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void theKeysCanBeListed() {
        ArrayCache cache = new ArrayCache();
        cache.get(aPlan());
        assertThat(cache.keys()).hasSize(1);
        assertThat(cache.keys().get(0).master()).isEqualTo("342156");
    }

    @Test
    void theStatsReadAsARate() {
        CacheStats stats = new CacheStats(3, 1, 0);
        assertThat(stats.lookups()).isEqualTo(4);
        assertThat(stats.hitRate()).isEqualTo(0.75);
        assertThat(stats.describe()).contains("rate 0.75");
    }

    @Test
    void aCacheNobodyHasUsedHasNoRate() {
        assertThat(new CacheStats().hitRate()).isZero();
    }

    @Test
    void theSharedCacheIsOneObject() {
        assertThat(ArrayCache.sharedCache()).isSameAs(ArrayCache.sharedCache());
    }

    @Test
    void theHelperGoesThroughTheSharedCache() {
        ArrayCache.sharedCache().clear();
        KeyBittingArray first = ArrayCache.arrayFor(aPlan());
        KeyBittingArray second = ArrayCache.arrayFor(aPlan());
        assertThat(first).isSameAs(second);
    }
}
