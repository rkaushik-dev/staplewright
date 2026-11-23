package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Cause;
import io.github.rkaushikdev.staplewright.rekey.Scope;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.rekey.ScopePolicy;
import io.github.rkaushikdev.staplewright.rekey.Severity;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceIncidentsTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private IncidentService aService() {
        return aService(new ScopePolicy());
    }

    private IncidentService aService(ScopePolicy policy) {
        DbCampus.build(session);
        IncidentService service = new IncidentService(session, policy);
        service.openSeries();
        return service;
    }

    private String keyState(String tag) {
        return session.get(IssuedKeyRow.class, tag).orElseThrow().getState();
    }

    private List<RetiredBittingRow> retired() {
        return session.all(RetiredBittingRow.class);
    }

    @Test
    void reportingWritesTheIncidentAndClosesTheKey() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN, "left on a bus");
        assertThat(row.getNumber()).isEqualTo("I-0001");
        assertThat(row.getSymbol()).isEqualTo("AA1");
        assertThat(keyState("41207")).isEqualTo("lost");
    }

    @Test
    void aTheftIsRecordedAsATheft() {
        IncidentService service = aService();
        service.report("41207", Cause.STOLEN, WHEN, "taken from a coat in the hall");
        assertThat(keyState("41207")).isEqualTo("stolen");
    }

    @Test
    void aTheftHasToBeWrittenUp() {
        IncidentService service = aService();
        assertThatThrownBy(() -> service.report("41207", Cause.STOLEN, WHEN)).isInstanceOf(ConflictError.class);
    }

    @Test
    void reportingRetiresTheCut() {
        IncidentService service = aService();
        service.report("41207", Cause.LOST, WHEN);
        List<RetiredBittingRow> held = retired();
        assertThat(held.stream().map(row -> row.getBitting().render()).toList()).containsExactly("620446");
        assertThat(held.get(0).getReason()).isEqualTo("compromised");
    }

    @Test
    void theShopCanDeclineToRetireTheCut() {
        IncidentService service = aService();
        service.report("41207", Cause.LOST, WHEN, "", false);
        assertThat(retired()).isEmpty();
    }

    @Test
    void aCutAlreadyRetiredIsNotRetiredTwice() {
        IncidentService service = aService();
        service.report("41207", Cause.LOST, WHEN);
        service.report("41208", Cause.LOST, WHEN);
        assertThat(retired()).hasSize(1);
    }

    @Test
    void aKeyThatIsNotOutCannotBeReported() {
        IncidentService service = aService();
        service.report("41207", Cause.LOST, WHEN);
        assertThatThrownBy(() -> service.report("41207", Cause.LOST, WHEN)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aTagNobodyIssued() {
        IncidentService service = aService();
        assertThatThrownBy(() -> service.report("99999", Cause.LOST, WHEN)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSeverityIsGradedWhenItIsReported() {
        IncidentService service = aService();
        IncidentRow change = service.report("41207", Cause.LOST, WHEN);
        IncidentRow grand = service.report("41211", Cause.LOST, WHEN);
        assertThat(change.getSeverity()).isEqualTo("routine");
        assertThat(grand.getSeverity()).isEqualTo("critical");
    }

    @Test
    void theAssessmentComesBackOffTheRecord() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        Assessment found = service.assessment(row.getNumber());
        assertThat(found.openings()).containsExactly("MOO-214", "MOO-216");
        assertThat(found.severity()).isSameAs(Severity.ROUTINE);
    }

    @Test
    void anIncidentNobodyReported() {
        IncidentService service = aService();
        assertThatThrownBy(() -> service.assessment("I-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void thePlanNamesTheCylinders() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        ScopeDecision plan = service.plan(row.getNumber());
        assertThat(plan.cylinders()).containsExactly("C-1001", "C-1002");
        assertThat(plan.scope()).isSameAs(Scope.GROUP);
    }

    @Test
    void aLostGrandMasterPlansASystemRekey() {
        IncidentService service = aService();
        IncidentRow row = service.report("41211", Cause.LOST, WHEN);
        assertThat(service.plan(row.getNumber()).scope()).isSameAs(Scope.SYSTEM);
    }

    @Test
    void theOrderCarriesALineACylinder() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        WorkOrderRow order = service.raiseOrder(row.getNumber());
        assertThat(order.getNumber()).isEqualTo("W-1041");
        List<WorkOrderLineRow> lines = session.all(WorkOrderLineRow.class);
        assertThat(lines.stream().map(WorkOrderLineRow::getSerial).sorted().toList())
                .containsExactly("C-1001", "C-1002");
        assertThat(lines.stream().map(WorkOrderLineRow::getOpeningNumber).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("MOO-214", "MOO-216");
    }

    @Test
    void theOrderPointsBackAtTheIncident() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        WorkOrderRow order = service.raiseOrder(row.getNumber());
        assertThat(order.getIncidentNumber()).contains(row.getNumber());
        assertThat(order.getState()).isEqualTo("drafted");
    }

    @Test
    void anOrderCanBeScheduledAsItIsRaised() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        WorkOrderRow order = service.raiseOrder(row.getNumber(), LocalDate.of(2025, 4, 15));
        assertThat(order.getScheduledFor()).contains(LocalDate.of(2025, 4, 15));
    }

    @Test
    void anIncidentThatCallsForNoWorkRaisesNoOrder() {
        IncidentService service = aService(
                new ScopePolicy().with(scope -> scope.ignoreBelowOpenings(10).alwaysRekeyMasters(false)));
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        assertThatThrownBy(() -> service.raiseOrder(row.getNumber())).isInstanceOf(ConflictError.class);
    }

    @Test
    void theSymbolsThatHaveToBeRecutComeBack() {
        IncidentService service = aService();
        IncidentRow row = service.report("41207", Cause.LOST, WHEN);
        assertThat(service.symbolsRecut(row.getNumber()).stream()
                        .map(KeySymbol::render)
                        .toList())
                .containsExactly("AA1");
    }

    @Test
    void twoIncidentsGetConsecutiveNumbers() {
        IncidentService service = aService();
        IncidentRow first = service.report("41207", Cause.LOST, WHEN);
        IncidentRow second = service.report("41208", Cause.LOST, WHEN);
        assertThat(List.of(first.getNumber(), second.getNumber())).containsExactly("I-0001", "I-0002");
    }
}
