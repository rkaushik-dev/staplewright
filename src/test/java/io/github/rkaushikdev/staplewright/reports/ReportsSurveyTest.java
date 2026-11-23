package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportsSurveyTest {

    private static final LocalDate WALKED = LocalDate.of(2025, 5, 12);

    private static SurveyLine aLine(String opening) {
        return aLine(opening, Condition.GOOD);
    }

    private static SurveyLine aLine(String opening, Condition condition, Deficiency... deficiencies) {
        return new SurveyLine(opening, WALKED, "jkoivu", condition)
                .toBuilder().deficiencies(deficiencies).build();
    }

    private static Survey aSurvey() {
        return aSurvey("MOO");
    }

    private static Survey aSurvey(String building) {
        Survey survey = new Survey(building, WALKED);
        survey.record(aLine("MOO-101"));
        survey.record(aLine("MOO-110", Condition.WORN));
        survey.record(aLine("MOO-214", Condition.FAILING, Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED));
        survey.record(aLine("MOO-216", Condition.BROKEN, Deficiency.NO_STRIKE));
        return survey;
    }

    @Test
    void theConditionTableCoversEveryValueWorstFirst() {
        List<String> lines = SurveyPages.conditionTable(aSurvey());
        assertThat(lines.get(0)).startsWith("condition");
        assertThat(lines.get(1)).startsWith("broken");
        assertThat(lines.get(lines.size() - 1)).startsWith("good");
    }

    @Test
    void theConditionTableCountsTheDoors() {
        List<String> lines = SurveyPages.conditionTable(aSurvey());
        assertThat(lines.get(1)).contains("broken");
        assertThat(Text.strip(lines.get(1))).endsWith("1");
    }

    @Test
    void theDeficiencyTableIsMostCommonFirst() {
        List<String> lines = SurveyPages.deficiencyTable(aSurvey());
        assertThat(lines.get(0)).isEqualTo("deficiencies found");
        assertThat(lines).hasSize(4);
    }

    @Test
    void aCleanSurveyReportsNothingFound() {
        Survey survey = new Survey("VOS", WALKED);
        survey.record(aLine("VOS-311"));
        assertThat(SurveyPages.deficiencyTable(survey)).containsExactly("nothing to report on any door");
    }

    @Test
    void theBlockingWorkNamesTheRooms() {
        List<String> lines = SurveyPages.blockingWork(aSurvey(), aCampus());
        assertThat(lines.get(0)).isEqualTo("2 doors need work before a rekey");
        assertThat(lines).anyMatch(line -> line.contains("MOO 214"));
    }

    @Test
    void aDoorNobodyHasARecordOfKeepsItsNumber() {
        Survey survey = new Survey("MOO", WALKED);
        survey.record(aLine("MOO-999", Condition.BROKEN, Deficiency.NO_CYLINDER));
        assertThat(SurveyPages.blockingWork(survey, aCampus())).anyMatch(line -> line.contains("MOO-999"));
    }

    @Test
    void aSurveyWithNothingBlockingSaysSo() {
        Survey survey = new Survey("VOS", WALKED);
        survey.record(aLine("VOS-311"));
        assertThat(SurveyPages.blockingWork(survey, aCampus()).get(0)).startsWith("nothing blocks");
    }

    @Test
    void theDoorsNobodyWalkedAreNamed() {
        List<String> lines = SurveyPages.unwalked(aSurvey(), aCampus());
        assertThat(lines.get(0)).startsWith("1 doors not walked");
        assertThat(lines).anyMatch(line -> line.contains("MOO-218"));
    }

    @Test
    void aFullWalkSaysSo() {
        Survey survey = new Survey("VOS", WALKED);
        for (String number : List.of("VOS-100", "VOS-311", "VOS-312")) {
            survey.record(aLine(number));
        }
        assertThat(SurveyPages.unwalked(survey, aCampus()).get(0)).startsWith("every keyed door");
    }

    @Test
    void theInventoryIsWorstFirst() {
        List<String> lines = SurveyPages.inventory(aSurvey());
        assertThat(lines.get(0)).startsWith("door");
        assertThat(lines.get(1)).startsWith("MOO-216");
    }

    @Test
    void aDoorWithNothingWrongSaysNothing() {
        List<String> lines = SurveyPages.inventory(aSurvey());
        assertThat(lines).anyMatch(line -> line.endsWith("nothing"));
    }

    @Test
    void theReportReadsTopToBottom() {
        List<String> lines = SurveyPages.surveyReport(aSurvey(), aCampus());
        assertThat(lines.get(0)).startsWith("MOO surveyed 2025-05-12");
        assertThat(lines.get(1)).isEqualTo("walked by jkoivu");
        assertThat(lines).anyMatch(line -> line.startsWith("2 doors need work"));
        assertThat(lines).anyMatch(line -> line.startsWith("door"));
    }

    @Test
    void aSurveyWithNoSurveyorNamed() {
        Survey survey = new Survey("VOS", WALKED);
        List<String> lines = SurveyPages.surveyReport(survey, aCampus());
        assertThat(lines.get(1)).isEqualTo("walked by nobody named");
    }

    @Test
    void theWorstBuildingsComeFirst() {
        Survey other = new Survey("VOS", WALKED);
        other.record(aLine("VOS-311"));
        List<Tally> found = SurveyPages.worstBuildings(List.of(other, aSurvey()));
        assertThat(found.get(0)).isEqualTo(new Tally("MOO", 2));
        assertThat(found.get(1)).isEqualTo(new Tally("VOS", 0));
    }

    @Test
    void theWorstListCanBeCutShort() {
        assertThat(SurveyPages.worstBuildings(List.of(aSurvey(), new Survey("VOS", WALKED)), 1))
                .hasSize(1);
    }

    @Test
    void theBlockingFaultsAreCountedAcrossSurveys() {
        Map<String, Integer> counts = SurveyPages.blockingDeficiencies(List.of(aSurvey(), aSurvey("HEI")));
        assertThat(counts.get("hardware needs replacing before it is rekeyed")).isEqualTo(2);
        assertThat(counts).doesNotContainKey("cylinder cannot be identified");
    }

    @Test
    void noBlockingFaultsCountAsNothing() {
        Survey survey = new Survey("VOS", WALKED);
        survey.record(aLine("VOS-311"));
        assertThat(SurveyPages.blockingDeficiencies(List.of(survey))).isEmpty();
    }

    @Test
    void aDoorThatNeedsAKeyToGetOutLeadsTheReport() {
        Survey survey = aSurvey();
        survey.record(aLine("MOO-001", Condition.GOOD, Deficiency.KEYED_EGRESS));
        List<String> lines = SurveyPages.surveyReport(survey, aCampus());
        int safety = lines.indexOf("1 doors break the life safety code");
        int blocking = lines.indexOf("3 doors need work before a rekey");
        assertThat(safety).isEqualTo(3);
        assertThat(blocking).isGreaterThan(safety);
        assertThat(lines.get(safety + 1)).startsWith("  ").endsWith("needs a key to get out");
    }

    @Test
    void aBuildingWithNoKeyedEgressSaysSo() {
        assertThat(SurveyPages.lifeSafety(aSurvey(), aCampus()))
                .containsExactly("no door in this building needs a key to get out");
        assertThat(SurveyPages.surveyReport(aSurvey(), aCampus()))
                .contains("no door in this building needs a key to get out");
    }

    @Test
    void keyedEgressIsLifeSafetyAndBlocksTheRekeyAndNothingElseIsLifeSafety() {
        SurveyLine line = aLine("MOO-001", Condition.GOOD, Deficiency.KEYED_EGRESS);
        assertThat(line.breaksLifeSafety()).isTrue();
        assertThat(line.needsWorkFirst()).isTrue();
        assertThat(line.isSound()).isFalse();
        assertThat(Arrays.stream(Deficiency.values()).filter(Deficiency::isLifeSafety))
                .containsExactly(Deficiency.KEYED_EGRESS);
        assertThat(Arrays.stream(Deficiency.values()).filter(Deficiency::blocksRekey))
                .containsExactlyInAnyOrder(
                        Deficiency.WORN_OUT, Deficiency.NO_STRIKE, Deficiency.NO_CYLINDER, Deficiency.KEYED_EGRESS);
        assertThat(aLine("MOO-214", Condition.FAILING, Deficiency.WORN_OUT).breaksLifeSafety())
                .isFalse();
    }

    @Test
    void theSurveyListsItsLifeSafetyDoorsInDoorOrder() {
        Survey survey = aSurvey();
        survey.record(aLine("MOO-310", Condition.WORN, Deficiency.KEYED_EGRESS));
        survey.record(aLine("MOO-002", Condition.GOOD, Deficiency.KEYED_EGRESS, Deficiency.UNKNOWN_KEY));
        assertThat(survey.lifeSafety()).extracting(SurveyLine::opening).containsExactly("MOO-002", "MOO-310");
        assertThat(Deficiency.fromValue("needs a key to get out")).isSameAs(Deficiency.KEYED_EGRESS);
    }
}
