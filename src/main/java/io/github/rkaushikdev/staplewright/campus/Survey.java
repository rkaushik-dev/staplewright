package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Tallies;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The hardware survey, which is what a building looks like before anybody touches it: one walk
 * round a building, line by line.
 *
 * <p>A survey is a walk with a clipboard. The deficiencies fall out of it: a door with a cylinder
 * nobody can identify, a door with the wrong function for the room, a door whose lock is so worn it
 * takes two hands. The value is entirely in doing it before the work is quoted. A survey after the
 * crew arrives is called a surprise, and the shop bills for those. A door is surveyed once per walk,
 * and the lines come back in door order.
 */
public final class Survey implements Iterable<SurveyLine> {

    private static final Comparator<SurveyLine> WORST_FIRST = Comparator.comparing(
                    (SurveyLine line) -> line.condition().value(), Comparator.reverseOrder())
            .thenComparing(line -> line.deficiencies().size(), Comparator.reverseOrder())
            .thenComparing(SurveyLine::opening);

    private final String building;
    private final LocalDate walkedOn;
    private final Registry<SurveyLine> lines = new Registry<>("survey line", "opening");

    /**
     * A walk round one building on one day.
     *
     * @throws InputError when no building is named
     */
    public Survey(String building, LocalDate walkedOn) {
        if (Text.isBlank(building)) {
            throw new InputError("a survey covers a building");
        }
        this.building = building;
        this.walkedOn = walkedOn;
    }

    /** The code of the building walked. */
    public String building() {
        return building;
    }

    /** The day it was walked. */
    public LocalDate walkedOn() {
        return walkedOn;
    }

    /**
     * Write down one door.
     *
     * @throws io.github.rkaushikdev.staplewright.ConflictError when that door is already on the survey
     */
    public SurveyLine record(SurveyLine line) {
        return lines.add(line.opening(), line);
    }

    /**
     * The line for one door.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody walked it
     */
    public SurveyLine line(String opening) {
        return lines.get(opening);
    }

    /** A second look at one door, which happens on every survey. The line is checked again. */
    public SurveyLine revise(String opening, UnaryOperator<SurveyLine.Builder> changes) {
        SurveyLine line = lines.get(opening);
        return lines.put(opening, changes.apply(line.toBuilder()).build());
    }

    /** Every line, in door order. */
    public List<SurveyLine> lines() {
        return lines.byKey();
    }

    /** The doors where one kind of deficiency was found, in door order. */
    public List<SurveyLine> withDeficiency(Deficiency kind) {
        return lines().stream()
                .filter(line -> line.deficiencies().contains(kind))
                .toList();
    }

    /** Doors that need other work before a rekey is worth booking, in door order. */
    public List<SurveyLine> blocking() {
        return lines().stream().filter(SurveyLine::needsWorkFirst).toList();
    }

    /** Doors that break the life safety code, in door order. */
    public List<SurveyLine> lifeSafety() {
        return lines().stream().filter(SurveyLine::breaksLifeSafety).toList();
    }

    /** Doors with nothing wrong, in door order. */
    public List<SurveyLine> sound() {
        return lines().stream().filter(SurveyLine::isSound).toList();
    }

    /** The worst condition first, then the most deficiencies, then door order. */
    public List<SurveyLine> worstFirst() {
        return lines().stream().sorted(WORST_FIRST).toList();
    }

    /** How many doors are in each condition, by label, every condition listed and best first. */
    public Map<String, Integer> countsByCondition() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Condition condition : Condition.values()) {
            counts.put(condition.label(), 0);
        }
        for (SurveyLine line : lines()) {
            counts.merge(line.condition().label(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** How often each deficiency was found, the most common first and a tie by its wording. */
    public Map<String, Integer> countsByDeficiency() {
        List<String> found = lines().stream()
                .flatMap(line -> line.deficiencies().stream())
                .map(Deficiency::value)
                .toList();
        return Tallies.count(found, wording -> wording);
    }

    /** Doors on the list that nobody walked, in number order. */
    public List<String> coverage(Collection<String> openings) {
        Set<String> walked = lines().stream().map(SurveyLine::opening).collect(Collectors.toSet());
        return openings.stream()
                .filter(number -> !walked.contains(number))
                .sorted()
                .toList();
    }

    /** Everybody who walked part of the survey, each once, in name order. */
    public List<String> surveyors() {
        return lines().stream().map(SurveyLine::surveyor).distinct().sorted().toList();
    }

    /** Whether one door is on the survey. */
    public boolean contains(Object opening) {
        return lines.contains(opening);
    }

    /** Every line, in door order. */
    @Override
    public Iterator<SurveyLine> iterator() {
        return lines().iterator();
    }

    /** How many doors were walked. */
    public int size() {
        return lines.size();
    }
}
