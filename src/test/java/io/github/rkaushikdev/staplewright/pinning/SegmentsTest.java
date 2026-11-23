package io.github.rkaushikdev.staplewright.pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SegmentsTest {

    private static PinSegment bottom(int mils) {
        return new PinSegment(SegmentKind.BOTTOM, mils);
    }

    private static PinSegment wafer(int mils) {
        return new PinSegment(SegmentKind.WAFER, mils);
    }

    private static PinSegment driver(int mils) {
        return driver(mils, DriverProfile.STANDARD);
    }

    private static PinSegment driver(int mils, DriverProfile profile) {
        return new PinSegment(SegmentKind.DRIVER, mils, profile);
    }

    private static PinKit aKit() {
        return PinKit.standard("bench", List.of(150, 165, 180), List.of(30, 45, 60), List.of(175, 190), 4);
    }

    @Test
    void aBinCodeNamesTheKindAndTheLength() {
        assertThat(bottom(150).binCode()).isEqualTo("B150");
        assertThat(wafer(30).binCode()).isEqualTo("W30");
        assertThat(driver(190).binCode()).isEqualTo("D190");
    }

    @Test
    void aSecurityDriverSaysSoOnTheBin() {
        assertThat(driver(190, DriverProfile.SPOOL).binCode()).isEqualTo("D190S");
        assertThat(driver(190, DriverProfile.SERRATED).binCode()).isEqualTo("D190S");
        assertThat(driver(190, DriverProfile.SPOOL).toString()).isEqualTo("D190S");
    }

    @Test
    void aSegmentNeedsALength() {
        assertThatThrownBy(() -> bottom(0)).isInstanceOf(PinningError.class);
    }

    @Test
    void onlyDriversComeInProfiles() {
        assertThatThrownBy(() -> new PinSegment(SegmentKind.BOTTOM, 150, DriverProfile.SPOOL))
                .isInstanceOf(PinningError.class);
    }

    @Test
    void twoSegmentsOfTheSameShapeAreTheSamePart() {
        assertThat(bottom(150)).isEqualTo(bottom(150));
        assertThat(Set.copyOf(List.of(bottom(150), bottom(150)))).hasSize(1);
        assertThat(bottom(150)).isNotEqualTo(wafer(150));
    }

    @Test
    void aNewKitIsStockedEvenly() {
        PinKit kit = aKit();
        assertThat(kit.stockOf(bottom(150))).isEqualTo(4);
        assertThat(kit.stockOf(wafer(45))).isEqualTo(4);
        assertThat(kit.total()).isEqualTo(8 * 4);
    }

    @Test
    void aBinNobodyStockedHoldsNothing() {
        assertThat(aKit().stockOf(bottom(999))).isZero();
        assertThat(aKit().holds(bottom(999))).isFalse();
    }

    @Test
    void theLengthsOnHandComeBackInOrder() {
        PinKit kit = aKit();
        assertThat(kit.lengths(SegmentKind.BOTTOM)).containsExactly(150, 165, 180);
        assertThat(kit.lengths(SegmentKind.WAFER)).containsExactly(30, 45, 60);
    }

    @Test
    void anEmptyBinDropsOutOfTheLengths() {
        PinKit kit = aKit();
        kit.draw(Collections.nCopies(4, bottom(165)));
        assertThat(kit.lengths(SegmentKind.BOTTOM)).containsExactly(150, 180);
    }

    @Test
    void lengthsCanBeAskedForOneProfile() {
        PinKit kit = aKit();
        kit.add(driver(190, DriverProfile.SPOOL), 6);
        assertThat(kit.lengths(SegmentKind.DRIVER)).containsExactly(175, 190);
        assertThat(kit.lengths(SegmentKind.DRIVER, DriverProfile.SPOOL)).containsExactly(190);
    }

    @Test
    void aDrawTakesEveryPartItAskedFor() {
        PinKit kit = aKit();
        kit.draw(List.of(bottom(150), bottom(150), wafer(30)));
        assertThat(kit.stockOf(bottom(150))).isEqualTo(2);
        assertThat(kit.stockOf(wafer(30))).isEqualTo(3);
    }

    @Test
    void aDrawThatCannotBeFilledTakesNothing() {
        PinKit kit = aKit();
        assertThatThrownBy(() -> kit.draw(Collections.nCopies(5, bottom(150)))).isInstanceOf(CapacityError.class);
        assertThat(kit.stockOf(bottom(150))).isEqualTo(4);
    }

    @Test
    void theShortfallNamesTheBinsAndTheCounts() {
        PinKit kit = aKit();
        List<PinSegment> wanted = new ArrayList<>(Collections.nCopies(6, bottom(150)));
        wanted.add(wafer(999));
        Map<PinSegment, Integer> missing = kit.shortfall(wanted);
        assertThat(missing.get(bottom(150))).isEqualTo(2);
        assertThat(missing.get(wafer(999))).isEqualTo(1);
    }

    @Test
    void aDrawThatFitsReportsNoShortfall() {
        assertThat(aKit().shortfall(List.of(bottom(150), wafer(30)))).isEmpty();
    }

    @Test
    void shortfallTakesNothingOffTheShelf() {
        PinKit kit = aKit();
        kit.shortfall(Collections.nCopies(6, bottom(150)));
        assertThat(kit.stockOf(bottom(150))).isEqualTo(4);
    }

    @Test
    void partsOffACylinderGoBackInTheDrawer() {
        PinKit kit = aKit();
        kit.draw(List.of(bottom(150), wafer(30)));
        kit.putBack(List.of(bottom(150), wafer(30), driver(175)));
        assertThat(kit.stockOf(bottom(150))).isEqualTo(4);
        assertThat(kit.stockOf(driver(175))).isEqualTo(5);
    }

    @Test
    void restockingABinThatWasNeverThere() {
        PinKit kit = new PinKit("new");
        kit.add(bottom(150), 12);
        assertThat(kit.stockOf(bottom(150))).isEqualTo(12);
    }

    @Test
    void restockingNothingIsRefused() {
        assertThatThrownBy(() -> new PinKit("new").add(bottom(150), 0)).isInstanceOf(PinningError.class);
    }

    @Test
    void aKitCannotStartWithANegativeBin() {
        assertThatThrownBy(() -> new PinKit("broken", Map.of(bottom(150), -2))).isInstanceOf(PinningError.class);
    }

    @Test
    void aBinStockedAtZeroIsNotABin() {
        Map<PinSegment, Integer> stock = new LinkedHashMap<>();
        stock.put(bottom(150), 0);
        stock.put(wafer(30), 3);
        PinKit kit = new PinKit("new", stock);
        assertThat(kit.bins()).containsExactly(new PinKit.Bin(wafer(30), 3));
    }

    @Test
    void binsPrintInBinCodeOrder() {
        Map<PinSegment, Integer> stock = new LinkedHashMap<>();
        stock.put(driver(190), 1);
        stock.put(bottom(150), 2);
        stock.put(wafer(30), 3);
        PinKit kit = new PinKit("new", stock);
        assertThat(kit.bins().stream().map(bin -> bin.segment().binCode())).containsExactly("B150", "D190", "W30");
    }

    @Test
    void aCopyDoesNotShareTheDrawer() {
        PinKit kit = aKit();
        PinKit clone = kit.copy();
        clone.draw(Collections.nCopies(4, bottom(150)));
        assertThat(kit.stockOf(bottom(150))).isEqualTo(4);
        assertThat(clone.stockOf(bottom(150))).isZero();
    }

    @Test
    void aBinDrawnDownToNothingIsStillABin() {
        PinKit kit = aKit();
        kit.draw(Collections.nCopies(4, wafer(45)));
        assertThat(kit.stockOf(wafer(45))).isZero();
        assertThat(kit.emptyBins()).containsExactly(new PinKit.Bin(wafer(45), 0));
        assertThat(kit.bins()).doesNotContain(new PinKit.Bin(wafer(45), 0)).hasSize(7);
        assertThat(kit.lengths(SegmentKind.WAFER)).containsExactly(30, 60);
        assertThat(kit.copy().emptyBins()).containsExactly(new PinKit.Bin(wafer(45), 0));
    }

    @Test
    void theBinsUnderParComeBackInBinCodeOrderEmptyOnesIncluded() {
        PinKit kit = aKit();
        kit.draw(List.of(wafer(45), wafer(45), wafer(45), wafer(45), wafer(30), wafer(30), bottom(165)));
        assertThat(kit.belowPar(3)).containsExactly(new PinKit.Bin(wafer(30), 2), new PinKit.Bin(wafer(45), 0));
        assertThat(kit.belowPar(4)).extracting(bin -> bin.segment().binCode()).containsExactly("B165", "W30", "W45");
        assertThat(kit.belowPar(1)).containsExactly(new PinKit.Bin(wafer(45), 0));
    }

    @Test
    void theReorderFillsEveryBinUnderParAndLeavesTheRest() {
        PinKit kit = aKit();
        kit.draw(List.of(wafer(45), wafer(45), wafer(45), wafer(45), wafer(30), bottom(165), bottom(165), bottom(165)));
        Map<PinSegment, Integer> order = kit.reorder(2, 10);
        assertThat(order).containsExactly(Map.entry(bottom(165), 9), Map.entry(wafer(45), 10));
        order.forEach(kit::add);
        assertThat(kit.belowPar(2)).isEmpty();
        assertThat(kit.stockOf(wafer(30))).isEqualTo(3);
    }

    @Test
    void aParOrFillLevelThatMakesNoSenseIsRefused() {
        assertThatThrownBy(() -> aKit().belowPar(0)).isInstanceOf(PinningError.class);
        assertThatThrownBy(() -> aKit().reorder(5, 4)).isInstanceOf(PinningError.class);
        assertThat(aKit().reorder(2, 2)).isEmpty();
    }

    @Test
    void aStandardKitStocksEveryKindItWasGiven() {
        PinKit kit = aKit();
        assertThat(kit.lengths(SegmentKind.BOTTOM)).containsExactly(150, 165, 180);
        assertThat(kit.lengths(SegmentKind.DRIVER)).containsExactly(175, 190);
        assertThat(kit.emptyBins()).isEmpty();
    }
}
