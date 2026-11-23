package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.KeywayError;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * The patent clock, which is the part of key control nobody budgets for.
 *
 * <p>A restricted keyway is only restricted because a patent stops anybody else milling the blank.
 * The factory contract on top of it is worth something, but the patent is the part with teeth, and
 * it runs out on a date printed on the blank. After that date the section is a normal keyway that
 * anybody can buy on the open market, and every system riding on it quietly stops being a
 * restricted system.
 *
 * <p>Nothing breaks on that day. That is the problem. A shop that has not looked at the dates finds
 * out when a department turns up with a key it cut at a hardware shop for four dollars, and by then
 * the answer is a keyway migration across every building on that section, which is a two year
 * project.
 *
 * <p>So this reads the dates off the sections, sorts out what expires when, and answers the two
 * questions worth asking: what is exposed today, and what will be exposed by the time a system
 * planned this year is finished.
 */
public final class Patents {

    /** How many years a system planned today takes to land. */
    public static final int PLANNING_YEARS = 5;

    /** A utility patent runs twenty years from the day it was filed. */
    public static final int UTILITY_TERM_YEARS = 20;

    /** A design patent granted from the day the longer term began runs fifteen years from grant. */
    public static final int DESIGN_TERM_YEARS = 15;

    /** What a design patent granted before the longer term began runs for, from grant. */
    public static final int OLD_DESIGN_TERM_YEARS = 14;

    /** The first day a design patent carried the fifteen year term. */
    public static final LocalDate DESIGN_TERM_CHANGED = LocalDate.of(2015, 5, 13);

    private static final Pattern WRITTEN =
            Pattern.compile("(?:(filed|design granted) )?([0-9]{4})-([0-9]{2})-([0-9]{2})");

    private static final Comparator<Exposure> SOONEST_FIRST = Comparator.comparing(
                    (Exposure item) -> item.expiresOn().orElse(LocalDate.MAX))
            .thenComparing(Exposure::section);

    private Patents() {}

    /**
     * The day the patent on the section runs out, or nothing if it never carried one.
     *
     * <p>The catalogue prints these as plain days, {@code 2027-02-14}, and the section keeps them as
     * text, because a chart is a document before it is a data structure. Older catalogues print the
     * patent instead of its end, and the term is the statute's: {@code filed 2009-04-02} is a utility
     * patent and runs twenty years from filing, and {@code design granted 2016-03-01} is a design
     * patent and runs fifteen years from grant, or fourteen if it was granted before 13 May 2015.
     * Reading them lives here so a malformed date is one error in one place rather than a surprise
     * in the middle of a report.
     *
     * @throws KeywayError for a date nobody can read
     */
    public static Optional<LocalDate> expiryOf(KeywaySection section) {
        return section.patentExpires().map(written -> readExpiry(written)
                .orElseThrow(() -> new KeywayError(
                        "that section has a patent date nobody can read",
                        Facts.of("section", section.code(), "written", written))));
    }

    private static Optional<LocalDate> readExpiry(String written) {
        Matcher parts = WRITTEN.matcher(written);
        if (!parts.matches()) {
            return Optional.empty();
        }
        LocalDate day;
        try {
            day = LocalDate.of(
                    Integer.parseInt(parts.group(2)),
                    Integer.parseInt(parts.group(3)),
                    Integer.parseInt(parts.group(4)));
        } catch (DateTimeException unreadable) {
            return Optional.empty();
        }
        if (day.getYear() < 1) {
            return Optional.empty();
        }
        String basis = parts.group(1) == null ? "" : parts.group(1);
        return Optional.of(
                switch (basis) {
                    case "filed" -> day.plusYears(UTILITY_TERM_YEARS);
                    case "design granted" ->
                        day.plusYears(day.isBefore(DESIGN_TERM_CHANGED) ? OLD_DESIGN_TERM_YEARS : DESIGN_TERM_YEARS);
                    default -> day;
                });
    }

    /** Where one section stands. */
    public static Exposure exposureOf(KeywaySection section) {
        return new Exposure(section.code(), section.restricted(), expiryOf(section));
    }

    /** Every section in the family, soonest expiry first, the undated ones last, then by code. */
    public static List<Exposure> exposures(KeywayFamily family) {
        return StreamSupport.stream(family.spliterator(), false)
                .map(Patents::exposureOf)
                .sorted(SOONEST_FIRST)
                .toList();
    }

    /** Sections anybody can buy a blank for, restricted or never restricted, soonest expiry first. */
    public static List<String> openSections(KeywayFamily family, LocalDate today) {
        return exposures(family).stream()
                .filter(item -> item.isOpen(today))
                .map(Exposure::section)
                .toList();
    }

    /** Sections that will be open before a project started today lands, over the usual horizon. */
    public static List<String> closingSections(KeywayFamily family, LocalDate today) {
        return closingSections(family, today, PLANNING_YEARS);
    }

    /** Sections that will be open before a project started today and running this long lands. */
    public static List<String> closingSections(KeywayFamily family, LocalDate today, int withinYears) {
        return exposures(family).stream()
                .filter(item -> item.isClosing(today, withinYears))
                .map(Exposure::section)
                .toList();
    }

