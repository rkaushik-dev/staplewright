package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.HierarchyError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.spec.CutViolation;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import io.github.rkaushikdev.staplewright.support.Systems;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class ScheduleTest {

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static List<String> rendered(List<KeySymbol> symbols) {
        return symbols.stream().map(KeySymbol::render).toList();
    }

    @Test
    void aNewScheduleHoldsOnlyTheTopKey() {
        KeyingSchedule schedule = Systems.emptySchedule();
        assertThat(schedule.size()).isEqualTo(1);
        assertThat(schedule.symbols()).containsExactly(sym("GGM"));
        assertThat(schedule.entry(sym("GGM")).section()).isEqualTo("L");
    }

    @Test
    void symbolsComeBackInScheduleOrder() {
        assertThat(rendered(Systems.smallSchedule().symbols()))
                .containsExactly("GGM", "A", "B", "AA", "AB", "AA1", "AA2", "AB1", "AB2");
    }

    @Test
    void aKeyInheritsTheSectionAboveItUnlessToldOtherwise() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(schedule.entry(sym("AA1")).section()).isEqualTo("LC");
        assertThat(schedule.entry(sym("AB1")).section()).isEqualTo("LD");
    }

    @Test
    void aSectionTheKeyAboveCannotEnterIsRefused() {
        KeyingSchedule schedule = Systems.emptySchedule();
        schedule.add(sym("A"), "LA");
        HierarchyError caught = catchThrowableOfType(HierarchyError.class, () -> schedule.add(sym("AA"), "LE"));
        assertThat(caught.facts()).containsEntry("above", "LA");
    }

    @Test
    void aSymbolCannotBeAddedTwice() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThatThrownBy(() -> schedule.add(sym("AA"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSymbolWithNoParentInTheScheduleIsRefused() {
        KeyingSchedule schedule = Systems.emptySchedule();
        HierarchyError caught = catchThrowableOfType(HierarchyError.class, () -> schedule.add(sym("AA1")));
        assertThat(caught.facts()).containsEntry("wanted", "AA");
    }

    @Test
    void theTopKeyCannotBeAddedAgain() {
        assertThatThrownBy(() -> Systems.emptySchedule().add(sym("GGM"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutASymbolThatIsNotThere() {
        assertThatThrownBy(() -> Systems.emptySchedule().entry(sym("AA"))).isInstanceOf(NotFoundError.class);
    }

    @Test
    void childrenAreTheKeysDirectlyUnderOne() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(rendered(schedule.children(sym("A")))).containsExactly("AA", "AB");
        assertThat(rendered(schedule.children(sym("AA")))).containsExactly("AA1", "AA2");
        assertThat(schedule.children(sym("AA1"))).isEmpty();
    }

    @Test
    void descendantsGoAllTheWayDown() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(rendered(schedule.descendants(sym("A")))).containsExactly("AA", "AB", "AA1", "AA2", "AB1", "AB2");
        assertThat(schedule.descendants(sym("GGM"))).hasSize(8);
    }

    @Test
    void mastersAndChangeKeysAreToldApart() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(rendered(schedule.masters())).containsExactly("GGM", "A", "B", "AA", "AB");
        assertThat(rendered(schedule.changeKeys())).containsExactly("AA1", "AA2", "AB1", "AB2");
    }

    @Test
    void aDrawnScheduleStartsWithEverythingOpen() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(schedule.openSymbols()).hasSize(schedule.size());
        assertThat(schedule.cutSymbols()).isEmpty();
    }

    @Test
    void cuttingASymbolMovesItOutOfTheOpenList() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        assertThat(schedule.bittingOf(sym("AA1"))).contains(Bitting.parse("342156"));
        assertThat(schedule.cutSymbols()).contains(sym("AA1"));
        assertThat(schedule.openSymbols()).doesNotContain(sym("AA1"));
    }

    @Test
    void clearingASymbolPutsItBack() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        schedule.clear(sym("AA1"));
        assertThat(schedule.bittingOf(sym("AA1"))).isEmpty();
    }

    @Test
    void twoSymbolsCannotBeCutTheSame() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> schedule.assign(sym("AA2"), Bitting.parse("342156")));
        assertThat(caught.facts()).containsEntry("held_by", "AA1");
    }

    @Test
    void recuttingASymbolToWhatItAlreadyIsIsAllowed() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        assertThat(schedule.symbolCutTo(Bitting.parse("342156"))).contains(sym("AA1"));
    }

    @Test
    void aBittingTheRulesetRefusesNeverReachesTheSchedule() {
        KeyingSchedule schedule = Systems.smallSchedule();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> schedule.assign(sym("AA1"), Bitting.parse("192156")));
        assertThat(caught.fact("reasons"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains("macs");
        assertThat(schedule.bittingOf(sym("AA1"))).isEmpty();
    }

    @Test
    void nobodyHoldsABittingThatWasNeverCut() {
        assertThat(Systems.smallSchedule().symbolCutTo(Bitting.parse("342156"))).isEmpty();
    }

    @Test
    void aScheduleReportsKeysThatPolicyWouldNoLongerCut() {
        KeyingSchedule schedule = Systems.smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("342159"));
        assertThat(schedule.problems()).isEmpty();
        schedule.setRuleset(
                new RuleSet(schedule.ruleset().chart(), new CuttingRules().with(policy -> policy.shallowTipLimit(4))));
        List<CutViolation> problems = schedule.problems();
        assertThat(problems.stream().map(CutViolation::rule).toList()).containsExactly("tip");
        assertThat(problems.get(0).message()).startsWith("AA1:");
    }

    @Test
    void anEntryRendersForThePrintedSchedule() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(Text.words(schedule.entry(sym("AA1")).render())).containsExactly("AA1", "LC", "open");
        schedule.assign(sym("AA1"), Bitting.parse("342156"));
        assertThat(Text.words(schedule.entry(sym("AA1")).render())).containsExactly("AA1", "LC", "342156");
    }

    @Test
    void aScheduleIsAContainerOfSymbols() {
        KeyingSchedule schedule = Systems.smallSchedule();
        assertThat(schedule.contains(sym("AA1"))).isTrue();
        assertThat(schedule.contains(sym("AC1"))).isFalse();
        assertThat(schedule.contains("AA1")).isFalse();
        List<ScheduleEntry> walked = new ArrayList<>();
        schedule.forEach(walked::add);
        assertThat(walked).hasSize(schedule.size());
    }
}
