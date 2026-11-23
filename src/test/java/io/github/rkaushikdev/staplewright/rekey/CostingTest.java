package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static io.github.rkaushikdev.staplewright.support.Keyed.keyingGroups;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.CylinderFormat;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.util.Numbers;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CostingTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    /** A decision and the coverage it was read from, which every estimate needs together. */
    private record Decided(ScopeDecision decision, CoverageMap coverage) {
        Estimate estimate(List<String> keysToCut) {
            return Estimate.of(decision, coverage, keysToCut);
        }
    }

    private static Decided decisionFor(String symbol, String tag) {
        return decisionFor(symbol, tag, aCoverage());
    }

    private static Decided decisionFor(String symbol, String tag, CoverageMap coverage) {
        Incident incident = new Incident("I-1", tag, symbol, Cause.LOST, WHEN);
        return new Decided(ScopeDecision.decide(Assessment.assess(incident, coverage, aLedger()), coverage), coverage);
    }

    private static CoverageMap aCoreCoverage() {
        HardwareRegister register = new HardwareRegister(aCampus());
        for (String[] pair : new String[][] {{"K-1", "MOO-214"}, {"K-2", "MOO-216"}}) {
            register.add(new Cylinder(pair[0], CylinderFormat.SFIC, "A2", 7));
            register.install(pair[0], pair[1], Position.LOCKSET);
            register.assignGroup(pair[0], "moore offices");
        }
        return new CoverageMap(register, keyingGroups());
    }

    @Test
    void aSmallRekeyCostsBenchTimeDoorTimeAndOneTrip() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of("41208"));
        assertThat(price.cylinders()).isEqualTo(2);
        assertThat(price.keys()).isEqualTo(1);
        assertThat(price.buildings()).isEqualTo(1);
        assertThat(price.benchMinutes()).isEqualTo(12 * 2 + 3);
        assertThat(price.doorMinutes()).isEqualTo(20 * 2);
        assertThat(price.travelMinutes()).isEqualTo(25);
    }

    @Test
    void theTotalsAddUpToTheParts() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of("41208"));
        assertThat(price.totalMinutes()).isEqualTo(price.benchMinutes() + price.doorMinutes() + price.travelMinutes());
        assertThat(price.totalCents()).isEqualTo(price.labourCents() + price.partsCents());
    }

    @Test
    void labourIsPricedOffTheMinutes() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of("41208"));
        assertThat(price.labourCents()).isEqualTo((int) Numbers.round(price.totalMinutes() * 5200 / 60.0));
    }

    @Test
    void partsAreBlanksPlusPins() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of("41208", "41209"));
        assertThat(price.partsCents()).isEqualTo(180 * 2 + 12 * 12 * 2);
    }

    @Test
    void aBiggerScopeCostsMore() {
        Decided small = decisionFor("AA1", "41207");
        Decided large = decisionFor("GGM", "41211", small.coverage());
        assertThat(large.estimate(List.of()).totalCents())
                .isGreaterThan(small.estimate(List.of()).totalCents());
    }

    @Test
    void twoBuildingsMeansTwoTrips() {
        Estimate price = decisionFor("GGM", "41211").estimate(List.of());
        assertThat(price.buildings()).isEqualTo(2);
        assertThat(price.travelMinutes()).isEqualTo(50);
    }

    @Test
    void coresAreQuickAtTheDoorAndTheSameAtTheBench() {
        Estimate price = decisionFor("AA1", "41207", aCoreCoverage()).estimate(List.of());
        assertThat(price.cores()).isEqualTo(2);
        assertThat(price.doorMinutes()).isEqualTo(2 * 2);
        assertThat(price.benchMinutes()).isEqualTo(12 * 2);
    }

    @Test
    void cuttingNoKeysCostsNoBlanks() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of());
        assertThat(price.partsCents()).isEqualTo(12 * 12 * 2);
        assertThat(price.keys()).isZero();
    }

    @Test
    void anEmptyDecisionCostsNothingButStillPrices() {
        Estimate price = decisionFor("ZZ9", "41207").estimate(List.of());
        assertThat(price.cylinders()).isZero();
        assertThat(price.totalCents()).isZero();
        assertThat(price.hours()).isEqualTo(0.0);
    }

    @Test
    void theSummaryNamesTheTwoNumbersThatMatter() {
        String summary = decisionFor("AA1", "41207").estimate(List.of("41208")).summary();
        assertThat(summary).startsWith("2 cylinders and 1 keys");
    }

    @Test
    void hoursAreRoundedForTheMemo() {
        Estimate price = decisionFor("AA1", "41207").estimate(List.of("41208"));
        assertThat(price.hours()).isEqualTo(Numbers.round(price.totalMinutes() / 60.0, 2));
    }

    @Test
    void aDifferentRateCardMovesTheNumber() {
        Decided found = decisionFor("AA1", "41207");
        Estimate cheap = Estimate.of(
                found.decision(),
                found.coverage(),
                List.of("41208"),
                new RateCard().toBuilder().labourCentsPerHour(2600).build());
        Estimate dear = Estimate.of(found.decision(), found.coverage(), List.of("41208"), RateCard.DEFAULT);
        assertThat(cheap.labourCents()).isLessThan(dear.labourCents());
        assertThat(cheap.partsCents()).isEqualTo(dear.partsCents());
    }

    @Test
    void comparingTwoOptionsGivesTheSaving() {
        Decided found = decisionFor("AA1", "41207");
        Estimate dear = found.estimate(List.of("41208"));
        Estimate cheap = found.estimate(List.of());
        assertThat(Estimate.compare(dear, cheap)).isPositive();
    }

    @Test
    void theCheapestOptionIsNamed() {
        Decided mortise = decisionFor("AA1", "41207");
        Decided cores = decisionFor("AA1", "41207", aCoreCoverage());
        List<PricedOption> options = List.of(
                new PricedOption("repin in place", mortise.estimate(List.of("41208"))),
                new PricedOption("swap cores", cores.estimate(List.of())));
        assertThat(Estimate.cheaperOf(options)).isEqualTo("swap cores");
    }

    @Test
    void comparingNothingIsRefused() {
        assertThatThrownBy(() -> Estimate.cheaperOf(List.of())).isInstanceOf(InputError.class);
    }

    static Stream<UnaryOperator<RateCard>> aRateCardThatMakesNoSenseIsRefused() {
        return Stream.of(
                rates -> rates.toBuilder().benchMinutesPerCylinder(-1).build(),
                rates -> rates.toBuilder().labourCentsPerHour(0).build(),
                rates -> rates.toBuilder().blankCents(-5).build(),
                rates -> rates.toBuilder().afterHoursPercent(99).build());
    }

    @ParameterizedTest
    @MethodSource
    void aRateCardThatMakesNoSenseIsRefused(UnaryOperator<RateCard> broken) {
        assertThatThrownBy(() -> broken.apply(new RateCard())).isInstanceOf(InputError.class);
    }

    @Test
    void afterHoursTheDoorAndTheDriveCostMoreAndTheBenchDoesNot() {
        Decided found = decisionFor("AA1", "41207");
        Estimate day = Estimate.of(found.decision(), found.coverage(), List.of("41208"), RateCard.DEFAULT);
        Estimate night = Estimate.of(found.decision(), found.coverage(), List.of("41208"), RateCard.DEFAULT, true);
        assertThat(night.totalMinutes()).isEqualTo(day.totalMinutes());
        assertThat(night.partsCents()).isEqualTo(day.partsCents());
        int wanted = RateCard.DEFAULT.labourCents(day.benchMinutes(), false)
                + RateCard.DEFAULT.labourCents(day.doorMinutes() + day.travelMinutes(), true);
        assertThat(night.labourCents()).isEqualTo(wanted).isGreaterThan(day.labourCents());
    }

    @Test
    void anAfterHoursRateOfTheDayRateChangesNothing() {
        Decided found = decisionFor("AA1", "41207");
        RateCard flat = new RateCard().toBuilder().afterHoursPercent(100).build();
        Estimate day = Estimate.of(found.decision(), found.coverage(), List.of("41208"), flat);
        Estimate night = Estimate.of(found.decision(), found.coverage(), List.of("41208"), flat, true);
        assertThat(night).isEqualTo(day);
    }

    @Test
    void labourIsPricedToTheCent() {
        RateCard rates = RateCard.DEFAULT;
        assertThat(rates.labourCents(60, false)).isEqualTo(5200);
        assertThat(rates.labourCents(60, true)).isEqualTo(7800);
        assertThat(rates.labourCents(7, false)).isEqualTo(607);
        assertThat(rates.labourCents(7, true)).isEqualTo(910);
    }

    @Test
    void aBuilderChangesOnlyWhatItIsTold() {
        RateCard changed = RateCard.DEFAULT.toBuilder()
                .pinCents(20)
                .travelMinutesPerBuilding(40)
                .build();
        assertThat(changed.pinCents()).isEqualTo(20);
        assertThat(changed.travelMinutesPerBuilding()).isEqualTo(40);
        assertThat(changed.toBuilder().pinCents(12).travelMinutesPerBuilding(25).build())
                .isEqualTo(RateCard.DEFAULT);
    }

    @Test
    void onlyAWeekendWindowIsAfterHours() {
        assertThat(WindowKind.WEEKEND.isAfterHours()).isTrue();
        assertThat(WindowKind.VACATION.isAfterHours()).isFalse();
        assertThat(WindowKind.TERM.isAfterHours()).isFalse();
        assertThat(WindowKind.SHUTDOWN.isAfterHours()).isFalse();
    }
}