    /** The next section to lose its patent, which is the one to plan against. */
    public static Optional<Exposure> nextExpiry(KeywayFamily family, LocalDate today) {
        return exposures(family).stream()
                .filter(item ->
                        item.expiresOn().isPresent() && item.expiresOn().get().isAfter(today))
                .findFirst();
    }

    /** Sections that are genuinely restricted on the day, in code order. */
    public static List<String> stillRestricted(KeywayFamily family, LocalDate today) {
        Set<String> openToday = Set.copyOf(openSections(family, today));
        return family.codes().stream().filter(code -> !openToday.contains(code)).toList();
    }

    /** Whether a new system may be laid down on this section, over the usual horizon. */
    public static boolean sectionIsSafe(KeywayFamily family, String code, LocalDate today) {
        return sectionIsSafe(family, code, today, PLANNING_YEARS);
    }

    /**
     * Whether a new system may be laid down on this section.
     *
     * <p>A section is safe when it is restricted today and stays restricted long enough to be worth
     * the work. A section whose patent runs out inside the horizon is not a security decision, it is
     * a deadline.
     */
    public static boolean sectionIsSafe(KeywayFamily family, String code, LocalDate today, int withinYears) {
        Exposure item = exposureOf(family.section(code));
        return !item.isOpen(today) && !item.isClosing(today, withinYears);
    }

    /** Refuse a system about to be laid down on a section that is going, over the usual horizon. */
    public static void refuseUnsafeSection(KeywayFamily family, String code, LocalDate today) {
        refuseUnsafeSection(family, code, today, PLANNING_YEARS);
    }

    /**
     * Refuse a system about to be laid down on a section that is going.
     *
     * @throws KeywayError naming the section, its expiry or that it was never restricted, and the
     *     horizon
     */
    public static void refuseUnsafeSection(KeywayFamily family, String code, LocalDate today, int withinYears) {
        if (sectionIsSafe(family, code, today, withinYears)) {
            return;
        }
        Exposure item = exposureOf(family.section(code));
        throw new KeywayError(
                "that section will not stay restricted long enough to key on",
                Facts.of(
                        "section",
                        code,
                        "expires",
                        item.expiresOn().map(LocalDate::toString).orElse("never restricted"),
                        "horizon",
                        withinYears));
    }

    /**
     * Open sections a key cut on this one enters, nearest first.
     *
     * <p>This is the question that matters after an expiry, because the damage is not to the section
     * that expired. It is to every section underneath it, which is where the change keys live.
     */
    public static List<String> exposedBelow(KeywayFamily family, String code, LocalDate today) {
        Set<String> openToday = Set.copyOf(openSections(family, today));
        return family.sectionsEntered(code).stream().filter(openToday::contains).toList();
    }

    /** Which of the sections a shop is actually keying on have gone open, once each, in code order. */
    public static List<String> systemsAtRisk(KeywayFamily family, Collection<String> sectionsInUse, LocalDate today) {
        Set<String> openToday = Set.copyOf(openSections(family, today));
        return sectionsInUse.stream()
                .filter(openToday::contains)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Which section to move off first, worst exposure first.
     *
     * <p>Worst means widest. A root section going open exposes everything under it, so it gets moved
     * before a leaf that only exposes itself, whatever the dates say about which expired first.
     * Sections that were never restricted are not a migration.
     */
    public static List<String> migrationOrder(KeywayFamily family, LocalDate today) {
        return openSections(family, today).stream()
                .filter(code -> family.section(code).restricted())
                .sorted(Comparator.comparing(
                                (String code) -> -family.sectionsEntered(code).size())
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Where the family stands, in the order somebody reads it. */
    public static List<String> patentReport(KeywayFamily family, LocalDate today) {
        List<String> lines = new ArrayList<>();
        lines.add(family.name() + " patent position at " + today);
        List<String> gone = migrationOrder(family, today);
        if (gone.isEmpty()) {
            lines.add("every restricted section is still restricted");
        } else {
            lines.add(gone.size() + " sections are open and were not meant to be");
            gone.forEach(code -> lines.add("  " + code));
        }
        List<String> closing = closingSections(family, today);
        if (!closing.isEmpty()) {
            lines.add(closing.size() + " sections go open inside " + PLANNING_YEARS + " years");
            closing.forEach(
                    code -> lines.add("  " + exposureOf(family.section(code)).describe(today)));
        }
        nextExpiry(family, today).ifPresent(following -> lines.add("next is " + following.describe(today)));
        return List.copyOf(lines);
    }

    /** Sections that carry a date, soonest first, then by code, for a wall planner. */
    public static List<DatedSection> datesInOrder(List<KeywaySection> sections) {
        return sections.stream()
                .flatMap(item -> expiryOf(item).map(day -> new DatedSection(item.code(), day)).stream())
                .sorted(Comparator.comparing(DatedSection::expiresOn).thenComparing(DatedSection::code))
                .toList();
    }
}
