package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Cause;
import io.github.rkaushikdev.staplewright.rekey.Estimate;
import io.github.rkaushikdev.staplewright.rekey.Incident;
import io.github.rkaushikdev.staplewright.rekey.OrderLine;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.rekey.ScopePolicy;
import io.github.rkaushikdev.staplewright.rekey.WorkOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ReportsRekeyTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    /** The four pieces of paper a rekey produces, as the packet staples them. */
    private record Pieces(Assessment found, ScopeDecision decision, WorkOrder order, Estimate cost) {}

    private static Incident anIncident(String symbol, String tag) {
        return new Incident("I-2025-004", tag, symbol, Cause.LOST, WHEN, "kmaki");
    }

    private static Pieces thePieces() {
        return thePieces("AA1", "41207", policy -> policy);
    }

    private static Pieces thePieces(String symbol, String tag) {
        return thePieces(symbol, tag, policy -> policy);
    }

    private static Pieces thePieces(UnaryOperator<ScopePolicy> policy) {
        return thePieces("AA1", "41207", policy);
    }

    private static Pieces thePieces(String symbol, String tag, UnaryOperator<ScopePolicy> policy) {
        CoverageMap coverage = aCoverage();
        Assessment found = Assessment.assess(anIncident(symbol, tag), coverage, aLedger());
        ScopeDecision decision = ScopeDecision.decide(found, coverage, policy.apply(new ScopePolicy()));
        Estimate cost = Estimate.of(decision, coverage, List.copyOf(found.keysToRecut()));
        if (decision.cylinders().size() != found.openings().size()) {
            throw new IllegalStateException("a cylinder a door, or the order does not line up");
        }
        List<OrderLine> lines = new ArrayList<>();
        for (int index = 0; index < decision.cylinders().size(); index++) {
            lines.add(new OrderLine(
                    decision.cylinders().get(index), found.openings().get(index)));
        }
        WorkOrder order = new WorkOrder("W-1041", "I-2025-004", lines);
        return new Pieces(found, decision, order, cost);
    }

    @Test
    void theIncidentSheetNamesTheKeyAndTheGrade() {
        List<String> lines = RekeyPages.incidentSheet(thePieces().found());
        assertThat(lines.get(0)).startsWith("incident I-2025-004  lost");
        assertThat(lines.get(1)).contains("key AA1 on tag 41207");
        assertThat(lines.get(2)).contains("grade routine");
    }

    @Test
    void theIncidentSheetListsTheKeysToRecut() {
        assertThat(RekeyPages.incidentSheet(thePieces().found()))
                .anyMatch(line -> line.contains("keys to recut: 41208"));
    }

    @Test
    void anExteriorDoorGetsItsOwnLine() {
        assertThat(RekeyPages.incidentSheet(thePieces("A", "41210").found()))
                .anyMatch(line -> line.contains("exterior doors exposed"));
    }

    @Test
    void aNoteIsCarriedThrough() {
        CoverageMap coverage = aCoverage();
        Incident stolen = new Incident("I-2025-004", "41207", "AA1", Cause.STOLEN, WHEN, "kmaki", "taken from a coat");
        Assessment found = Assessment.assess(stolen, coverage, aLedger());
        assertThat(RekeyPages.incidentSheet(found)).anyMatch(line -> line.contains("taken from a coat"));
    }

    @Test
    void somebodyHoldingNothingIsNamedAsNobody() {
        CoverageMap coverage = aCoverage();
        Incident nobody = new Incident("I-2025-004", "41207", "AA1", Cause.LOST, WHEN, "");
        Assessment found = Assessment.assess(nobody, coverage, aLedger());
        assertThat(RekeyPages.incidentSheet(found).get(1)).contains("held by nobody");
    }

    @Test
    void theScopeSheetNamesTheDecisionAndTheRecuts() {
        List<String> lines = RekeyPages.scopeSheet(thePieces().decision());
        assertThat(lines.get(0)).startsWith("scope group:");
        assertThat(lines).anyMatch(line -> line.contains("recut AA1"));
    }

    @Test
    void goingOverTheCapAsksForASignature() {
        ScopeDecision decision =
                thePieces(policy -> policy.with(scope -> scope.cylinderCap(1))).decision();
        assertThat(RekeyPages.scopeSheet(decision)).anyMatch(line -> line.contains("wants a signature"));
    }

    @Test
    void aLineSheetHasRoomToWriteOn() {
        WorkOrder order = thePieces().order();
        List<String> lines = RekeyPages.lineSheet(order.lines().get(0), order);
        assertThat(lines.get(0)).startsWith("W-1041  cylinder C-1001");
        assertThat(lines).anyMatch(line -> line.startsWith("pinned by"));
        assertThat(lines.get(lines.size() - 1)).isEqualTo("");
    }

    @Test
    void thePacketHasASheetACylinder() {
        WorkOrder order = thePieces().order();
        List<String> lines = RekeyPages.orderPacket(order);
        List<String> headers = lines.stream()
                .filter(line -> line.startsWith("W-1041  cylinder"))
                .toList();
        assertThat(headers).hasSize(order.lines().size());
    }

    @Test
    void thePacketWalksTheDoorsInOrder() {
        WorkOrder order = thePieces().order();
        List<String> lines = RekeyPages.orderPacket(order);
        List<String> doors = lines.stream()
                .filter(line -> line.contains("door "))
                .map(line -> line.split("door ", -1)[1])
                .toList();
        assertThat(doors).isSorted();
    }

    @Test
    void anUnassignedOrderSaysSo() {
        assertThat(RekeyPages.orderPacket(thePieces().order())).contains("technician unassigned");
    }

    @Test
    void theCostSheetCarriesTheTotals() {
        List<String> lines = RekeyPages.costSheet(thePieces().cost());
        assertThat(lines.get(0)).startsWith("2 cylinders");
        assertThat(lines).anyMatch(line -> line.startsWith("total "));
    }

    @Test
    void theWholePacketIsStapledInReadingOrder() {
        Pieces pieces = thePieces();
        List<String> lines = RekeyPages.packet(pieces.found(), pieces.decision(), pieces.order(), pieces.cost());
        assertThat(lines.get(0)).startsWith("incident");
        assertThat(lines).anyMatch(line -> line.startsWith("scope "));
        assertThat(lines).anyMatch(line -> line.startsWith("work order"));
    }

    @Test
    void thePacketJoinsIntoAPage() {
        Pieces pieces = thePieces();
        String page =
                Layout.asText(RekeyPages.packet(pieces.found(), pieces.decision(), pieces.order(), pieces.cost()));
        assertThat(page).startsWith("incident I-2025-004");
        assertThat(page).contains("\n\n");
    }
}
