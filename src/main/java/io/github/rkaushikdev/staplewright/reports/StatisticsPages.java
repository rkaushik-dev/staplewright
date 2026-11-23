package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Tallies;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The numbers the shop takes to a budget meeting once a year.
 *
 * <p>None of these are hard. What makes them worth having in one place is that they are the numbers
 * somebody will ask for on a Tuesday afternoon with an hour's notice, and working them out from
 * scratch each time is how a figure ends up different in two documents in the same folder.
 *
 * <p>Every one of them is a plain count over objects that are already in memory. There is no clever
 * aggregation and no cache: a campus is a few thousand keys and a few hundred doors, and anything
 * that needs more than that has bigger problems than arithmetic.
 */
public final class StatisticsPages {

    /** How many keys the exposure list names unless asked for more or fewer. */
    public static final int EXPOSURE_LIMIT = 5;

    private StatisticsPages() {}

    /** Count the campus once, the deposits coming off the ledger. */
    public static CampusNumbers campusNumbers(OpeningSchedule openings, CoverageMap coverage, KeyLedger ledger) {
        return campusNumbers(openings, coverage, ledger, Optional.empty());
    }

    /** Count the campus once, the deposits coming off the book. */
    public static CampusNumbers campusNumbers(
            OpeningSchedule openings, CoverageMap coverage, KeyLedger ledger, DepositBook book) {
        return campusNumbers(openings, coverage, ledger, Optional.of(book));
    }

    /**
     * Count the campus once, so every report quotes the same figures. The deposits come off the book
     * when there is one with anything in it, and off the ledger otherwise.
     */
    public static CampusNumbers campusNumbers(
            OpeningSchedule openings, CoverageMap coverage, KeyLedger ledger, Optional<DepositBook> book) {
        HardwareRegister register = coverage.register();
        int installed = 0;
        for (Cylinder cylinder : register) {
            if (cylinder.isInstalled()) {
                installed++;
            }
        }
        long deposits = ControlPages.inUse(book).map(DepositBook::totalHeld).orElseGet(ledger::depositsHeld);
        return new CampusNumbers(
                openings.buildings().size(),
                openings.size(),
                openings.keyedOpenings().size(),
                installed,
                register.size() - installed,
                ledger.out().size(),
                ledger.gone().size(),
                deposits);
    }

    /**
     * How many keys out reach each building, biggest first.
     *
     * <p>A key that reaches four buildings is counted in all four, because the question this answers
     * is how exposed a building is rather than how many pieces of brass exist.
     */
    public static Map<String, Integer> keysByBuilding(CoverageMap coverage, KeyLedger ledger) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IssuedKey key : ledger.out()) {
            for (String building : coverage.buildingsFor(KeySymbol.parse(key.symbol()))) {
                counts.merge(building, 1, Integer::sum);
            }
        }
        return Tallies.biggestFirst(counts);
    }

    /** How many keys out each department holds, biggest first. */
    public static Map<String, Integer> keysByDepartment(KeyLedger ledger) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IssuedKey key : ledger.out()) {
            counts.merge(ledger.directory().person(key.keyholder()).department(), 1, Integer::sum);
        }
        return Tallies.biggestFirst(counts);
    }

    /** How many keys were reported missing in each year, oldest year first. */
    public static Map<Integer, Integer> incidentsByYear(Collection<LocalDate> reported) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (LocalDate when : reported) {
            counts.merge(when.getYear(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /** Doors generating calls, worst first, with the room for the meeting where the door is on record. */
    public static List<Tally> lockoutPressure(LockoutLog log, OpeningSchedule openings) {
        return pressure(log.countsByOpening(), openings);
    }

    /** Doors generating the calls made on or after a day, worst first, for the term's budget meeting. */
    public static List<Tally> lockoutPressure(LockoutLog log, OpeningSchedule openings, LocalDate since) {
        return pressure(log.countsByOpening(since), openings);
    }

    private static List<Tally> pressure(Map<String, Integer> counts, OpeningSchedule openings) {
        return counts.entrySet().stream()
                .map(entry -> new Tally(labelOf(openings, entry.getKey()), entry.getValue()))
                .toList();
    }

    /** The building and room of an opening, or its number when nobody has a record of it. */
    static String labelOf(OpeningSchedule openings, String number) {
        try {
            return openings.opening(number).label();
        } catch (NotFoundError unknown) {
            return number;
        }
    }

    /** The keys that are out and open the most doors, worst first. */
    public static List<Tally> exposure(CoverageMap coverage, KeyLedger ledger) {
        return exposure(coverage, ledger, EXPOSURE_LIMIT);
    }

    /** The {@code limit} symbols out that open the most doors, each symbol once, worst first. */
    public static List<Tally> exposure(CoverageMap coverage, KeyLedger ledger, int limit) {
        List<Tally> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IssuedKey key : ledger.out()) {
            if (seen.add(key.symbol())) {
                found.add(new Tally(key.symbol(), coverage.reachOf(KeySymbol.parse(key.symbol()))));
            }
        }
        return Tally.firstOf(found.stream().sorted(Tally.MOST_FIRST).toList(), limit);
    }

    /** The figures as lines, with no title. */
    public static List<String> summary(CampusNumbers numbers) {
        return summary(numbers, "");
    }

    /** The figures as lines under a title, and the keyed openings with nothing in them when there are any. */
    public static List<String> summary(CampusNumbers numbers, String title) {
        List<String> lines = new ArrayList<>(numbers.lines());
        if (numbers.uncovered() != 0) {
            lines.add(numbers.uncovered() + " keyed openings with nothing in them");
        }
        return Layout.titled(title, lines);
    }
}
