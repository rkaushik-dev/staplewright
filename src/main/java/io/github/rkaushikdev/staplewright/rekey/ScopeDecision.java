package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the shop is going to do about a missing key, and why.
 *
 * <p>Deciding how much to rekey is where the money goes. A master always comes apart when the
 * policy says so, an exposed exterior door forces the work through any threshold, and so does a
 * key that was taken rather than lost. Anything else under the shop's threshold is left alone.
 * Going over the cylinder cap is flagged rather than refused, because the cap is there to get a
 * signature, not to leave a door on a compromised key.
 *
 * @param scope how far the work reaches
 * @param symbols the symbols to recut, shallowest first
 * @param cylinders the serials of the cylinders to take apart, in serial order
 * @param reason why, in the words the work order uses
 * @param overCap whether the work touches more cylinders than the policy's cap
 */
public record ScopeDecision(
        Scope scope, List<KeySymbol> symbols, List<String> cylinders, String reason, boolean overCap) {

    public ScopeDecision {
        symbols = List.copyOf(symbols);
        cylinders = List.copyOf(cylinders);
    }

    /** A decision within the cap. */
    public ScopeDecision(Scope scope, List<KeySymbol> symbols, List<String> cylinders, String reason) {
        this(scope, symbols, cylinders, reason, false);
    }

    /** How many cylinders come apart. */
    public int cylinderCount() {
        return cylinders.size();
    }

    /** Whether the decision is to leave everything alone. */
    public boolean doesNothing() {
        return scope == Scope.NOTHING;
    }

    /** One line for a work order: {@code group: 2 cylinders, 1 symbols recut, ...}. */
    public String summary() {
        return scope.label() + ": " + cylinderCount() + " cylinders, " + symbols.size() + " symbols recut, " + reason;
    }

    /** Read an assessment under the shop's standing policy. */
    public static ScopeDecision decide(Assessment assessment, CoverageMap coverage) {
        return decide(assessment, coverage, ScopePolicy.DEFAULT);
    }

    /** Read an assessment and say what work it justifies under one policy. */
    public static ScopeDecision decide(Assessment assessment, CoverageMap coverage, ScopePolicy policy) {
        KeySymbol symbol = assessment.incident().keySymbol();
        String forced = forcedBy(assessment, policy);

        if (forced.isEmpty() && assessment.openingCount() <= policy.ignoreBelowOpenings()) {
            return new ScopeDecision(Scope.NOTHING, List.of(), List.of(), "under the threshold the shop acts on");
        }

        Closure closure = Closure.of(coverage, List.of(symbol), policy.recutAllOnRepin());
        List<String> serials = closure.cylinders();
        if (serials.isEmpty()) {
            return new ScopeDecision(Scope.NOTHING, List.of(), List.of(), "that key turns nothing on record");
        }
        boolean over = policy.cylinderCap().filter(cap -> serials.size() > cap).isPresent();
        String reason = forced.isEmpty() ? "the key is out and the cylinders are known" : forced;
        if (over) {
            reason = reason + ", over the cap of " + policy.cylinderCap().get();
        }
        return new ScopeDecision(scopeFor(coverage, serials), closure.symbols(), serials, reason, over);
    }

    /**
     * Why the work goes ahead whatever the threshold says, or nothing when it does not have to.
     *
     * <p>A missing master and an exposed outside door come first, when policy says they force it.
     * After those it is how the key went. A theft forces the work, and so does a key copied without
     * a signature, because nobody knows how many of it are out. A key a leaver never handed back is
     * a lost key unless policy says the shop treats it as taken.
     */
    private static String forcedBy(Assessment assessment, ScopePolicy policy) {
        if (policy.alwaysRekeyMasters() && !assessment.incident().keySymbol().isChangeKey()) {
            return "a master is missing";
        }
        if (policy.alwaysRekeyExterior() && assessment.touchesOutside()) {
            return "an exterior door is exposed";
        }
        return switch (assessment.incident().cause()) {
            case STOLEN -> "the key was taken rather than lost";
            case DUPLICATED -> "copies were cut that nobody signed for";
            case UNRETURNED -> policy.rekeyUnreturned() ? "a leaver never handed the key back" : "";
            case LOST, UNACCOUNTED -> "";
        };
    }

    /** Name the reach of a set of cylinders the way a work order names it. */
    private static Scope scopeFor(CoverageMap coverage, List<String> serials) {
        if (serials.isEmpty()) {
            return Scope.NOTHING;
        }
        HardwareRegister register = coverage.register();
        Set<String> openings = new TreeSet<>();
        for (String serial : serials) {
            register.cylinder(serial).installedAt().ifPresent(openings::add);
        }
        Set<String> buildings = new TreeSet<>();
        for (String number : openings) {
            if (!number.isEmpty()) {
                buildings.add(register.openings().opening(number).building());
            }
        }
        if (buildings.size() > 1) {
            return Scope.SYSTEM;
        }
        if (openings.size() == 1) {
            return Scope.OPENING;
        }
        int total = register.inBuilding(buildings.iterator().next()).size();
        return serials.size() == total ? Scope.BRANCH : Scope.GROUP;
    }
}
