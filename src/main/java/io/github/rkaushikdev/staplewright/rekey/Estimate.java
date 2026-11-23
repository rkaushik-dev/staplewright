package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.util.Numbers;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a rekey costs, in minutes and in cents, before anybody approves it. The number that goes on
 * the bottom of the memo.
 *
 * <p>The estimate is the argument. A department that has lost a master wants to hear that it is one
 * cylinder and a new key, and the only way to have that conversation is to put the real number in
 * front of them. Interchangeable cores are the interesting line: a core is pinned at the bench and
 * swapped in seconds, so the labour at the door collapses and the labour in the shop goes up. A
 * mortise cylinder is the other way round.
 *
 * @param cylinders how many cylinders come apart
 * @param cores how many of them are interchangeable cores
 * @param keys how many keys get cut
 * @param buildings how many buildings the crew goes to
 * @param benchMinutes repinning and cutting at the bench
 * @param doorMinutes pulling and refitting at the doors
 * @param travelMinutes getting to the buildings and back
 * @param labourCents what those minutes cost, in whole cents
 * @param partsCents blanks and pins, in whole cents
 */
public record Estimate(
        int cylinders,
        int cores,
        int keys,
        int buildings,
        int benchMinutes,
        int doorMinutes,
        int travelMinutes,
        int labourCents,
        int partsCents) {

    private static final Comparator<PricedOption> CHEAPEST = Comparator.comparingInt(
                    (PricedOption option) -> option.estimate().totalCents())
            .thenComparingInt(option -> option.estimate().totalMinutes())
            .thenComparing(PricedOption::name);

    /** Every minute on the job. */
    public int totalMinutes() {
        return benchMinutes + doorMinutes + travelMinutes;
    }

    /** Labour and parts together, in whole cents. */
    public int totalCents() {
        return labourCents + partsCents;
    }

    /** The minutes as hours, rounded to two places for the memo. */
    public double hours() {
        return Numbers.round(totalMinutes() / 60.0, 2);
    }

    /** One line for the memo: {@code 2 cylinders and 1 keys, 92 minutes, 9876 cents}. */
    public String summary() {
        return cylinders + " cylinders and " + keys + " keys, " + totalMinutes() + " minutes, " + totalCents()
                + " cents";
    }

    /** Cost one rekey decision at the shop's standing rates. */
    public static Estimate of(ScopeDecision decision, CoverageMap coverage, List<String> keysToCut) {
        return of(decision, coverage, keysToCut, RateCard.DEFAULT);
    }

    /** Cost one rekey decision done in the working day. */
    public static Estimate of(ScopeDecision decision, CoverageMap coverage, List<String> keysToCut, RateCard rates) {
        return of(decision, coverage, keysToCut, rates, false);
    }

    /**
     * Cost one rekey decision, split by where the time is spent. Labour is the minutes at the hourly
     * rate, rounded to the cent; parts are a blank a key and a repin's pins a cylinder.
     *
     * <p>Done after hours, the door and travel minutes go at the after hours rate. The bench minutes
     * do not: the cylinders are pinned in the shop in the day either way.
     */
    public static Estimate of(
            ScopeDecision decision, CoverageMap coverage, List<String> keysToCut, RateCard rates, boolean afterHours) {
        HardwareRegister register = coverage.register();
        List<Cylinder> cylinders =
                decision.cylinders().stream().map(register::cylinder).toList();
        int cores = (int) cylinders.stream().filter(Cylinder::isCore).count();
        int conventional = cylinders.size() - cores;
        Set<String> buildings = new TreeSet<>();
        for (Cylinder cylinder : cylinders) {
            cylinder.installedAt()
                    .filter(number -> !number.isEmpty())
                    .ifPresent(number ->
                            buildings.add(register.openings().opening(number).building()));
        }

        int bench = rates.benchMinutesPerCylinder() * cylinders.size();
        bench += rates.keyCutMinutes() * keysToCut.size();
        int door = rates.doorMinutesPerCylinder() * conventional;
        door += rates.coreDoorMinutes() * cores;
        int travel = rates.travelMinutesPerBuilding() * buildings.size();

        int minutes = bench + door + travel;
        int labour = afterHours
                ? rates.labourCents(bench, false) + rates.labourCents(door + travel, true)
                : rates.labourCents(minutes, false);
        int parts = rates.blankCents() * keysToCut.size();
        parts += rates.pinCents() * rates.pinsPerCylinder() * cylinders.size();

        return new Estimate(
                cylinders.size(), cores, keysToCut.size(), buildings.size(), bench, door, travel, labour, parts);
    }

    /** How many cents the second option saves against the first. */
    public static int compare(Estimate first, Estimate second) {
        return first.totalCents() - second.totalCents();
    }

    /**
     * Name the cheapest option, breaking a tie on the fewest minutes and then on the name.
     *
     * @throws InputError when there is nothing to compare
     */
    public static String cheaperOf(List<PricedOption> options) {
        return options.stream()
                .min(CHEAPEST)
                .map(PricedOption::name)
                .orElseThrow(() -> new InputError("nothing to compare"));
    }
}
