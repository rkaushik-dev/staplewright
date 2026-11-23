package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.Calibration;
import io.github.rkaushikdev.staplewright.keys.CutCard;
import io.github.rkaushikdev.staplewright.keys.CutInstruction;
import io.github.rkaushikdev.staplewright.keys.Machines;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GaugingTest {

    private static final int NOMINAL = 1400;

    private static Blade aBlade() {
        return aBlade(NOMINAL);
    }

    private static Blade aBlade(int lengthMils) {
        return aBlade(lengthMils, NOMINAL);
    }

    private static Blade aBlade(int lengthMils, int nominalMils) {
        return new Blade("LC-6", lengthMils, nominalMils);
    }

    private static DepthAndSpaceChart tipGauged() {
        return Charts.sixPinChart(chart -> chart.gaugedFromTip(true));
    }

    @Test
    void aBladeHasALength() {
        assertThatThrownBy(() -> aBlade(0)).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> aBlade(NOMINAL, 0)).isInstanceOf(InputError.class);
    }

    @Test
    void aBladeReportsHowFarOffNominalItIs() {
        assertThat(aBlade().isNominal()).isTrue();
        assertThat(aBlade(1406).errorMils()).isEqualTo(6);
        assertThat(aBlade(1394).errorMils()).isEqualTo(-6);
    }

    @Test
    void theShoulderPositionsAreTheChart() {
        assertThat(Gauging.shoulderPositions(Charts.sixPinChart()))
                .isEqualTo(Charts.sixPinChart().spaces());
    }

    @Test
    void theTipPositionsMeasureBackFromTheTip() {
        List<Integer> found = Gauging.tipPositions(Charts.sixPinChart(), aBlade());
        assertThat(found.get(0)).isEqualTo(1400 - 231);
        assertThat(found.get(found.size() - 1)).isEqualTo(1400 - 1011);
    }

    @Test
    void theTipPositionsStayInChamberOrder() {
        List<Integer> found = Gauging.tipPositions(Charts.sixPinChart(), aBlade());
        assertThat(found)
                .isEqualTo(found.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Test
    void theConversionGoesBackWhereItCameFrom() {
        DepthAndSpaceChart chart = Charts.sixPinChart();
        Blade blade = aBlade();
        assertThat(Gauging.fromTipToShoulder(blade, Gauging.tipPositions(chart, blade)))
                .isEqualTo(Gauging.shoulderPositions(chart));
    }

    @Test
    void aShoulderGaugedFamilyUsesTheChartPositions() {
        assertThat(Gauging.positionsFor(Charts.sixPinChart(), aBlade()))
                .isEqualTo(Charts.sixPinChart().spaces());
    }

    @Test
    void aShoulderGaugedFamilyDoesNotCareAboutALongBlank() {
        DepthAndSpaceChart chart = Charts.sixPinChart();
        assertThat(Gauging.positionsFor(chart, aBlade(1406))).isEqualTo(chart.spaces());
        assertThat(Gauging.driftOn(chart, aBlade(1406))).isZero();
    }

    @Test
    void aTipGaugedFamilyOnANominalBlankLandsInTheSamePlace() {
        DepthAndSpaceChart chart = tipGauged();
        assertThat(Gauging.positionsFor(chart, aBlade())).isEqualTo(chart.spaces());
    }

    @Test
    void aTipGaugedFamilyDriftsOnALongBlank() {
        assertThat(Gauging.driftOn(tipGauged(), aBlade(1406))).isEqualTo(6);
    }

    @Test
    void aTipGaugedFamilyDriftsTheOtherWayOnAShortBlank() {
        assertThat(Gauging.driftOn(tipGauged(), aBlade(1392))).isEqualTo(-8);
    }

    @Test
    void aSmallErrorIsInsideTheAllowance() {
        DepthAndSpaceChart chart = tipGauged();
        assertThat(Gauging.withinTolerance(chart, aBlade(1403))).isTrue();
        assertThat(Gauging.withinTolerance(chart, aBlade(1410))).isFalse();
    }

    @Test
    void aShoulderGaugedFamilyIsAlwaysInsideTheAllowance() {
        assertThat(Gauging.withinTolerance(Charts.sixPinChart(), aBlade(1500))).isTrue();
    }

    @Test
    void aTighterAllowanceCatchesMore() {
        assertThat(Gauging.withinTolerance(tipGauged(), aBlade(1403), 1)).isFalse();
    }

    @Test
    void aNegativeAllowanceIsRefused() {
        assertThatThrownBy(() -> Gauging.withinTolerance(Charts.sixPinChart(), aBlade(), -1))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aBladeNeedsMetalPastTheLastChamber() {
        assertThat(Gauging.bladeLongEnough(Charts.sixPinChart(), aBlade())).isTrue();
        assertThat(Gauging.bladeLongEnough(Charts.sixPinChart(), aBlade(1050))).isFalse();
    }

    @Test
    void theClearanceCanBeNamed() {
        assertThat(Gauging.bladeLongEnough(Charts.sixPinChart(), aBlade(1050), 30))
                .isTrue();
    }

    @Test
    void aGoodBlankHasNothingWrongWithIt() {
        assertThat(Gauging.checkBlade(Charts.sixPinChart(), aBlade())).isEmpty();
    }

    @Test
    void aShortBlankIsReported() {
        List<String> found = Gauging.checkBlade(Charts.sixPinChart(), aBlade(1040));
        assertThat(found.get(0)).contains("too short");
    }

    @Test
    void aLongBlankOnATipGaugedFamilyIsReported() {
        List<String> found = Gauging.checkBlade(tipGauged(), aBlade(1412));
        assertThat(found.get(0)).contains("off nominal");
        assertThat(found.get(0)).contains("gauges from the tip");
    }

    @Test
    void bothProblemsAreReportedTogether() {
        List<String> found = Gauging.checkBlade(tipGauged(), aBlade(1040, 1400));
        assertThat(found).hasSize(2);
    }

    @Test
    void oneChamberCanBeAskedFor() {
        assertThat(Gauging.positionOf(Charts.sixPinChart(), 1, aBlade())).isEqualTo(231);
        assertThat(Gauging.positionOf(Charts.sixPinChart(), 6, aBlade())).isEqualTo(1011);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -1})
    void aChamberOffTheEndIsRefused(int chamber) {
        assertThatThrownBy(() -> Gauging.positionOf(Charts.sixPinChart(), chamber, aBlade()))
                .isInstanceOf(BittingError.class);
    }

    @Test
    void aTipGaugedFamilyMovesEveryCutWithALongBlank() {
        DepthAndSpaceChart chart = tipGauged();
        assertThat(Gauging.positionsFor(chart, aBlade(1406)))
                .isEqualTo(chart.spaces().stream().map(space -> space + 6).toList());
        assertThat(Gauging.positionOf(chart, 6, aBlade(1392))).isEqualTo(1011 - 8);
    }

    @Test
    void aShortBlankOnATipGaugedFamilyRunsTheFirstCutIntoTheShoulder() {
        List<String> found = Gauging.checkBlade(tipGauged(), aBlade(1160, 1400));
        assertThat(found.get(0))
                .isEqualTo("LC-6 is too short for C keyway, six pin, the cut against the shoulder would sit at -9");
        assertThat(Gauging.bladeLongEnough(tipGauged(), aBlade(1160, 1400))).isFalse();
        assertThat(Gauging.bladeLongEnough(tipGauged(), aBlade(1180, 1400))).isTrue();
    }

    @Test
    void aTipGaugedCardSetsTheCarriageOffTheBlankInTheJaw() {
        DepthAndSpaceChart chart = tipGauged();
        CutCard card = Machines.cutCard(chart, Bitting.parse("342156"), Calibration.TRUE, aBlade(1403));
        assertThat(card.instructions().stream().map(CutInstruction::spaceMils).toList())
                .isEqualTo(chart.spaces().stream().map(space -> space + 3).toList());
        CutCard shoulder =
                Machines.cutCard(Charts.sixPinChart(), Bitting.parse("342156"), Calibration.TRUE, aBlade(1403));
        assertThat(shoulder).isEqualTo(Machines.cutCard(Charts.sixPinChart(), Bitting.parse("342156")));
    }

    @Test
    void aBlankTheChartCannotBeCutOnGetsNoCard() {
        assertThatThrownBy(() -> Machines.cutCard(tipGauged(), Bitting.parse("342156"), Calibration.TRUE, aBlade(1412)))
                .isInstanceOf(BittingError.class)
                .satisfies(refused -> assertThat(((BittingError) refused).facts())
                        .containsEntry("part", "LC-6")
                        .containsKey("reasons"));
        assertThatThrownBy(() ->
                        Machines.cutCard(Charts.sixPinChart(), Bitting.parse("342156"), Calibration.TRUE, aBlade(1040)))
                .isInstanceOf(BittingError.class);
    }
}
