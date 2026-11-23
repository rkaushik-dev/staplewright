package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScopeTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static List<String> rendered(List<KeySymbol> symbols) {
        return symbols.stream().map(KeySymbol::render).toList();
    }

    private static Incident anIncident(String symbol, String tag) {
        return new Incident("I-2025-004", tag, symbol, Cause.LOST, WHEN, "kmaki");
    }

    private static ScopeDecision decisionFor(String symbol) {
        return decisionFor(symbol, "41207", policy -> policy);
    }

    private static ScopeDecision decisionFor(String symbol, String tag) {
        return decisionFor(symbol, tag, policy -> policy);
    }

    private static ScopeDecision decisionFor(String symbol, UnaryOperator<ScopePolicy> policyBits) {
        return decisionFor(symbol, "41207", policyBits);
    }

    private static ScopeDecision decisionFor(String symbol, String tag, UnaryOperator<ScopePolicy> policyBits) {
        CoverageMap coverage = aCoverage();
        Assessment found = Assessment.assess(anIncident(symbol, tag), coverage, aLedger());
        return ScopeDecision.decide(found, coverage, policyBits.apply(new ScopePolicy()));
    }

    @Test
    void aChangeKeyDragsInOnlyItsOwnCylinders() {
        Closure closure = Closure.of(aCoverage(), List.of(sym("AA1")));
        assertThat(rendered(closure.symbols())).containsExactly("AA1");
        assertThat(closure.cylinders()).containsExactly("C-1001", "C-1002");
    }

    @Test
    void aMasterDragsInEverythingItTurns() {
        Closure closure = Closure.of(aCoverage(), List.of(sym("AA")));
        assertThat(rendered(closure.symbols())).containsExactly("AA");
        assertThat(closure.cylinders()).containsExactly("C-1001", "C-1002", "C-1003");
    }

    @Test
    void theStrictPolicyPullsInTheOtherKeysOnTheCylinder() {
        Closure closure = Closure.of(aCoverage(), List.of(sym("AA1")), true);
        Set<String> rendered = closure.symbols().stream().map(KeySymbol::render).collect(Collectors.toSet());
        assertThat(rendered).containsAll(Set.of("GGM", "A", "AA", "AA1"));
        assertThat(closure.cylinders()).hasSize(6);
    }

    @Test
    void theStrictPolicyReachesAFixpointRatherThanLooping() {
        Closure closure = Closure.of(aCoverage(), List.of(sym("AA1")), true);
        Closure again = Closure.of(aCoverage(), closure.symbols(), true);
        assertThat(again).isEqualTo(new Closure(closure.symbols(), closure.cylinders()));
    }

    @Test
    void aSymbolThatTurnsNothingClosesOverNothing() {
        Closure closure = Closure.of(aCoverage(), List.of(sym("ZZ9")));
        assertThat(closure.cylinders()).isEmpty();
        assertThat(rendered(closure.symbols())).containsExactly("ZZ9");
    }

    @Test
    void aLostChangeKeyRekeysItsGroup() {
        ScopeDecision found = decisionFor("AA1");
        assertThat(found.scope()).isSameAs(Scope.GROUP);
        assertThat(found.cylinderCount()).isEqualTo(2);
        assertThat(found.overCap()).isFalse();
    }

    @Test
    void aLostMasterSaysSoInTheReason() {
        ScopeDecision found = decisionFor("AA", "41209");
        assertThat(found.reason()).contains("master");
        assertThat(found.cylinderCount()).isEqualTo(3);
    }

    @Test
    void aKeyReachingTwoBuildingsIsASystemRekey() {
        ScopeDecision found = decisionFor("GGM", "41211");
        assertThat(found.scope()).isSameAs(Scope.SYSTEM);
        assertThat(found.cylinderCount()).isEqualTo(6);
    }

    @Test
    void oneCylinderAloneIsAnOpeningRekey() {
        ScopeDecision found = decisionFor("AA2");
        assertThat(found.scope()).isSameAs(Scope.OPENING);
        assertThat(found.cylinderCount()).isEqualTo(1);
    }

    @Test
    void aKeyOnRecordAsTurningNothingGetsNoWork() {
        ScopeDecision found = decisionFor("ZZ9");
        assertThat(found.doesNothing()).isTrue();
        assertThat(found.cylinders()).isEmpty();
    }

    @Test
    void aSmallIncidentUnderTheThresholdIsLeftAlone() {
        ScopeDecision found = decisionFor("AA1", policy -> policy.with(scope -> scope.ignoreBelowOpenings(3)));
        assertThat(found.doesNothing()).isTrue();
        assertThat(found.reason()).contains("threshold");
    }

    @Test
    void aMasterIsRekeyedEvenUnderTheThreshold() {
        ScopeDecision found = decisionFor("AA", "41209", policy -> policy.with(scope -> scope.ignoreBelowOpenings(10)));
        assertThat(found.doesNothing()).isFalse();
        assertThat(found.reason()).contains("master");
    }

    @Test
    void anExteriorDoorForcesTheWorkThroughTheThreshold() {
        ScopeDecision found = decisionFor(
                "A",
                "41210",
                policy -> policy.with(scope -> scope.ignoreBelowOpenings(10).alwaysRekeyMasters(false)));
        assertThat(found.reason()).contains("exterior");
    }

    @Test
    void aShopThatDoesNotCareAboutExteriorsCanSaySo() {
        CoverageMap coverage = aCoverage();
        Assessment found = Assessment.assess(anIncident("A", "41210"), coverage, aLedger());
        ScopePolicy policy = new ScopePolicy().with(scope -> scope.alwaysRekeyMasters(false)
                .alwaysRekeyExterior(false)
                .ignoreBelowOpenings(10));
        assertThat(ScopeDecision.decide(found, coverage, policy).doesNothing()).isTrue();
    }

    @Test
    void aTheftIsActedOnWhateverTheThresholdSays() {
        CoverageMap coverage = aCoverage();
        Incident incident =
                new Incident("I-2025-004", "41207", "AA1", Cause.STOLEN, WHEN, "kmaki", "taken from a coat");
        Assessment found = Assessment.assess(incident, coverage, aLedger());
        ScopePolicy policy = new ScopePolicy().with(scope -> scope.ignoreBelowOpenings(10));
        assertThat(ScopeDecision.decide(found, coverage, policy).reason()).contains("taken");
    }

    private static ScopeDecision underAHighThreshold(Cause cause, UnaryOperator<ScopePolicy> policyBits) {
        CoverageMap coverage = aCoverage();
        Incident incident = new Incident("I-2025-004", "41207", "AA1", cause, WHEN, "kmaki", "reported at the counter");
        Assessment found = Assessment.assess(incident, coverage, aLedger());
        ScopePolicy policy = policyBits.apply(new ScopePolicy().with(scope -> scope.ignoreBelowOpenings(10)));
        return ScopeDecision.decide(found, coverage, policy);
    }

    @Test
    void aKeyCopiedWithoutASignatureIsActedOnWhateverTheThresholdSays() {
        ScopeDecision found = underAHighThreshold(Cause.DUPLICATED, policy -> policy);
        assertThat(found.doesNothing()).isFalse();
        assertThat(found.reason()).isEqualTo("copies were cut that nobody signed for");
        assertThat(found.cylinders()).containsExactly("C-1001", "C-1002");
    }

    @Test
    void anUnreturnedKeyIsOnlyLostUnlessPolicySaysOtherwise() {
        assertThat(underAHighThreshold(Cause.UNRETURNED, policy -> policy).doesNothing())
                .isTrue();
        ScopeDecision strict =
                underAHighThreshold(Cause.UNRETURNED, policy -> policy.with(scope -> scope.rekeyUnreturned(true)));
        assertThat(strict.doesNothing()).isFalse();
        assertThat(strict.reason()).isEqualTo("a leaver never handed the key back");
    }

    @Test
    void aLostOrUnaccountedKeyUnderTheThresholdIsLeftAlone() {
        assertThat(underAHighThreshold(Cause.LOST, policy -> policy.with(scope -> scope.rekeyUnreturned(true)))
                        .doesNothing())
                .isTrue();
        assertThat(underAHighThreshold(Cause.UNACCOUNTED, policy -> policy).reason())
                .isEqualTo("under the threshold the shop acts on");
    }

    @Test
    void aPolicyBuiltWithNothingChangedIsTheSamePolicy() {
        assertThat(new ScopePolicy().with(scope -> scope)).isEqualTo(ScopePolicy.DEFAULT);
        assertThat(ScopePolicy.DEFAULT.rekeyUnreturned()).isFalse();
    }

    @Test
    void goingOverTheCapIsFlaggedRatherThanRefused() {
        ScopeDecision found = decisionFor("GGM", "41211", policy -> policy.with(scope -> scope.cylinderCap(4)));
        assertThat(found.overCap()).isTrue();
        assertThat(found.reason()).contains("cap of 4");
        assertThat(found.cylinderCount()).isEqualTo(6);
    }

    @Test
    void stayingUnderTheCapIsNotFlagged() {
        assertThat(decisionFor("AA1", policy -> policy.with(scope -> scope.cylinderCap(4)))
                        .overCap())
                .isFalse();
    }

    @Test
    void theSummaryReadsLikeAWorkOrderLine() {
        String summary = decisionFor("AA1").summary();
        assertThat(summary).startsWith("group: 2 cylinders");
    }

    static Stream<Supplier<ScopePolicy>> aPolicyThatContradictsItselfIsRefused() {
        return Stream.of(() -> new ScopePolicy().with(scope -> scope.ignoreBelowOpenings(-1)), () -> new ScopePolicy()
                .with(scope -> scope.cylinderCap(0)));
    }

    @ParameterizedTest
    @MethodSource
    void aPolicyThatContradictsItselfIsRefused(Supplier<ScopePolicy> broken) {
        assertThatThrownBy(broken::get).isInstanceOf(IllegalArgumentException.class);
    }
}
