package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IncidentalTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    @Test
    void oneKeyAloneBreaksOnceAChamber() {
        Map<Integer, List<Integer>> breaks = Incidentals.breakDepths(List.of(b("342156")));
        assertThat(breaks.values()).allSatisfy(depths -> assertThat(depths).hasSize(1));
        assertThat(Incidentals.operatingCount(List.of(b("342156")))).isEqualTo(1);
        assertThat(Incidentals.isClean(List.of(b("342156")))).isTrue();
    }

    @Test
    void aMasterAndOneChangeKeySplitWhereTheyDiffer() {
        Map<Integer, List<Integer>> breaks = Incidentals.breakDepths(List.of(b("342156"), b("362156")));
        assertThat(breaks.get(2)).containsExactly(4, 6);
        assertThat(breaks.get(1)).containsExactly(3);
    }

    @Test
    void aSingleSplitMakesNoIncidentalKeys() {
        List<Bitting> keys = List.of(b("342156"), b("362156"));
        assertThat(Incidentals.operatingCount(keys)).isEqualTo(2);
        assertThat(Incidentals.incidentalCount(keys)).isZero();
        assertThat(Incidentals.isClean(keys)).isTrue();
    }

    @Test
    void twoSplitsGiveAwayTwoKeysNobodyDrew() {
        List<Bitting> keys = List.of(b("342156"), b("362356"));
        assertThat(Incidentals.operatingCount(keys)).isEqualTo(4);
        assertThat(Incidentals.incidentalCount(keys)).isEqualTo(2);
        Set<String> found =
                Incidentals.incidentalKeys(keys).stream().map(Bitting::render).collect(Collectors.toSet());
        assertThat(found).isEqualTo(Set.of("342356", "362156"));
    }

    @Test
    void theIntendedKeysAreNeverInTheIncidentalList() {
        List<Bitting> keys = List.of(b("342156"), b("362356"), b("382556"));
        Set<Bitting> intended = new HashSet<>(keys);
        for (Bitting key : Incidentals.incidentalKeys(keys)) {
            assertThat(intended).doesNotContain(key);
        }
    }

    @Test
    void threeKeysOverTwoChambersHoldNineCombinations() {
        List<Bitting> keys = List.of(b("342156"), b("362356"), b("382556"));
        assertThat(Incidentals.operatingCount(keys)).isEqualTo(9);
        assertThat(Incidentals.incidentalCount(keys)).isEqualTo(6);
    }

    @Test
    void everyOperatingKeyIsProducedOnce() {
        List<Bitting> keys = List.of(b("342156"), b("362356"));
        List<String> produced =
                Incidentals.operatingKeys(keys).map(Bitting::render).toList();
        assertThat(produced).hasSize(4);
        assertThat(new HashSet<>(produced)).hasSize(4);
    }

    @Test
    void theWorstChamberIsTheOneCarryingTheMostBreaks() {
        List<Bitting> keys = List.of(b("342156"), b("362156"), b("382156"), b("342356"));
        assertThat(Incidentals.worstChamber(keys)).isEqualTo(2);
    }

    @Test
    void theShallowestChamberWinsATie() {
        List<Bitting> keys = List.of(b("342156"), b("362356"));
        assertThat(Incidentals.worstChamber(keys)).isEqualTo(2);
    }

    @Test
    void aCylinderWithNoKeysIsRefused() {
        assertThatThrownBy(() -> Incidentals.breakDepths(List.of())).isInstanceOf(InputError.class);
    }

    @Test
    void keysOfDifferentLengthsAreRefused() {
        assertThatThrownBy(() -> Incidentals.operatingCount(List.of(b("342156"), b("3421"))))
                .isInstanceOf(InputError.class);
    }

    @Test
    void anIncidentalKeyThatBreaksMacsCannotBeCut() {
        List<Bitting> keys = List.of(b("342156"), b("392856"));
        assertThat(Incidentals.incidentalCount(keys)).isEqualTo(2);
        List<Bitting> cuttable = Incidentals.cuttableIncidentals(Charts.sixPinChart(chart -> chart.macs(3)), keys);
        assertThat(cuttable.size()).isLessThan(Incidentals.incidentalKeys(keys).size());
    }

    @Test
    void aLooseChartLeavesEveryIncidentalKeyCuttable() {
        List<Bitting> keys = List.of(b("342156"), b("362356"));
        List<Bitting> cuttable = Incidentals.cuttableIncidentals(Charts.sixPinChart(chart -> chart.macs(9)), keys);
        assertThat(cuttable).hasSize(Incidentals.incidentalCount(keys));
    }

    @Test
    void addingAKeyToACylinderCostsWhatItCosts() {
        List<Bitting> intended = List.of(b("342156"), b("362156"));
        assertThat(Incidentals.crossKeyingCost(intended, b("362356"))).isEqualTo(1);
    }

    @Test
    void aCrossKeyThatLinesUpCostsNothing() {
        List<Bitting> intended = List.of(b("342156"), b("362156"));
        assertThat(Incidentals.crossKeyingCost(intended, b("382156"))).isZero();
    }

    @Test
    void crossKeyingAnUnrelatedKeyIsExpensive() {
        List<Bitting> intended = List.of(b("342156"), b("362156"));
        assertThat(Incidentals.crossKeyingCost(intended, b("563748"))).isGreaterThan(20);
    }

    @Test
    void theMasterOverACorridorOpensEveryDoorInIt() {
        Bitting master = b("442446");
        List<List<Bitting>> corridor =
                List.of(List.of(master, b("462446")), List.of(master, b("482446")), List.of(master, b("402446")));
        assertThat(Incidentals.commonKeys(corridor)).containsExactly(master);
        assertThat(Incidentals.unintendedCommonKeys(corridor, List.of(master))).isEmpty();
    }

    @Test
    void doorsSplitInDifferentChambersShareAKeyNobodyDrew() {
        Bitting master = b("442446");
        List<List<Bitting>> corridor = List.of(List.of(master, b("462466")), List.of(master, b("462486")));
        assertThat(Incidentals.commonKeys(corridor)).containsExactly(master, b("462446"));
        assertThat(Incidentals.unintendedCommonKeys(corridor, List.of(master))).containsExactly(b("462446"));
    }

    @Test
    void doorsWithNothingInCommonInOneChamberShareNoKey() {
        List<List<Bitting>> pair = List.of(List.of(b("442446")), List.of(b("642446")));
        assertThat(Incidentals.commonKeys(pair)).isEmpty();
    }

    @Test
    void oneDoorAloneSharesEveryKeyThatTurnsIt() {
        List<Bitting> keys = List.of(b("442446"), b("462466"));
        assertThat(Incidentals.commonKeys(List.of(keys)))
                .containsExactlyElementsOf(Incidentals.operatingKeys(keys).toList());
    }

    @Test
    void aRunOfNoDoorsOrOfDoorsOfTwoLengthsIsRefused() {
        assertThatThrownBy(() -> Incidentals.commonKeys(List.of())).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> Incidentals.commonKeys(List.of(List.of(b("442446")), List.of(b("44244")))))
                .isInstanceOf(InputError.class);
    }

    @Test
    void everyCombinationTurnsTheLastChamberFastest() {
        assertThat(Bitting.everyCombination(List.of(List.of(1, 3), List.of(2), List.of(5, 7))))
                .containsExactly(b("125"), b("127"), b("325"), b("327"));
        assertThatThrownBy(() -> Bitting.everyCombination(List.of(List.of(1), List.of())))
                .isInstanceOf(InputError.class);
        assertThatThrownBy(() -> Bitting.everyCombination(List.of())).isInstanceOf(InputError.class);
    }
}
