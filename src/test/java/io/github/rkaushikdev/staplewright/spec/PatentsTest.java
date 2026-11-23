package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.KeywayError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PatentsTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);

    private static KeywaySection dated(String code, String expires) {
        return new KeywaySection(code, "Schlage", true, Optional.of(expires));
    }

    private static KeywayFamily aFamily() {
        KeywayFamily family = new KeywayFamily("everest 29", dated("C", "2031-04-01"));
        family.add(dated("CE", "2027-02-14"), "C");
        family.add(dated("CF", "2023-11-30"), "C");
        family.add(new KeywaySection("CEA", "Schlage"), "CE");
        family.add(new KeywaySection("CEB", "Schlage", true), "CE");
        return family;
    }

    private static Exposure exposure(String section, boolean restricted, LocalDate expiresOn) {
        return new Exposure(section, restricted, Optional.of(expiresOn));
    }

    private static Exposure undated(String section, boolean restricted) {
        return new Exposure(section, restricted, Optional.empty());
    }

    @Test
    void aSectionWithNoDateHasNoExpiry() {
        assertThat(Patents.expiryOf(new KeywaySection("CEA", "Schlage"))).isEmpty();
    }

    @Test
    void aDateTheCataloguePrintedComesBackAsADay() {
        KeywaySection section = dated("CE", "2027-02-14");
        assertThat(Patents.expiryOf(section)).contains(LocalDate.of(2027, 2, 14));
    }

    @Test
    void aUtilityPatentRunsTwentyYearsFromFiling() {
        assertThat(Patents.expiryOf(dated("CE", "filed 2009-04-02"))).contains(LocalDate.of(2029, 4, 2));
        assertThat(Patents.expiryOf(dated("CE", "filed 2008-02-29"))).contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void aDesignPatentRunsFifteenYearsFromGrantOnceTheLongerTermBegan() {
        assertThat(Patents.expiryOf(dated("CE", "design granted 2016-03-01"))).contains(LocalDate.of(2031, 3, 1));
        assertThat(Patents.expiryOf(dated("CE", "design granted 2015-05-13"))).contains(LocalDate.of(2030, 5, 13));
        assertThat(Patents.expiryOf(dated("CE", "design granted 2016-02-29"))).contains(LocalDate.of(2031, 2, 28));
    }

    @Test
    void aDesignPatentGrantedBeforeTheChangeRunsFourteen() {
        assertThat(Patents.expiryOf(dated("CE", "design granted 2015-05-12"))).contains(LocalDate.of(2029, 5, 12));
    }

    @Test
    void aFilingDateDecidesWhetherTheSectionIsStillRestricted() {
        Exposure item = Patents.exposureOf(new KeywaySection("CE", "Schlage", true, Optional.of("filed 2004-06-30")));
        assertThat(item.isOpen(LocalDate.of(2024, 6, 29))).isFalse();
        assertThat(item.isOpen(LocalDate.of(2024, 6, 30))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"filed 2009-02-30", "granted 2016-03-01", "design 2016-03-01", "filed  2009-04-02", "0000-01-01"
            })
    void aPatentNobodyCanReadIsAnError(String written) {
        assertThatThrownBy(() -> Patents.expiryOf(dated("CE", written))).isInstanceOf(KeywayError.class);
    }

    @Test
    void aDateNobodyCanReadIsAnError() {
        KeywaySection section = dated("CE", "february");
        KeywayError caught = catchThrowableOfType(KeywayError.class, () -> Patents.expiryOf(section));
        assertThat(caught.facts()).containsEntry("section", "CE");
    }

    @Test
    void anOpenKeywayIsOpenWhateverTheDate() {
        assertThat(undated("CEA", false).isOpen(TODAY)).isTrue();
    }

    @Test
    void aContractOnlyRestrictionNeverGoesOpenOnItsOwn() {
        assertThat(undated("CEB", true).isOpen(TODAY)).isFalse();
    }

    @Test
    void aPatentThatHasRunLeavesTheSectionOpen() {
        assertThat(exposure("CF", true, LocalDate.of(2023, 11, 30)).isOpen(TODAY))
                .isTrue();
    }

    @Test
    void aPatentExpiringTodayHasAlreadyGone() {
        assertThat(exposure("CF", true, TODAY).isOpen(TODAY)).isTrue();
    }

    @Test
    void theDaysLeftCountDownToTheDate() {
        assertThat(exposure("CE", true, LocalDate.of(2025, 7, 10)).daysLeft(TODAY))
                .contains(10);
    }

    @Test
    void aSectionWithNoDateCountsDownToNothing() {
        assertThat(undated("CEB", true).daysLeft(TODAY)).isEmpty();
    }

    @Test
    void aSectionGoingInsideTheHorizonIsClosing() {
        assertThat(exposure("CE", true, LocalDate.of(2027, 2, 14)).isClosing(TODAY))
                .isTrue();
    }

    @Test
    void aSectionOutsideTheHorizonIsNotClosingYet() {
        assertThat(exposure("C", true, LocalDate.of(2031, 4, 1)).isClosing(TODAY))
                .isFalse();
    }

    @Test
    void aSectionAlreadyOpenIsNotClosingItIsGone() {
        assertThat(exposure("CF", true, LocalDate.of(2023, 11, 30)).isClosing(TODAY))
                .isFalse();
    }

    @Test
    void theHorizonCanBeStretched() {
        assertThat(exposure("C", true, LocalDate.of(2031, 4, 1)).isClosing(TODAY, 10))
                .isTrue();
    }

    @Test
    void aHorizonThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> exposure("C", true, LocalDate.of(2031, 4, 1)).isClosing(TODAY, -1))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aHorizonCrossingALeapDayStillLands() {
        Exposure leap = exposure("C", true, LocalDate.of(2029, 2, 28));
        assertThat(leap.isClosing(LocalDate.of(2024, 2, 29), 5)).isTrue();
    }

    @Test
    void theExposuresComeBackSoonestFirst() {
        List<String> found =
                Patents.exposures(aFamily()).stream().map(Exposure::section).toList();
        assertThat(found.subList(0, 3)).containsExactly("CF", "CE", "C");
    }

    @Test
    void sectionsWithNoDateSortToTheBack() {
        List<String> found =
                Patents.exposures(aFamily()).stream().map(Exposure::section).toList();
        assertThat(found.subList(3, found.size())).containsExactlyInAnyOrder("CEA", "CEB");
    }

    @Test
    void theOpenSectionsAreTheExpiredAndTheUnrestricted() {
        assertThat(Patents.openSections(aFamily(), TODAY)).containsExactly("CF", "CEA");
    }

    @Test
    void theClosingSectionsAreTheOnesInsideTheHorizon() {
        assertThat(Patents.closingSections(aFamily(), TODAY)).containsExactly("CE");
    }

    @Test
    void aWiderHorizonPullsInMore() {
        assertThat(Patents.closingSections(aFamily(), TODAY, 10)).containsExactly("CE", "C");
    }

    @Test
    void theNextExpiryIsTheSoonestOneStillAhead() {
        Optional<Exposure> found = Patents.nextExpiry(aFamily(), TODAY);
        assertThat(found.orElseThrow().section()).isEqualTo("CE");
    }

    @Test
    void aFamilyWithNothingAheadOfItHasNoNext() {
        KeywayFamily family = new KeywayFamily("old", new KeywaySection("A", "Best", true, Optional.of("2001-01-01")));
        assertThat(Patents.nextExpiry(family, TODAY)).isEmpty();
    }

    @Test
    void whatIsStillRestrictedComesBackInTreeOrder() {
        assertThat(Patents.stillRestricted(aFamily(), TODAY)).containsExactly("C", "CE", "CEB");
    }

    @Test
    void aSectionWithYearsLeftIsSafeToKeyOn() {
        assertThat(Patents.sectionIsSafe(aFamily(), "C", TODAY)).isTrue();
    }

    @Test
    void aSectionGoingInsideTheHorizonIsNotSafe() {
        assertThat(Patents.sectionIsSafe(aFamily(), "CE", TODAY)).isFalse();
    }

    @Test
    void anExpiredSectionIsNotSafe() {
        assertThat(Patents.sectionIsSafe(aFamily(), "CF", TODAY)).isFalse();
    }

    @Test
    void anUnrestrictedSectionIsNeverSafe() {
        assertThat(Patents.sectionIsSafe(aFamily(), "CEA", TODAY)).isFalse();
    }

    @Test
    void aContractOnlySectionIsSafeBecauseItHasNoClock() {
        assertThat(Patents.sectionIsSafe(aFamily(), "CEB", TODAY)).isTrue();
    }

    @Test
    void keyingOnASafeSectionIsAllowedThrough() {
        Patents.refuseUnsafeSection(aFamily(), "C", TODAY);
    }

    @Test
    void keyingOnASectionThatIsGoingIsRefusedWithTheDate() {
        KeywayError caught =
                catchThrowableOfType(KeywayError.class, () -> Patents.refuseUnsafeSection(aFamily(), "CE", TODAY));
        assertThat(caught.facts()).containsEntry("expires", "2027-02-14");
    }

    @Test
    void keyingOnAnOpenSectionSaysItWasNeverRestricted() {
        KeywayError caught =
                catchThrowableOfType(KeywayError.class, () -> Patents.refuseUnsafeSection(aFamily(), "CEA", TODAY));
        assertThat(caught.facts()).containsEntry("expires", "never restricted");
    }

    @Test
    void aRootSectionReachesTheOpenSectionsUnderIt() {
        assertThat(Patents.exposedBelow(aFamily(), "C", TODAY)).containsExactly("CF", "CEA");
    }

    @Test
    void aLeafThatIsStillRestrictedExposesNothing() {
        assertThat(Patents.exposedBelow(aFamily(), "CEB", TODAY)).isEmpty();
    }

    @Test
    void onlyTheSectionsInUseAreReportedAtRisk() {
        assertThat(Patents.systemsAtRisk(aFamily(), List.of("C", "CF", "CEB"), TODAY))
                .containsExactly("CF");
    }

    @Test
    void nothingInUseIsNothingAtRisk() {
        assertThat(Patents.systemsAtRisk(aFamily(), List.of(), TODAY)).isEmpty();
    }

    @Test
    void theWidestExposureGetsMovedFirst() {
        KeywayFamily family = aFamily();
        family.add(new KeywaySection("CFA", "Schlage", true, Optional.of("2022-01-01")), "CF");
        assertThat(Patents.migrationOrder(family, TODAY)).containsExactly("CF", "CFA");
    }

    @Test
    void aSectionThatWasNeverRestrictedIsNotAMigration() {
        assertThat(Patents.migrationOrder(aFamily(), TODAY)).doesNotContain("CEA");
    }

    @Test
    void theReportLeadsWithWhatIsAlreadyGone() {
        List<String> lines = Patents.patentReport(aFamily(), TODAY);
        assertThat(lines.get(0)).isEqualTo("everest 29 patent position at 2025-06-30");
        assertThat(lines.get(1)).isEqualTo("1 sections are open and were not meant to be");
    }

    @Test
    void aCleanFamilySaysSo() {
        KeywayFamily family =
                new KeywayFamily("clean", new KeywaySection("A", "Best", true, Optional.of("2044-01-01")));
        List<String> lines = Patents.patentReport(family, TODAY);
        assertThat(lines.get(1)).isEqualTo("every restricted section is still restricted");
    }

    @Test
    void theReportNamesTheNextOneToGo() {
        List<String> lines = Patents.patentReport(aFamily(), TODAY);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("next is CE restricted until 2027-02-14");
    }

    @Test
    void theWallPlannerDropsEverythingWithoutADate() {
        List<KeywaySection> sections =
                StreamSupport.stream(aFamily().spliterator(), false).toList();
        List<DatedSection> found = Patents.datesInOrder(sections);
        assertThat(found.stream().map(DatedSection::code)).containsExactly("CF", "CE", "C");
    }

    @Test
    void aDescriptionSaysWhatKindOfRestrictionItIs() {
        assertThat(Patents.exposureOf(aFamily().section("CEB")).describe(TODAY)).endsWith("by contract only");
        assertThat(Patents.exposureOf(aFamily().section("CEA")).describe(TODAY)).isEqualTo("CEA open keyway");
    }
}
