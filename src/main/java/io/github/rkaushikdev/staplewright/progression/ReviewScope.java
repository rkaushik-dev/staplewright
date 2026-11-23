package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.List;
import java.util.Optional;

/**
 * What a schedule review is asked to look at beyond the keys themselves.
 *
 * @param step how many depths apart two cuts in a moving chamber are
 * @param plan the plan the change keys are held against, when there is one
 * @param groups the keying groups whose cylinders are judged for crowded chambers
 * @param mostWafers the most wafers the shop builds into one chamber
 */
public record ReviewScope(int step, Optional<ProgressionPlan> plan, List<KeyingGroup> groups, int mostWafers) {

    public ReviewScope {
        if (step < 1) {
            throw new InputError("a progression step is at least one", Facts.of("step", step));
        }
        if (mostWafers < 0) {
            throw new InputError("a wafer limit is not negative", Facts.of("most_wafers", mostWafers));
        }
        groups = List.copyOf(groups);
    }

    /** The keys alone at this step: no plan, no groups, the shop's wafer limit. */
    public static ReviewScope of(int step) {
        return new ReviewScope(step, Optional.empty(), List.of(), Validation.MOST_WAFERS);
    }

    /** The same review with the change keys held against a plan. */
    public ReviewScope against(ProgressionPlan value) {
        return new ReviewScope(step, Optional.of(value), groups, mostWafers);
    }

    /** The same review with these keying groups judged for crowded chambers. */
    public ReviewScope withGroups(List<KeyingGroup> value) {
        return new ReviewScope(step, plan, value, mostWafers);
    }
}
