package io.github.rkaushikdev.staplewright.rekey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.support.Systems;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuarantineTest {

    private static final LocalDate RETIRED_ON = LocalDate.of(2018, 6, 14);
    private static final LocalDate TODAY = LocalDate.of(2025, 4, 8);

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static Quarantine aQuarantine() {
        Quarantine hold = new Quarantine();
        hold.retire(b("342156"), RetirementReason.COMPROMISED, RETIRED_ON, "AA1");
        hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON, "AA2");
        hold.retire(b("382556"), RetirementReason.SUPERSEDED, LocalDate.of(2024, 1, 5), "AB1");
        hold.retire(b("302156"), RetirementReason.DAMAGED, RETIRED_ON);
        return hold;
    }

    private static List<String> cuts(Iterable<RetiredBitting> entries) {
        List<String> found = new ArrayList<>();
        entries.forEach(entry -> found.add(entry.bitting().render()));
        return found;
    }

    @Test
    void aRetiredCutIsRefused() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("342156"))).isTrue();
        assertThat(hold.contains(b("342156"))).isTrue();
        assertThat(hold.isRetired(b("999999"))).isFalse();
    }

    @Test
    void aCutNobodyRetiredIsNotInTheQuarantine() {
        assertThat(aQuarantine().contains(b("999999"))).isFalse();
        assertThat(aQuarantine().contains("342156")).isFalse();
    }

    @Test
    void aCompromisedCutNeverComesBack() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("342156"), LocalDate.of(2099, 1, 1))).isTrue();
        assertThatThrownBy(() -> hold.release(b("342156"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSupersededCutComesBackAfterTheWindow() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2024, 6, 13))).isTrue();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2025, 6, 14))).isFalse();
    }

    @Test
    void theDayBeforeTheWindowClosesIsStillRetired() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2025, 6, 13))).isTrue();
    }

    @Test
    void aShopWithAShorterWindowReleasesSooner() {
        Quarantine hold = new Quarantine(3);
        hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON);
        assertThat(hold.isRetired(b("362356"), TODAY)).isFalse();
    }

    @Test
    void aWindowOfNothingIsRefused() {
        assertThatThrownBy(() -> new Quarantine(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void askingWithoutADateTreatsEverythingAsRetired() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"))).isTrue();
    }

    @Test
    void aSupersededCutCanBeReleasedByHand() {
        Quarantine hold = aQuarantine();
        RetiredBitting released = hold.release(b("362356"));
        assertThat(released.symbol()).isEqualTo("AA2");
        assertThat(hold.contains(b("362356"))).isFalse();
    }

    @Test
    void releasingSomethingThatWasNeverRetired() {
        assertThatThrownBy(() -> aQuarantine().release(b("999999"))).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCutCannotBeRetiredTwiceForTheSameKindOfReason() {
        Quarantine hold = aQuarantine();
        assertThatThrownBy(() -> hold.retire(b("362356"), RetirementReason.SUPERSEDED, TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCompromiseOverwritesASofterRetirement() {
        Quarantine hold = aQuarantine();
        hold.retire(b("362356"), RetirementReason.COMPROMISED, TODAY, "", "turned up on ebay");
        assertThat(hold.entry(b("362356")).isPermanent()).isTrue();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void aCompromisedCutCannotBeSoftened() {
        Quarantine hold = aQuarantine();
        assertThatThrownBy(() -> hold.retire(b("342156"), RetirementReason.SUPERSEDED, TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutACutThatIsNotRetired() {
        assertThatThrownBy(() -> aQuarantine().entry(b("999999"))).isInstanceOf(NotFoundError.class);
    }

    @Test
    void filteringAnArrayDropsTheRetiredCuts() {
        Quarantine hold = aQuarantine();
        List<Bitting> candidates = List.of(b("342156"), b("362356"), b("442156"));
        assertThat(hold.available(candidates)).containsExactly(b("442156"));
    }

    @Test
    void filteringWithADateLetsTheOldOnesThrough() {
        Quarantine hold = aQuarantine();
        List<Bitting> candidates = List.of(b("342156"), b("362356"), b("442156"));
        assertThat(hold.available(candidates, LocalDate.of(2026, 1, 1))).containsExactly(b("362356"), b("442156"));
    }

    @Test
    void thePermanentListIsOnlyTheCompromisedOnes() {
        assertThat(cuts(aQuarantine().permanent())).containsExactly("342156");
    }

    @Test
    void theReleasableListIsWhatHasSatOutItsWindow() {
        assertThat(cuts(aQuarantine().releasable(LocalDate.of(2026, 1, 1)))).containsExactly("302156", "362356");
    }

    @Test
    void aRecentRetirementIsNotReleasable() {
        assertThat(aQuarantine().releasable(TODAY)).isEmpty();
    }

    @Test
    void theReasonsAreCounted() {
        Map<String, Integer> counts = aQuarantine().byReason();
        assertThat(counts).isEqualTo(Map.of("compromised", 1, "damaged", 1, "superseded", 2));
    }

    @Test
    void theQuarantineCountsAndIteratesInCutOrder() {
        Quarantine hold = aQuarantine();
        assertThat(hold.size()).isEqualTo(4);
        assertThat(cuts(hold)).containsExactly("302156", "342156", "362356", "382556");
    }

    @Test
    void aRekeyThatKeptTheMasterBreakLeavesTheMissingKeyWorking() {
        List<Bitting> rebuilt = List.of(b("322156"), b("342356"));
        List<RetiredBitting> found = aQuarantine().stillTurning(rebuilt, TODAY);
        assertThat(cuts(found)).containsExactly("342156");
        assertThat(found.get(0).reason()).isSameAs(RetirementReason.COMPROMISED);
        assertThat(found.get(0).symbol()).isEqualTo("AA1");
        assertThat(aQuarantine().isSafeToInstall(rebuilt, TODAY)).isFalse();
    }

    @Test
    void aRekeyThatMovedTheSharedChamberIsSafe() {
        List<Bitting> rebuilt = List.of(b("322156"), b("352356"));
        assertThat(aQuarantine().stillTurning(rebuilt, TODAY)).isEmpty();
        assertThat(aQuarantine().isSafeToInstall(rebuilt, TODAY)).isTrue();
    }

    @Test
    void aSupersededCutCountsUntilItHasSatOutItsWindow() {
        List<Bitting> reissued = List.of(b("322156"), b("362356"));
        assertThat(cuts(aQuarantine().stillTurning(reissued, TODAY))).containsExactly("362356");
        assertThat(aQuarantine().stillTurning(reissued, LocalDate.of(2026, 7, 1)))
                .isEmpty();
    }

    @Test
    void theCutsComeBackByDayRetiredThenByCut() {
        List<Bitting> wide = List.of(b("302156"), b("342156"), b("382556"), b("362356"));
        assertThat(cuts(aQuarantine().stillTurning(wide, TODAY)))
                .containsExactly("302156", "342156", "362356", "382556");
    }

    @Test
    void aCylinderOfAnotherLengthIsNeverTurnedAndNoKeysIsRefused() {
        assertThat(aQuarantine().stillTurning(List.of(b("34215")), TODAY)).isEmpty();
        assertThatThrownBy(() -> aQuarantine().stillTurning(List.of(), TODAY)).isInstanceOf(InputError.class);
    }

    @Test
    void aLeapDayRetirementWaitsForTheFirstOfMarch() {
        RetiredBitting leap = new RetiredBitting(b("342156"), RetirementReason.SUPERSEDED, LocalDate.of(2016, 2, 29));
        assertThat(leap.releasedBy(LocalDate.of(2023, 2, 28), 7)).isFalse();
        assertThat(leap.releasedBy(LocalDate.of(2023, 3, 1), 7)).isTrue();
        assertThat(leap.releasedBy(LocalDate.of(2015, 3, 1), 7)).isFalse();
    }

    /** The small campus schedule with the master and two change keys cut for one group. */
    private static KeyingSchedule aRekeyedSchedule(String changeKey) {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(KeySymbol.parse("AA"), b("322156"));
        schedule.assign(KeySymbol.parse("AA1"), b(changeKey));
        return schedule;
    }

    private static KeyingGroup office() {
        return new KeyingGroup("office 214", List.of(KeySymbol.parse("AA"), KeySymbol.parse("AA1")));
    }

    @Test
    void aGroupACompromisedCutStillTurnsStopsTheJob() {
        List<Finding> found = aQuarantine().stillTurningGroups(aRekeyedSchedule("342356"), List.of(office()), TODAY);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).code()).isEqualTo("retired_cut_turns");
        assertThat(found.get(0).severity()).isSameAs(io.github.rkaushikdev.staplewright.progression.Severity.BLOCKING);
        assertThat(found.get(0).message())
                .isEqualTo("group office 214 still turns for 342156, retired compromised on 2018-06-14");
    }

    @Test
    void aSupersededCutStillTurningIsSeriousAndARekeyedGroupIsQuiet() {
        List<Finding> found = aQuarantine().stillTurningGroups(aRekeyedSchedule("362356"), List.of(office()), TODAY);
        assertThat(found)
                .extracting(Finding::severity)
                .containsExactly(io.github.rkaushikdev.staplewright.progression.Severity.SERIOUS);
        assertThat(aQuarantine().stillTurningGroups(aRekeyedSchedule("352356"), List.of(office()), TODAY))
                .isEmpty();
    }

    @Test
    void aGroupWithNothingCutYetHasNothingToSay() {
        assertThat(aQuarantine().stillTurningGroups(Systems.smallSchedule(), List.of(office()), TODAY))
                .isEmpty();
        assertThat(aQuarantine().available(List.of(b("342156"), b("352356")), TODAY))
                .containsExactly(b("352356"));
    }
}
