package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What a keying group costs before anything is cut.
 *
 * <p>Two things about a group are worth computing before the bench sees it. How many wafers it
 * costs, which is the bench time and the failure rate. And how many keys it gives away, which is
 * the security cost, and which goes up fast the moment a group holds two symbols that are not
 * related to each other.
 */
public final class Groups {

    /** How many groups the worst list names unless told otherwise. */
    public static final int WORST_LIMIT = 5;

    private Groups() {}

    /**
     * The cuts behind a group, in schedule order.
     *
     * @throws NotFoundError for a symbol nobody has cut yet, or one the schedule does not hold
     */
    public static List<Bitting> bittingsFor(KeyingSchedule schedule, KeyingGroup group) {
        List<Bitting> found = new ArrayList<>();
        for (KeySymbol symbol : group.ordered()) {
            ScheduleEntry entry = schedule.entry(symbol);
            found.add(entry.bitting()
                    .orElseThrow(() -> new NotFoundError(
                            "that key has not been cut yet",
                            Facts.of("symbol", symbol.render(), "group", group.name()))));
        }
        return List.copyOf(found);
    }

    /**
     * What the keys in a group are cut to so far, in the group's order, leaving out any symbol not
     * yet cut or not on the schedule at all.
     */
    public static List<Bitting> cutSoFar(KeyingSchedule schedule, KeyingGroup group) {
        return group.ordered().stream()
                .filter(schedule::contains)
                .flatMap(symbol -> schedule.bittingOf(symbol).stream())
                .toList();
    }

    /** Every keyway section the group's keys are cut on, once each, in code order. */
    public static List<String> sectionsFor(KeyingSchedule schedule, KeyingGroup group) {
        return List.copyOf(group.symbols().stream()
                .map(symbol -> schedule.entry(symbol).section())
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    /**
     * The section a cylinder has to be keyed to for the whole group to enter.
     *
     * <p>Every key in the group must enter the cylinder, so the cylinder gets the section that all of
     * them reach, which is the lowest one in the family.
     *
     * @throws ConflictError when no one section takes every key
     */
    public static String cylinderSection(KeyingSchedule schedule, KeyingGroup group) {
        KeywayFamily family = schedule.family();
        List<String> sections = sectionsFor(schedule, group);
        for (String candidate : sections) {
            if (sections.stream().allMatch(section -> family.enters(section, candidate))) {
                return candidate;
            }
        }
        throw new ConflictError(
                "no one section takes every key in this group", Facts.of("group", group.name(), "sections", sections));
    }

    /** Wafer count and key leak for one group, without pinning anything. */
    public static GroupCost costOf(KeyingSchedule schedule, KeyingGroup group) {
        List<Bitting> bittings = bittingsFor(schedule, group);
        int wafers = 0;
        for (int chamber = 1; chamber <= schedule.chambers(); chamber++) {
            Set<Integer> depths = new HashSet<>();
            for (Bitting bitting : bittings) {
                depths.add(bitting.at(chamber));
            }
            wafers += depths.size() - 1;
        }
        return new GroupCost(
                group.name(),
                bittings.size(),
                wafers,
                Incidentals.operatingCount(bittings),
                Incidentals.incidentalCount(bittings));
    }

    /** The five groups giving away the most keys, worst first. */
    public static List<GroupCost> worstGroups(KeyingSchedule schedule, List<KeyingGroup> groups) {
        return worstGroups(schedule, groups, WORST_LIMIT);
    }

    /** The groups giving away the most keys, worst first, then the costliest, then by name. */
    public static List<GroupCost> worstGroups(KeyingSchedule schedule, List<KeyingGroup> groups, int limit) {
        return groups.stream()
                .map(group -> costOf(schedule, group))
                .sorted(Comparator.comparing(GroupCost::incidental, Comparator.reverseOrder())
                        .thenComparing(GroupCost::wafers, Comparator.reverseOrder())
                        .thenComparing(GroupCost::group))
                .limit(limit)
                .toList();
    }
}
