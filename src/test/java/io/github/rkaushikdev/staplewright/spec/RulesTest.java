package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RulesTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static List<Integer> chambers(List<CutViolation> found) {
        return found.stream()
                .map(violation -> violation.chamber().orElseThrow())
                .toList();
    }

    @Test
    void aReasonableKeyDrawsNoComplaints() {
        assertThat(Rules.checkCuts(Charts.sixPinChart(), b("342156"))).isEmpty();
        assertThat(Rules.isAcceptable(Charts.sixPinChart(), b("342156"))).isTrue();
    }

    @Test
    void aStepPastTheMacsNamesTheLowerChamber() {
        List<CutViolation> found = Rules.checkMacs(Charts.sixPinChart(), b("192156"));
        assertThat(chambers(found)).containsExactly(1);
        assertThat(List.copyOf(found.get(0).facts().entrySet()))
                .containsExactly(Map.entry("step", 8), Map.entry("macs", 7));
    }

    @Test
    void aTighterMacsCatchesWhatTheWiderOneAllows() {
        Bitting bitting = b("16151");
        assertThat(Rules.checkMacs(Charts.fivePinChart(chart -> chart.macs(7)), bitting))
                .isEmpty();
        assertThat(Rules.reasons(Rules.checkMacs(Charts.fivePinChart(), bitting)))
                .containsExactly("macs", "macs");
    }

    @Test
    void aLongRunOfOneDepthIsCaughtOnce() {
        List<CutViolation> found = Rules.checkRuns(new CuttingRules(), b("144442"));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).chamber()).contains(2);
        assertThat(found.get(0).facts()).containsEntry("length", 4);
    }

    @Test
    void aRunExactlyAtTheLimitPasses() {
        assertThat(Rules.checkRuns(new CuttingRules().withMaxRun(3), b("144426")))
                .isEmpty();
    }

    @Test
    void twoSeparateRunsAreTwoComplaints() {
        List<CutViolation> found = Rules.checkRuns(new CuttingRules().withMaxRun(1), b("113355"));
        assertThat(chambers(found)).containsExactly(1, 3, 5);
    }

    @Test
    void aRunThatReachesTheTipIsStillSeen() {
        List<CutViolation> found = Rules.checkRuns(new CuttingRules().withMaxRun(2), b("142222"));
        assertThat(chambers(found)).containsExactly(3);
    }

    @Test
    void aKeyOfOneDepthFailsVarietyAndTheEndOfTheChart() {
        List<CutViolation> found = Rules.checkVariety(Charts.sixPinChart(), new CuttingRules(), b("000000"));
        assertThat(Rules.reasons(found)).containsExactly("variety", "all_shallowest");
    }

    @Test
    void allDeepestIsItsOwnComplaint() {
        List<CutViolation> found = Rules.checkVariety(Charts.sixPinChart(), new CuttingRules(), b("999999"));
        assertThat(Rules.reasons(found)).containsExactly("variety", "all_deepest");
    }

    @Test
    void policyCanStopCaringAboutTheEnds() {
        CuttingRules rules = new CuttingRules()
                .withMinDistinct(1)
                .withForbidAllShallowest(false)
                .withForbidAllDeepest(false);
        assertThat(Rules.checkVariety(Charts.sixPinChart(), rules, b("000000"))).isEmpty();
    }

    @Test
    void theTipRuleIsOffUnlessPolicySetsIt() {
        assertThat(Rules.checkTip(new CuttingRules(), b("342159"))).isEmpty();
        List<CutViolation> found = Rules.checkTip(new CuttingRules().withShallowTipLimit(6), b("342159"));
        assertThat(found.get(0).chamber()).contains(6);
        assertThat(found.get(0).facts()).isEqualTo(Map.of("depth", 9, "limit", 6));
    }

    @Test
    void theBowRuleIsOffUnlessPolicySetsIt() {
        assertThat(Rules.checkBow(new CuttingRules(), b("942156"))).isEmpty();
        List<CutViolation> found = Rules.checkBow(new CuttingRules().withDeepBowLimit(7), b("942156"));
        assertThat(Rules.reasons(found)).containsExactly("bow");
        assertThat(found.get(0).chamber()).contains(1);
        assertThat(found.get(0).facts()).isEqualTo(Map.of("depth", 9, "limit", 7));
        assertThat(found.get(0).message()).isEqualTo("the cut against the shoulder is 9 and policy stops at 7");
    }

    @Test
    void aBowCutExactlyAtTheLimitPasses() {
        assertThat(Rules.checkBow(new CuttingRules().withDeepBowLimit(7), b("742156")))
                .isEmpty();
    }

    @Test
    void theBowRuleReadsOnlyTheFirstChamber() {
        CuttingRules rules = new CuttingRules().withDeepBowLimit(3);
        assertThat(Rules.checkBow(rules, b("249999"))).isEmpty();
        assertThat(Rules.checkTip(rules, b("942159"))).isEmpty();
    }

    @Test
    void theBowAndTipRulesAreReportedShoulderFirst() {
        List<CutViolation> found = Rules.checkCuts(
                Charts.sixPinChart(),
                b("942159"),
                new CuttingRules().withDeepBowLimit(6).withShallowTipLimit(6));
        assertThat(Rules.reasons(found)).containsExactly("bow", "tip");
        assertThat(chambers(found)).containsExactly(1, 6);
        assertThat(Rules.isAcceptable(Charts.sixPinChart(), b("942159"), new CuttingRules().withDeepBowLimit(9)))
                .isTrue();
    }

    @Test
    void aKeyOfTheWrongLengthGetsThatAnswerAlone() {
        assertThat(Rules.reasons(Rules.checkCuts(Charts.sixPinChart(), b("19"))))
                .containsExactly("length");
    }

    @Test
    void aDepthOffTheChartStopsTheLaterRules() {
        DepthAndSpaceChart chart = Charts.sixPinChart(builder -> builder.depths(List.of(0, 1, 2, 3, 4, 5, 6)));
        List<CutViolation> found = Rules.checkCuts(chart, b("199999"));
        assertThat(Rules.reasons(found)).isEqualTo(Collections.nCopies(5, "depth"));
        assertThat(chambers(found)).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    void theRulesStackUpOnOneBadKey() {
        List<CutViolation> found = Rules.checkCuts(
                Charts.sixPinChart(),
                b("199999"),
                new CuttingRules().withMaxRun(3).withShallowTipLimit(6));
        assertThat(Rules.reasons(found)).containsExactly("macs", "run", "tip");
    }

    @Test
    void aViolationRendersFlatForAReport() {
        List<CutViolation> found = Rules.checkMacs(Charts.sixPinChart(), b("192156"));
        Map<String, Object> wanted = new LinkedHashMap<>();
        wanted.put("rule", "macs");
        wanted.put("message", "chambers 1 and 2 step 8, the chart allows 7");
        wanted.put("chamber", 1);
        wanted.put("step", 8);
        wanted.put("macs", 7);
        assertThat(found.get(0).asDict()).containsExactlyEntriesOf(wanted);
    }

    @Test
    void aViolationWithoutAChamberLeavesItOut() {
        List<CutViolation> found = Rules.checkVariety(Charts.sixPinChart(), new CuttingRules(), b("111111"));
        assertThat(found.get(0).asDict()).doesNotContainKey("chamber");
        assertThat(found.get(0).chamber()).isEqualTo(Optional.empty());
    }

    @Test
    void aRulesetCarriesBothHalvesTogether() {
        RuleSet ruleset = new RuleSet(Charts.sixPinChart(), new CuttingRules().withShallowTipLimit(4));
        assertThat(ruleset.accepts(b("342154"))).isTrue();
        assertThat(Rules.reasons(ruleset.check(b("342159")))).containsExactly("tip");
    }

    @Test
    void aRulesetTakesDefaultPolicyIfNoneIsGiven() {
        assertThat(new RuleSet(Charts.sixPinChart()).accepts(b("342159"))).isTrue();
    }

    static Stream<Supplier<CuttingRules>> policyThatForbidsEveryKeyIsRefused() {
        return Stream.of(() -> new CuttingRules().withMaxRun(0), () -> new CuttingRules().withMinDistinct(0));
    }

    @ParameterizedTest
    @MethodSource
    void policyThatForbidsEveryKeyIsRefused(Supplier<CuttingRules> broken) {
        assertThatThrownBy(broken::get).isInstanceOf(IllegalArgumentException.class);
    }
}
