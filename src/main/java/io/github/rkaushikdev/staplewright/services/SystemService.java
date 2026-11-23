package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.SystemRepository;
import io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.progression.ReviewScope;
import io.github.rkaushikdev.staplewright.progression.Validation;
import io.github.rkaushikdev.staplewright.rekey.Quarantine;
import io.github.rkaushikdev.staplewright.rekey.RetiredBitting;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opening a keying system, drawing symbols on it, and cutting them.
 *
 * <p>This is the first place the pieces meet. The chart comes off a table, the keyway family off
 * another, the policy from settings, and the domain objects that fall out of those are what decide
 * whether a cut is allowed. Nothing in the domain knows there is a database, and nothing in the
 * database knows what macs is.
 *
 * <p>Cutting goes through the schedule, which checks the rules and the duplicate, and then through
 * the quarantine, which knows about cuts retired years ago on systems that no longer exist. Either
 * can refuse, and the order matters: a cut that breaks macs is a mistake worth correcting, while a
 * cut that is quarantined is a fact nobody can argue with.
 */
public final class SystemService {

    private final Session session;
    private final SystemRepository systems;
    private final CuttingRules rules;

    public SystemService(Session session) {
        this(session, new CuttingRules());
    }

    public SystemService(Session session, CuttingRules rules) {
        this.session = session;
        this.systems = new SystemRepository(session);
        this.rules = rules;
    }

    public Session session() {
        return session;
    }

    /** Open a system stepping by two, with no opening day. */
    public SystemRow openSystem(String name, int chartId, String familyName, String topSection) {
        return openSystem(name, chartId, familyName, topSection, 2, Optional.empty());
    }

    public SystemRow openSystem(String name, int chartId, String familyName, String topSection, int step) {
        return openSystem(name, chartId, familyName, topSection, step, Optional.empty());
    }

    /** Open a system on a chart and a family, with the top key already drawn on its schedule. */
    public SystemRow openSystem(
            String name, int chartId, String familyName, String topSection, int step, Optional<LocalDate> openedOn) {
        if (systems.byName(name).isPresent()) {
            throw new ConflictError("that system is already open", Facts.of("system", name));
        }
        SystemRow row = new SystemRow(name, session.reference(ChartRow.class, chartId), familyName, topSection);
        row.setProgressionStep(step);
        row.setOpenedOn(openedOn.orElse(null));
        systems.add(row);
        session.flush();
        session.add(ScheduleEntryRow.of(row, KeySymbol.TOP, topSection));
        session.flush();
        return row;
    }

    /** The system by name, refusing one nobody opened. */
    public SystemRow system(String name) {
        return systems.byName(name)
                .orElseThrow(() -> new NotFoundError("no such keying system", Facts.of("system", name)));
    }

    /** A keyway family by name, refusing one nobody stored. */
    public KeywayFamily family(String familyName) {
        if (session.list("select s from KeywaySectionRow s where s.familyName = ?1", KeywaySectionRow.class, familyName)
                .isEmpty()) {
            throw new NotFoundError("no such keyway family", Facts.of("family", familyName));
        }
        return Loaders.loadFamily(session, familyName);
    }

    /** The system's chart under this service's rules. */
    public RuleSet ruleset(SystemRow system) {
        return new RuleSet(ReferenceMappers.chartFromRow(system.getChart()), rules);
    }

    /** The whole schedule, rebuilt out of its rows. */
    public KeyingSchedule schedule(String name) {
        SystemRow system = system(name);
        family(system.getFamilyName());
        return Loaders.loadSchedule(session, system, rules);
    }

    /** Put a symbol on the schedule without cutting it, on the section of the key above. */
    public ScheduleEntryRow draw(String name, KeySymbol symbol) {
        return draw(name, symbol, Optional.empty());
    }

    public ScheduleEntryRow draw(String name, KeySymbol symbol, String section) {
        return draw(name, symbol, Optional.of(section));
    }

    /** Put a symbol on the schedule without cutting it. */
    public ScheduleEntryRow draw(String name, KeySymbol symbol, Optional<String> section) {
        SystemRow system = system(name);
        ScheduleEntry entry = schedule(name).add(symbol, section, "");
        ScheduleEntryRow row = KeyingMappers.entryToRow(entry, system);
        session.add(row);
        session.flush();
        return row;
    }

    /** Cut a symbol, refusing anything the rules or the quarantine refuse. */
    public ScheduleEntryRow cut(String name, KeySymbol symbol, Bitting bitting) {
        SystemRow system = system(name);
        schedule(name).assign(symbol, bitting);
        Quarantine quarantine = quarantine();
        if (quarantine.isRetired(bitting)) {
            RetiredBitting entry = quarantine.entry(bitting);
            throw new ConflictError(
                    "that cut is out of service",
                    Facts.of(
                            "bitting",
                            bitting.render(),
                            "reason",
                            entry.reason().value(),
                            "retired_on",
                            entry.retiredOn().toString()));
        }
        ScheduleEntryRow row = entryRow(system, symbol);
        row.setBitting(bitting);
        session.flush();
        return row;
    }

    /** Take the cut off a symbol and leave the symbol on the schedule. */
    public ScheduleEntryRow uncut(String name, KeySymbol symbol) {
        ScheduleEntryRow row = entryRow(system(name), symbol);
        row.setBitting(null);
        session.flush();
        return row;
    }

    public Quarantine quarantine() {
        return Loaders.loadQuarantine(session);
    }

    /** The system's keying groups by name. */
    public Map<String, KeyingGroup> groups(String name) {
        return KeyingMappers.groupsFromRows(systems.groups(system(name).getId()));
    }

    /** Everything wrong with one system, worst first, its keying groups judged for crowded chambers. */
    public List<Finding> review(String name) {
        SystemRow system = system(name);
        List<KeyingGroup> groups = List.copyOf(groups(name).values());
        return Validation.review(
                schedule(name), ReviewScope.of(system.getProgressionStep()).withGroups(groups));
    }

    /** Close a system so nothing else gets drawn on it; once only. */
    public SystemRow archive(String name) {
        SystemRow system = system(name);
        if (system.isArchived()) {
            throw new ConflictError("that system is already archived", Facts.of("system", name));
        }
        system.setArchived(true);
        session.flush();
        return system;
    }

    private ScheduleEntryRow entryRow(SystemRow system, KeySymbol symbol) {
        return session.first(
                        "select e from ScheduleEntryRow e where e.system = ?1 and e.symbol = ?2",
                        ScheduleEntryRow.class,
                        system,
                        symbol)
                .orElseThrow(() ->
                        new NotFoundError("that symbol is not on the schedule", Facts.of("symbol", symbol.render())));
    }
}
