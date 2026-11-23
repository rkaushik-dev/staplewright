package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.campus.Lockout;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.util.Records;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    private static final LocalDateTime CALLED = LocalDateTime.of(2025, 4, 8, 14, 12);

    private static DepositBook aBook() {
        DepositBook book = new DepositBook();
        book.charge("41207", "kmaki", 2500, TODAY);
        book.charge("41209", "rvirta", 5000, TODAY);
        return book;
    }

    private static LockoutLog aLog() {
        LockoutLog log = new LockoutLog();
        log.log(new Lockout("L-001", "MOO-214", "kmaki", CALLED));
        log.log(new Lockout("L-002", "MOO-214", "dsalo", CALLED));
        log.log(new Lockout("L-003", "MOO-216", "kmaki", CALLED));
        return log;
    }

    @Test
    void theCampusCountsItselfOnce() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger());
        assertThat(found.buildings()).isEqualTo(3);
        assertThat(found.openings()).isEqualTo(11);
        assertThat(found.keyedOpenings()).isEqualTo(10);
        assertThat(found.cylindersInstalled()).isEqualTo(6);
        assertThat(found.cylindersShelved()).isEqualTo(1);
    }

    @Test
    void theKeysOutAndGoneAreCounted() {
        KeyLedger ledger = aLedger();
        ledger.reportLost("41207", TODAY);
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), ledger);
        assertThat(found.keysOut()).isEqualTo(4);
        assertThat(found.keysGone()).isEqualTo(1);
    }

    @Test
    void theDepositsComeOffTheBookWhenOneIsGiven() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger(), aBook());
        assertThat(found.depositsHeldCents()).isEqualTo(7500);
    }

    @Test
    void withoutABookTheDepositsComeOffTheLedger() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger());
        assertThat(found.depositsHeldCents()).isEqualTo(0);
    }

    @Test
    void theUncoveredOpeningsFallOutOfTheCounts() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger());
        assertThat(found.uncovered()).isEqualTo(4);
    }

    @Test
    void theSummaryReadsAsLines() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger());
        List<String> lines = StatisticsPages.summary(found, "chester park, 2025");
        assertThat(lines.get(0)).isEqualTo("chester park, 2025");
        assertThat(lines).anyMatch(line -> line.contains("buildings"));
        assertThat(lines).anyMatch(line -> line.contains("nothing in them"));
    }

    @Test
    void aCampusWithNothingUncoveredSaysNothingAboutIt() {
        CampusNumbers found = StatisticsPages.campusNumbers(aCampus(), aCoverage(), aLedger());
        CampusNumbers tidy = Records.replace(found, Map.of("cylindersInstalled", found.keyedOpenings()));
        assertThat(StatisticsPages.summary(tidy)).noneMatch(line -> line.contains("nothing in them"));
    }

    @Test
    void aKeyReachingTwoBuildingsCountsInBoth() {
        Map<String, Integer> counts = StatisticsPages.keysByBuilding(aCoverage(), aLedger());
        assertThat(counts.get("MOO")).isGreaterThanOrEqualTo(counts.get("HEI"));
        assertThat(counts.get("HEI")).isEqualTo(1);
    }

    @Test
    void keysAreCountedByDepartment() {
        Map<String, Integer> counts = StatisticsPages.keysByDepartment(aLedger());
        assertThat(counts.get("facilities")).isEqualTo(2);
        assertThat(counts.get("geography")).isEqualTo(3);
    }

    @Test
    void aReturnedKeyIsNotCountedAnywhere() {
        KeyLedger ledger = aLedger();
        ledger.takeBack("41211", TODAY);
        Map<String, Integer> counts = StatisticsPages.keysByBuilding(aCoverage(), ledger);
        assertThat(counts).doesNotContainKey("HEI");
    }

    @Test
    void incidentsAreCountedByYear() {
        Map<Integer, Integer> found = StatisticsPages.incidentsByYear(
                List.of(LocalDate.of(2024, 5, 1), LocalDate.of(2025, 1, 4), LocalDate.of(2025, 9, 9)));
        assertThat(found).isEqualTo(Map.of(2024, 1, 2025, 2));
    }

    @Test
    void noIncidentsCountAsNothing() {
        assertThat(StatisticsPages.incidentsByYear(List.of())).isEmpty();
    }

    @Test
    void theLockoutPressureNamesTheRoom() {
        List<Tally> found = StatisticsPages.lockoutPressure(aLog(), aCampus());
        assertThat(found.get(0)).isEqualTo(new Tally("MOO 214", 2));
    }

    @Test
    void theTermsLockoutPressureLeavesOutTheCallsBeforeIt() {
        LockoutLog log = aLog();
        log.log(new Lockout("L-010", "ZZZ-1", "kmaki", LocalDateTime.of(2025, 9, 4, 10, 0)));
        assertThat(StatisticsPages.lockoutPressure(log, aCampus(), LocalDate.of(2025, 9, 1)))
                .containsExactly(new Tally("ZZZ-1", 1));
        assertThat(StatisticsPages.lockoutPressure(log, aCampus(), LocalDate.of(2025, 1, 1)))
                .isEqualTo(StatisticsPages.lockoutPressure(log, aCampus()));
    }

    @Test
    void aDoorNobodyHasARecordOfKeepsItsNumber() {
        LockoutLog log = new LockoutLog();
        log.log(new Lockout("L-009", "ZZZ-1", "kmaki", CALLED));
        assertThat(StatisticsPages.lockoutPressure(log, aCampus()).get(0).name())
                .isEqualTo("ZZZ-1");
    }

    @Test
    void theWidestKeyOutComesFirst() {
        List<Tally> found = StatisticsPages.exposure(aCoverage(), aLedger());
        assertThat(found.get(0).name()).isEqualTo("GGM");
        assertThat(found.get(0).count()).isEqualTo(6);
    }

    @Test
    void aSymbolHeldTwiceIsOnlyCountedOnce() {
        List<Tally> found = StatisticsPages.exposure(aCoverage(), aLedger());
        List<String> symbols = found.stream().map(Tally::name).toList();
        assertThat(symbols).hasSameSizeAs(new HashSet<>(symbols));
    }

    @Test
    void theExposureListCanBeCutShort() {
        assertThat(StatisticsPages.exposure(aCoverage(), aLedger(), 2)).hasSize(2);
    }
}
