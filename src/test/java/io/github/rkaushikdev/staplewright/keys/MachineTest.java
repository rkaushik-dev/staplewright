package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static List<Integer> dimensionsFor(String text) {
        DepthAndSpaceChart chart = Charts.sixPinChart();
        return new ArrayList<>(
                b(text).depths().stream().map(chart::dimensionFor).toList());
    }

    private static List<Integer> shifted(List<Integer> dimensions, int by) {
        return dimensions.stream().map(mils -> mils + by).toList();
    }

    @Test
    void aCardCarriesOneLineAChamber() {
        CutCard card = Machines.cutCard(Charts.sixPinChart(), b("342156"));
        assertThat(card.cuts()).isEqualTo(6);
        assertThat(card.instructions().get(0).chamber()).isEqualTo(1);
        assertThat(card.instructions().get(0).depth()).isEqualTo(3);
    }

    @Test
    void theCarriagePositionComesOffTheSpacing() {
        CutCard card = Machines.cutCard(Charts.sixPinChart(), b("342156"));
        assertThat(card.instructions().stream().map(CutInstruction::spaceMils).toList())
                .isEqualTo(Charts.sixPinChart().spaces());
    }

    @Test
    void theCutterDepthComesOffTheChart() {
        CutCard card = Machines.cutCard(Charts.sixPinChart(), b("342156"));
        assertThat(card.instructions().get(0).depthMils())
                .isEqualTo(Charts.sixPinChart().dimensionFor(3));
    }

    @Test
    void aMachineOutOfCalibrationShiftsEveryCut() {
        CutCard card = Machines.cutCard(Charts.sixPinChart(), b("342156"), new Calibration().withSpaceOffsetMils(4));
        assertThat(card.instructions().get(0).spaceMils())
                .isEqualTo(Charts.sixPinChart().spaceFor(1) + 4);
        assertThat(card.instructions().get(5).spaceMils())
                .isEqualTo(Charts.sixPinChart().spaceFor(6) + 4);
    }

    @Test
    void aWornCutterShiftsEveryDepth() {
        CutCard card = Machines.cutCard(Charts.sixPinChart(), b("342156"), new Calibration().withDepthOffsetMils(3));
        for (CutInstruction item : card.instructions()) {
            assertThat(item.depthMils()).isEqualTo(Charts.sixPinChart().dimensionFor(item.depth()) + 3);
        }
    }

    @Test
    void aTrueMachineSaysSo() {
        assertThat(new Calibration().isTrue()).isTrue();
        assertThat(new Calibration().withDepthOffsetMils(1).isTrue()).isFalse();
    }

    @Test
    void aKeyTheChartWillNotCutGetsNoCard() {
        assertThatThrownBy(() -> Machines.cutCard(Charts.sixPinChart(), b("3421")))
                .isInstanceOf(BittingError.class);
    }

    @Test
    void theCardPrintsAHeaderAndALineACut() {
        List<String> lines = Machines.cutCard(Charts.sixPinChart(), b("342156")).lines();
        assertThat(lines.get(0)).endsWith("342156");
        assertThat(lines).hasSize(7);
        assertThat(lines.get(1)).startsWith("1  depth 3");
    }

    @Test
    void aCutKeyReadsBackToWhatItWasCutTo() {
        assertThat(Machines.measure(Charts.sixPinChart(), dimensionsFor("342156")))
                .isEqualTo(b("342156"));
    }

    @Test
    void aKeyOfTheWrongLengthCannotBeMeasured() {
        assertThatThrownBy(() -> Machines.measure(Charts.sixPinChart(), List.of(335, 320)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aCutNowhereNearADepthIsRefused() {
        assertThatThrownBy(() -> Machines.measure(Charts.sixPinChart(), List.of(312, 320, 305, 320, 260, 245)))
                .isInstanceOf(BittingError.class);
    }

    @Test
    void aRefusedMeasurementNamesItsChamber() {
        assertThatThrownBy(() -> Machines.measure(Charts.sixPinChart(), List.of(335, 320, 305, 312, 260, 245)))
                .isInstanceOf(BittingError.class)
                .satisfies(refused -> assertThat(((BittingError) refused).facts())
                        .containsEntry("chamber", 4)
                        .containsEntry("mils", 312));
        assertThat(Machines.withinTolerance(Charts.sixPinChart(), List.of(335, 320, 305, 312, 260, 245)))
                .isFalse();
        assertThat(Machines.withinTolerance(Charts.sixPinChart(), List.of(335, 320)))
                .isFalse();
    }

    @Test
    void aGoodKeyVerifiesClean() {
        assertThat(Machines.verify(Charts.sixPinChart(), b("342156"), dimensionsFor("342156")))
                .isEmpty();
    }

    @Test
    void aWrongCutNamesItsChamber() {
        List<Integer> dims = dimensionsFor("342156");
        dims.set(2, Charts.sixPinChart().dimensionFor(6));
        assertThat(Machines.verify(Charts.sixPinChart(), b("342156"), dims)).containsExactly(3);
    }

    @Test
    void twoWrongCutsNameBoth() {
        List<Integer> dims = dimensionsFor("342156");
        dims.set(0, Charts.sixPinChart().dimensionFor(5));
        dims.set(4, Charts.sixPinChart().dimensionFor(7));
        assertThat(Machines.verify(Charts.sixPinChart(), b("342156"), dims)).containsExactly(1, 5);
    }

    @Test
    void aConstantErrorAcrossAKeyIsTheMachine() {
        List<Integer> dims = shifted(dimensionsFor("342156"), 2);
        assertThat(Machines.drift(Charts.sixPinChart(), b("342156"), dims)).contains(2);
    }

    @Test
    void aKeyCutTrueHasNoDrift() {
        assertThat(Machines.drift(Charts.sixPinChart(), b("342156"), dimensionsFor("342156")))
                .isEmpty();
    }

    @Test
    void oneBadChamberIsNotAMachineFault() {
        List<Integer> dims = dimensionsFor("342156");
        dims.set(2, dims.get(2) + 4);
        assertThat(Machines.drift(Charts.sixPinChart(), b("342156"), dims)).isEmpty();
    }

    @Test
    void driftNeedsAKeyOfTheRightLength() {
        assertThatThrownBy(() -> Machines.drift(Charts.sixPinChart(), b("342156"), List.of(335, 320)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aTracedCopyReportsHowShallowItCameOut() {
        List<Integer> original = dimensionsFor("342156");
        List<Integer> copy = shifted(original, 3);
        assertThat(Machines.duplicateWear(original, copy)).isEqualTo(3);
    }

    @Test
    void aCopyCutTrueWearsNothing() {
        List<Integer> original = dimensionsFor("342156");
        assertThat(Machines.duplicateWear(original, List.copyOf(original))).isZero();
    }

    @Test
    void theWorstChamberIsTheOneReported() {
        List<Integer> original = dimensionsFor("342156");
        List<Integer> copy = new ArrayList<>(original);
        copy.set(0, copy.get(0) + 2);
        copy.set(3, copy.get(3) + 6);
        assertThat(Machines.duplicateWear(original, copy)).isEqualTo(6);
    }

    @Test
    void twoKeysOfDifferentLengthsCannotBeCompared() {
        assertThatThrownBy(() -> Machines.duplicateWear(List.of(335, 320), List.of(335)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void comparingNothingIsRefused() {
        assertThatThrownBy(() -> Machines.duplicateWear(List.of(), List.of())).isInstanceOf(InputError.class);
    }

    @Test
    void toleranceIsAYesOrNoForTheBench() {
        assertThat(Machines.withinTolerance(Charts.sixPinChart(), dimensionsFor("342156")))
                .isTrue();
        assertThat(Machines.withinTolerance(Charts.sixPinChart(), List.of(312, 320, 305, 320, 260, 245)))
                .isFalse();
        assertThat(Machines.withinTolerance(Charts.sixPinChart(), List.of(335))).isFalse();
    }
}
