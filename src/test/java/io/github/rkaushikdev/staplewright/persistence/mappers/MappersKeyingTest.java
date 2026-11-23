package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.entryFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.entryToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.groupFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.groupToRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.groupsFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.scheduleFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.scheduleToRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.symbolsIn;
import static io.github.rkaushikdev.staplewright.support.Systems.campusFamily;
import static io.github.rkaushikdev.staplewright.support.Systems.smallSchedule;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers.GroupRows;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import io.github.rkaushikdev.staplewright.support.Charts;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappersKeyingTest {

    private static final Map<String, String> CUTS = cuts();

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("AA", "622446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        return cuts;
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static KeyingSchedule aCutSchedule() {
        KeyingSchedule schedule = smallSchedule();
        CUTS.forEach((symbol, bitting) -> schedule.assign(sym(symbol), Bitting.parse(bitting)));
        return schedule;
    }

    private static ChartRow aChartRow() {
        return new ChartRow("Sargent", "LA six pin", 6, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 335, 15, 231, 156, 7);
    }

    /** A system row nobody has written, for the mappings that need no database. */
    private static SystemRow aLooseSystem() {
        return new SystemRow("chester park", aChartRow(), "Sargent LA", "L");
    }

    private SystemRow aSystem() {
        ChartRow chart = aChartRow();
        session.add(chart);
        session.flush();
        SystemRow system = new SystemRow("chester park", chart, "Sargent LA", "L");
        session.add(system);
        session.flush();
        return system;
    }

    private static RuleSet aRuleset() {
        return new RuleSet(Charts.sixPinChart(), new CuttingRules());
    }

    private void storeGroup(GroupRows rows) {
        session.add(rows.group());
        session.flush();
        for (GroupMemberRow member : rows.members()) {
            session.add(member);
        }
    }

    @Test
    void oneEntrySurvivesARoundTrip() {
        KeyingSchedule schedule = aCutSchedule();
        ScheduleEntry entry = schedule.entry(sym("AA1"));
        assertThat(entryFromRow(entryToRow(entry, aLooseSystem()))).isEqualTo(entry);
    }

    @Test
    void anUncutEntrySurvivesARoundTrip() {
        KeyingSchedule schedule = smallSchedule();
        ScheduleEntry entry = schedule.entry(sym("AB1"));
        assertThat(entryFromRow(entryToRow(entry, aLooseSystem())).bitting()).isEmpty();
    }

    @Test
    void aWholeScheduleSurvivesTheDatabase() {
        SystemRow system = aSystem();
        KeyingSchedule schedule = aCutSchedule();
        session.addAll(scheduleToRows(schedule, system));
        session.flush();
        List<ScheduleEntryRow> rows = session.all(ScheduleEntryRow.class);
        KeyingSchedule back = scheduleFromRows("chester park", aRuleset(), campusFamily(), "L", rows);
        assertThat(back.symbols()).isEqualTo(schedule.symbols());
        assertThat(back.bittingOf(sym("AA1"))).contains(Bitting.parse("620446"));
    }

    @Test
    void theSectionsSurvive() {
        SystemRow system = aSystem();
        session.addAll(scheduleToRows(aCutSchedule(), system));
        session.flush();
        List<ScheduleEntryRow> rows = session.all(ScheduleEntryRow.class);
        KeyingSchedule back = scheduleFromRows("chester park", aRuleset(), campusFamily(), "L", rows);
        assertThat(back.entry(sym("AA1")).section()).isEqualTo("LC");
        assertThat(back.entry(sym("AB1")).section()).isEqualTo("LD");
    }

    @Test
    void aLoadedScheduleKeepsLinesThatPolicyWouldNowRefuse() {
        SystemRow system = aSystem();
        KeyingSchedule schedule = aCutSchedule();
        session.addAll(scheduleToRows(schedule, system));
        session.flush();
        List<ScheduleEntryRow> rows = session.all(ScheduleEntryRow.class);
        RuleSet strict =
                new RuleSet(Charts.sixPinChart(), new CuttingRules().with(policy -> policy.shallowTipLimit(2)));
        KeyingSchedule back = scheduleFromRows("chester park", strict, campusFamily(), "L", rows);
        assertThat(back.size()).isEqualTo(schedule.size());
        assertThat(back.problems()).isNotEmpty();
    }

    @Test
    void theRowsComeOutShallowFirst() {
        List<ScheduleEntryRow> rows = scheduleToRows(aCutSchedule(), aLooseSystem());
        List<Integer> levels = rows.stream().map(row -> row.getSymbol().level()).toList();
        assertThat(levels).isEqualTo(levels.stream().sorted().toList());
    }

    @Test
    void theSymbolsCanBeReadWithoutBuildingASchedule() {
        List<ScheduleEntryRow> rows = scheduleToRows(aCutSchedule(), aLooseSystem());
        assertThat(symbolsIn(rows).stream().map(KeySymbol::render).limit(3).toList())
                .containsExactly("GGM", "A", "B");
    }

    @Test
    void aGroupSurvivesARoundTrip() {
        SystemRow system = aSystem();
        KeyingGroup group = new KeyingGroup("moore offices", List.of(sym("AA1"), sym("AA"), sym("GGM")));
        GroupRows rows = groupToRows(group, system);
        storeGroup(rows);
        session.flush();
        session.refresh(rows.group());
        assertThat(groupFromRow(rows.group()).ordered()).isEqualTo(group.ordered());
    }

    @Test
    void theMembersComeOutInScheduleOrder() {
        SystemRow system = aSystem();
        KeyingGroup group = new KeyingGroup("moore offices", List.of(sym("AA1"), sym("GGM"), sym("AA")));
        GroupRows rows = groupToRows(group, system);
        storeGroup(rows);
        session.flush();
        session.refresh(rows.group());
        KeyingGroup back = groupFromRow(rows.group());
        assertThat(back.symbols().stream().map(KeySymbol::render).toList()).containsExactly("GGM", "AA", "AA1");
    }

    @Test
    void severalGroupsComeBackKeyedByName() {
        SystemRow system = aSystem();
        Map<String, List<String>> wanted = new LinkedHashMap<>();
        wanted.put("offices", List.of("AA1"));
        wanted.put("storeroom", List.of("AA2", "AA"));
        wanted.forEach((name, symbols) -> {
            KeyingGroup group = new KeyingGroup(
                    name, symbols.stream().map(MappersKeyingTest::sym).toList());
            storeGroup(groupToRows(group, system));
        });
        session.flush();
        List<GroupRow> rows = session.all(GroupRow.class);
        Map<String, KeyingGroup> found = groupsFromRows(rows);
        assertThat(found.keySet().stream().sorted().toList()).containsExactly("offices", "storeroom");
        assertThat(found.get("storeroom").size()).isEqualTo(2);
    }
}
