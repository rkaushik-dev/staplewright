package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TranslationTest {

    /** The six pin chart reissued one depth deeper at the root, so every number moves along one. */
    private static DepthAndSpaceChart reissued() {
        return Charts.sixPinChart(chart -> chart.rootMils(350).depths(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
    }

    @Test
    void aChartReadsAsItself() {
        Translation same = new Translation(Charts.sixPinChart(), Charts.sixPinChart());
        assertThat(same.linesUp()).isTrue();
        assertThat(same.translate(Bitting.parse("342156"))).isEqualTo(Bitting.parse("342156"));
        assertThat(same.unreadableDepths()).isEmpty();
    }

    @Test
    void aReissuedRootMovesEveryDepthNumberAlongOne() {
        Translation translation = new Translation(Charts.sixPinChart(), reissued());
        assertThat(translation.translate(Bitting.parse("342156"))).isEqualTo(Bitting.parse("453267"));
        assertThat(translation.depthTable()).containsEntry(0, Optional.of(1)).containsEntry(9, Optional.of(10));
        assertThat(translation.carriesOver(Bitting.parse("342156"))).isTrue();
    }

    @Test
    void aCutThatLandsBetweenDepthsHasNoReading() {
        DepthAndSpaceChart twenty =
                Charts.sixPinChart(chart -> chart.incrementMils(20).depths(List.of(0, 1, 2, 3, 4, 5, 6)));
        Translation translation = new Translation(Charts.sixPinChart(), twenty);
        assertThat(translation.depthTable())
                .containsEntry(0, Optional.of(0))
                .containsEntry(1, Optional.empty())
                .containsEntry(4, Optional.of(3));
        assertThat(translation.unreadableDepths()).contains(1, 2);
        assertThat(translation.carriesOver(Bitting.parse("040404"))).isTrue();
        assertThat(translation.carriesOver(Bitting.parse("140404"))).isFalse();
        assertThatThrownBy(() -> translation.translate(Bitting.parse("041404")))
                .isInstanceOf(BittingError.class)
                .satisfies(refused -> assertThat(((BittingError) refused).facts())
                        .containsEntry("chamber", 3)
                        .containsEntry("depth", 1));
    }

    @Test
    void chambersThatDoNotLineUpRefuseEveryKey() {
        DepthAndSpaceChart wider = Charts.sixPinChart(chart -> chart.spaceIncrementMils(160));
        Translation translation = new Translation(Charts.sixPinChart(), wider);
        assertThat(translation.spacingProblem()).contains("chamber 3 sits 8 mils apart on the two charts");
        assertThat(translation.carriesOver(Bitting.parse("342156"))).isFalse();
        assertThatThrownBy(() -> translation.translate(Bitting.parse("342156")))
                .isInstanceOf(BittingError.class)
                .hasMessageContaining("do not sit under the chambers");
    }

    @Test
    void aSmallSpacingDriftInsideTheAllowanceStillLinesUp() {
        DepthAndSpaceChart near = Charts.sixPinChart(chart -> chart.firstSpaceMils(234));
        assertThat(new Translation(Charts.sixPinChart(), near).linesUp()).isTrue();
    }

    @Test
    void theGaugingEndAndTheChamberCountHaveToAgree() {
        assertThat(new Translation(Charts.sixPinChart(), Charts.sixPinChart(chart -> chart.gaugedFromTip(true)))
                        .spacingProblem())
                .contains("one chart gauges from the tip and the other from the shoulder");
        assertThat(new Translation(Charts.sixPinChart(), Charts.sevenPinCoreChart()).spacingProblem())
                .contains("the charts have 6 and 7 chambers");
    }

    @Test
    void aKeyTheFirstChartCannotCutIsRefusedFirst() {
        Translation translation = new Translation(Charts.sixPinChart(), reissued());
        assertThat(translation.carriesOver(Bitting.parse("34215"))).isFalse();
        assertThatThrownBy(() -> translation.translate(Bitting.parse("34215"))).isInstanceOf(BittingError.class);
    }

    @Test
    void aReadingMidwayBetweenTwoDepthsIsNoReading() {
        DepthAndSpaceChart loose =
                Charts.sixPinChart(chart -> chart.incrementMils(16).depthToleranceMils(8));
        assertThat(loose.nearestDepth(327)).isEmpty();
        assertThat(loose.nearestDepth(328)).contains(0);
        assertThatThrownBy(() -> loose.depthForDimension(327)).isInstanceOf(BittingError.class);
        assertThat(Map.of("midway", loose.nearestDepth(311))).containsEntry("midway", Optional.empty());
    }
}
