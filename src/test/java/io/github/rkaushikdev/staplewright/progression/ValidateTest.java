package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.support.Charts;
import io.github.rkaushikdev.staplewright.support.Systems;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ValidateTest {

    private static final Map<String, String> CUTS = cuts();

    private static final List<String> WORST_FIRST = List.of("blocking", "serious", "note");

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("B", "242446");
        cuts.put("AA", "622446");
        cuts.put("AB", "662446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        cuts.put("AB1", "662046");
        cuts.put("AB2", "662646");
        return cuts;
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static KeyingSchedule aGoodSchedule() {
        KeyingSchedule schedule = Systems.smallSchedule();
        CUTS.forEach((symbol, bitting) -> schedule.assign(sym(symbol), b(bitting)));
        return schedule;
    }

    /** Write a line the way the database would, checks and all skipped. */
    private static KeyingSchedule force(KeyingSchedule schedule, String symbol, String bitting) {
        ScheduleEntry entry = schedule.entry(sym(symbol));
        schedule.load(new ScheduleEntry(entry.symbol(), entry.section(), Optional.of(b(bitting)), entry.note()));
        return schedule;
    }

    /** A cylinder taking the top key, both grand masters and one master: three cuts in chamber one. */
    private static KeyingGroup lobby() {
        return new KeyingGroup("lobby", List.of(sym("GGM"), sym("A"), sym("B"), sym("AA")));
    }

    @Test
    void aCleanScheduleHasNothingToSay() {
        KeyingSchedule schedule = aGoodSchedule();
        assertThat(Validation.duplicateCuts(schedule)).isEmpty();
        assertThat(Validation.keysMatchingTheirMaster(schedule)).isEmpty();
        assertThat(Validation.thinSplits(schedule)).isEmpty();
        assertThat(Validation.review(schedule)).isEmpty();
        assertThat(Validation.isBuildable(Validation.review(schedule))).isTrue();
    }

    @Test
    void twoKeysCutTheSameIsBlocking() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AB2", "620446");
        List<Finding> found = Validation.duplicateCuts(schedule);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).severity()).isSameAs(Severity.BLOCKING);
        assertThat(found.get(0).message()).contains("AA1");
        assertThat(found.get(0).symbol()).contains("AB2");
    }

    @Test
    void aChangeKeyCutLikeItsMasterIsBlocking() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "622446");
        List<Finding> found = Validation.keysMatchingTheirMaster(schedule);
        assertThat(found.stream().map(Finding::symbol).toList()).containsExactly(Optional.of("AA1"));
        assertThat(found.get(0).code()).isEqualTo("same_as_master");
    }

    @Test
    void aOneStepSplitIsSeriousNotBlocking() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "621446");
        List<Finding> found = Validation.thinSplits(schedule);
        assertThat(found.stream().map(Finding::code).toList()).containsExactly("off_step");
        assertThat(found.get(0).severity()).isSameAs(Severity.SERIOUS);
        assertThat(found.get(0).message()).contains("chamber 3");
    }

    @Test
    void aSystemThatMovesOneStepAtATimeSeesNothing() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "621446");
        assertThat(Validation.thinSplits(schedule, 1)).isEmpty();
    }

    @Test
    void twoSiblingsOneStepApartInOneChamberAreNearNeighbours() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA2", "621446");
        List<Finding> found = Validation.nearNeighbours(schedule, 2);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).code()).isEqualTo("near_neighbour");
        assertThat(found.get(0).severity()).isSameAs(Severity.SERIOUS);
        assertThat(found.get(0).symbol()).contains("AA2");
        assertThat(found.get(0).message()).isEqualTo("AA2 is 1 from AA1 in chamber 3 and the same everywhere else");
    }

    @Test
    void aNearKeyIsReportedOnceAgainstTheFirstItSitsNear() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AB1", "621446");
        List<Finding> found = Validation.nearNeighbours(schedule, 2);
        assertThat(found)
                .extracting(Finding::message)
                .containsExactly("AB1 is 1 from AA in chamber 3 and the same everywhere else");
    }

    @Test
    void aKeyOneStepFromItsOwnMasterIsLeftToTheSplitCheck() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "621446");
        assertThat(Validation.nearNeighbours(schedule, 2)).isEmpty();
        assertThat(Validation.thinSplits(schedule)).extracting(Finding::code).containsExactly("off_step");
    }

    @Test
    void twoChambersApartIsNotANearNeighbour() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA2", "621456");
        assertThat(Validation.nearNeighbours(schedule, 2)).isEmpty();
    }

    @Test
    void aOneStepSystemHasNoNearNeighboursAndAWholeStepIsNotOne() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA2", "621446");
        assertThat(Validation.nearNeighbours(schedule, 1)).isEmpty();
        assertThat(Validation.nearNeighbours(aGoodSchedule(), 2)).isEmpty();
        assertThat(Validation.nearNeighbours(aGoodSchedule(), 5))
                .extracting(Finding::symbol)
                .contains(Optional.of("AA2"));
    }

    @Test
    void theReviewCarriesTheNearNeighbours() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA2", "621446");
        assertThat(Validation.review(schedule)).extracting(Finding::code).contains("near_neighbour", "off_step");
        assertThat(Validation.review(schedule, 1)).extracting(Finding::code).doesNotContain("near_neighbour");
    }

    @Test
    void aKeyThatMovedInAConstantChamberIsOffPlan() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "621456");
        ProgressionPlan plan = new ProgressionPlan(Charts.sixPinChart(), b("622446"), List.of(3), 2);
        List<Finding> found = Validation.keysOffPlan(schedule, plan);
        Set<Optional<String>> codes = found.stream().map(Finding::symbol).collect(Collectors.toSet());
        assertThat(codes).contains(Optional.of("AA1"));
        assertThat(found).anyMatch(finding -> finding.message().contains("constant chamber"));
    }

    @Test
    void aKeyThePlanHoldsIsNotReported() {
        KeyingSchedule schedule = aGoodSchedule();
        ProgressionPlan plan = new ProgressionPlan(Charts.sixPinChart(), b("622446"), List.of(3), 2);
        Set<Optional<String>> off = Validation.keysOffPlan(schedule, plan).stream()
                .map(Finding::symbol)
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain(Optional.of("AA1"));
    }

    @Test
    void anUncutSymbolIsOnlyANote() {
        KeyingSchedule schedule = Systems.smallSchedule();
        List<Finding> found = Validation.uncutSymbols(schedule);
        assertThat(found).hasSize(schedule.size());
        assertThat(found).allSatisfy(finding -> assertThat(finding.severity()).isSameAs(Severity.NOTE));
    }

    @Test
    void aCrowdedChamberIsReportedAgainstTheGroupByName() {
        List<Finding> found = Validation.crowdedChambers(aGoodSchedule(), List.of(lobby()), 1);
        assertThat(found.stream().map(Finding::code).toList()).containsExactly("crowded_chamber");
        assertThat(found.get(0).message()).isEqualTo("group lobby wants 2 wafers in chamber 1 and the shop builds 1");
        assertThat(found.get(0).severity()).isSameAs(Severity.SERIOUS);
    }

    @Test
    void aGroupInsideTheLimitIsQuiet() {
        KeyingGroup pair = new KeyingGroup("pair", List.of(sym("GGM"), sym("A")));
        assertThat(Validation.crowdedChambers(aGoodSchedule(), List.of(pair), 1))
                .isEmpty();
        assertThat(Validation.crowdedChambers(aGoodSchedule(), List.of(lobby())))
                .isEmpty();
    }

    @Test
    void aGroupIsJudgedOnTheKeysCutSoFar() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("A"), b("642446"));
        schedule.assign(sym("B"), b("242446"));
        assertThat(Validation.crowdedChambers(schedule, List.of(lobby()), 0))
                .extracting(Finding::message)
                .containsExactly("group lobby wants 1 wafers in chamber 1 and the shop builds 0");
        assertThat(Validation.crowdedChambers(Systems.smallSchedule(), List.of(lobby()), 0))
                .isEmpty();
    }

    @Test
    void aReviewWithGroupsCarriesTheCrowdedChambersAndOneWithoutDoesNot() {
        ReviewScope scope = new ReviewScope(2, Optional.empty(), List.of(lobby()), 1);
        assertThat(Validation.review(aGoodSchedule(), scope))
                .extracting(Finding::code)
                .containsExactly("crowded_chamber");
        assertThat(Validation.review(aGoodSchedule(), ReviewScope.of(2).withGroups(List.of(lobby()))))
                .isEmpty();
        assertThat(Validation.review(aGoodSchedule())).isEmpty();
    }

    @Test
    void aReviewAgainstAPlanCarriesTheOffPlanKeys() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AA1", "621456");
        ProgressionPlan plan = new ProgressionPlan(Charts.sixPinChart(), b("622446"), List.of(3), 2);
        assertThat(Validation.review(schedule, ReviewScope.of(2).against(plan)))
                .extracting(Finding::code)
                .contains("off_plan");
        assertThat(Validation.review(schedule, ReviewScope.of(2)))
                .extracting(Finding::code)
                .doesNotContain("off_plan");
    }

    @Test
    void aReviewScopeThatMakesNoSenseIsRefused() {
        assertThatThrownBy(() -> ReviewScope.of(0)).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new ReviewScope(2, Optional.empty(), List.of(), -1))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theReportPutsTheBlockingFindingsFirst() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AB2", "620446");
        schedule = force(schedule, "AA2", "623446");
        List<Finding> found = Validation.review(schedule);
        assertThat(found.get(0).severity()).isSameAs(Severity.BLOCKING);
        List<Severity> severities = found.stream().map(Finding::severity).toList();
        assertThat(severities)
                .isEqualTo(severities.stream()
                        .sorted(Comparator.comparingInt(severity -> WORST_FIRST.indexOf(severity.value())))
                        .toList());
    }

    @Test
    void blockingPullsOutTheOnesThatStopTheJob() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AB2", "620446");
        assertThat(Validation.blocking(Validation.review(schedule))).hasSize(1);
        assertThat(Validation.isBuildable(Validation.review(schedule))).isFalse();
    }

    @Test
    void aFindingRendersFlatForTheReport() {
        KeyingSchedule schedule = force(aGoodSchedule(), "AB2", "620446");
        Map<String, Object> rendered = Validation.duplicateCuts(schedule).get(0).asDict();
        assertThat(rendered).containsEntry("code", "duplicate_cut");
        assertThat(rendered).containsEntry("severity", "blocking");
        assertThat(rendered).containsEntry("symbol", "AB2");
    }

    @Test
    void aFindingWithoutASymbolLeavesItOut() {
        Map<String, Object> rendered = Validation.crowdedChambers(aGoodSchedule(), List.of(lobby()), 1)
                .get(0)
                .asDict();
        assertThat(rendered).doesNotContainKey("symbol");
    }
}
