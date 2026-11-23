package io.github.rkaushikdev.staplewright.services;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.DepositRates;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.support.People;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceCounterTest {

    private static final LocalDate LATER = LocalDate.of(2025, 6, 2);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private CounterService aCounter() {
        return aCounter(new DepositRates());
    }

    private CounterService aCounter(DepositRates rates) {
        Directory directory = People.aDirectory();
        for (Keyholder person : directory) {
            session.add(ControlMappers.keyholderToRow(person));
        }
        session.flush();
        for (Authorizer signer : directory.authorizers()) {
            session.add(ControlMappers.authorizerToRow(signer));
        }
        session.flush();
        CounterService service = new CounterService(session, rates);
        service.openSeries();
        return service;
    }

    private static KeyRequestRow aRequest(CounterService service) {
        return aRequest(service, "kmaki", "AA1", "MOO", "moved into 214", false);
    }

    private static KeyRequestRow aRequest(
            CounterService service, String keyholder, String symbol, String building, String reason, boolean master) {
        return service.raiseRequest(keyholder, symbol, building, reason, TODAY, master);
    }

    private KeyRequestRow anApprovedRequest(CounterService service) {
        KeyRequestRow row = aRequest(service);
        service.approve(row.getNumber(), "rvirta", TODAY);
        return row;
    }

    private List<String> depositKinds() {
        return session.all(DepositLineRow.class).stream()
                .map(DepositLineRow::getKind)
                .toList();
    }

    private void separate(String identifier) {
        KeyholderRow person = session.get(KeyholderRow.class, identifier).orElseThrow();
        person.setStanding("separated");
        person.setSeparatedOn(TODAY);
        session.flush();
    }

    @Test
    void aRequestGetsTheNextNumber() {
        CounterService service = aCounter();
        assertThat(aRequest(service).getNumber()).isEqualTo("R-0001");
        assertThat(aRequest(service).getNumber()).isEqualTo("R-0002");
    }

    @Test
    void aRequestArrivesSubmitted() {
        CounterService service = aCounter();
        assertThat(aRequest(service).getState()).isEqualTo("submitted");
    }

    @Test
    void aRequestNeedsAReason() {
        CounterService service = aCounter();
        assertThatThrownBy(() -> aRequest(service, "kmaki", "AA1", "MOO", "  ", false))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aRequestForSomebodyWhoIsNotThere() {
        CounterService service = aCounter();
        assertThatThrownBy(() -> aRequest(service, "ghost", "AA1", "MOO", "moved into 214", false))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theRightSignatureApproves() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        KeyRequestRow approved = service.approve(row.getNumber(), "rvirta", TODAY);
        assertThat(approved.getState()).isEqualTo("approved");
        assertThat(approved.getDecidedBy()).isEqualTo("rvirta");
    }

    @Test
    void aSignatureThatDoesNotReachIsRefused() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        assertThatThrownBy(() -> service.approve(row.getNumber(), "phamm", TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aMasterNeedsASignatureThatMayApproveOne() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service, "kmaki", "AA", "MOO", "moved into 214", true);
        assertThatThrownBy(() -> service.approve(row.getNumber(), "rvirta", TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSeparatedKeyholderCannotBeApproved() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        separate("kmaki");
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.approve(row.getNumber(), "rvirta", TODAY));
        assertThat(caught.facts().get("standing")).isEqualTo("separated");
    }

    @Test
    void aKeyholderWhoSeparatesAfterApprovalGetsNoKey() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        service.approve(row.getNumber(), "rvirta", TODAY);
        separate("kmaki");
        assertThatThrownBy(() -> service.issue(row.getNumber(), LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aDenialSaysWhy() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        KeyRequestRow denied = service.deny(row.getNumber(), "rvirta", TODAY, "the room is shared");
        assertThat(denied.getState()).isEqualTo("denied");
        assertThatThrownBy(() -> service.approve(row.getNumber(), "rvirta", TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aDenialWithoutAReasonIsRefused() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        assertThatThrownBy(() -> service.deny(row.getNumber(), "rvirta", TODAY, "   "))
                .isInstanceOf(InputError.class);
    }

    @Test
    void issuingTakesATagAndChargesTheDeposit() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY);
        assertThat(key.getTag()).isEqualTo("41000");
        assertThat(key.getDepositCents()).isEqualTo(2500);
        assertThat(depositKinds()).containsExactly("charge");
    }

    @Test
    void aMasterCostsMore() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service, "phamm", "B", "HEI", "moved into 214", true);
        service.approve(row.getNumber(), "phamm", TODAY);
        assertThat(service.issue(row.getNumber(), TODAY).getDepositCents()).isEqualTo(5000);
    }

    @Test
    void theShopCanNameItsOwnRates() {
        CounterService service = aCounter(new DepositRates(1000, 9000));
        KeyRequestRow row = anApprovedRequest(service);
        assertThat(service.issue(row.getNumber(), TODAY).getDepositCents()).isEqualTo(1000);
    }

    @Test
    void aGrandMasterGoesAtItsOwnRateWhenTheShopNamesOne() {
        CounterService service = aCounter(new DepositRates().withGrandMasterCents(15000));
        KeyRequestRow grand = aRequest(service, "phamm", "B", "HEI", "runs the science precinct", true);
        service.approve(grand.getNumber(), "phamm", TODAY);
        assertThat(service.issue(grand.getNumber(), TODAY).getDepositCents()).isEqualTo(15000);
        KeyRequestRow master = aRequest(service, "phamm", "AB", "HEI", "runs the second floor", true);
        service.approve(master.getNumber(), "phamm", TODAY);
        assertThat(service.issue(master.getNumber(), TODAY).getDepositCents()).isEqualTo(5000);
    }

    @Test
    void theRatesFollowTheRequestAndThenTheLevel() {
        DepositRates rates = new DepositRates(2500, 5000, Optional.of(15000));
        assertThat(rates.forKey(KeySymbol.parse("AA1"), false)).isEqualTo(2500);
        assertThat(rates.forKey(KeySymbol.parse("AA1"), true)).isEqualTo(5000);
        assertThat(rates.forKey(KeySymbol.parse("ABC"), true)).isEqualTo(5000);
        assertThat(rates.forKey(KeySymbol.parse("A"), true)).isEqualTo(15000);
        assertThat(rates.forKey(KeySymbol.TOP, true)).isEqualTo(15000);
        assertThat(rates.forKey(KeySymbol.parse("A"), false)).isEqualTo(2500);
        assertThat(new DepositRates().forKey(KeySymbol.TOP, true)).isEqualTo(5000);
        assertThatThrownBy(() -> new DepositRates().withGrandMasterCents(-1)).isInstanceOf(InputError.class);
    }

    @Test
    void aDepositCanBeWaivedAtTheCounter() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY, Optional.empty(), Optional.of(0));
        assertThat(key.getDepositCents()).isZero();
        assertThat(session.all(DepositLineRow.class)).isEmpty();
    }

    @Test
    void issuingClosesTheRequest() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        service.issue(row.getNumber(), TODAY);
        assertThat(row.getState()).isEqualTo("filled");
        assertThatThrownBy(() -> service.issue(row.getNumber(), TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anUnapprovedRequestIsNotFilled() {
        CounterService service = aCounter();
        KeyRequestRow row = aRequest(service);
        assertThatThrownBy(() -> service.issue(row.getNumber(), TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aRequestThatDoesNotExist() {
        CounterService service = aCounter();
        assertThatThrownBy(() -> service.issue("R-9999", TODAY)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aReturnedKeyGetsItsDepositBack() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY);
        service.takeBack(key.getTag(), LATER);
        assertThat(depositKinds()).containsExactly("charge", "refund");
        assertThat(key.getState()).isEqualTo("returned");
    }

    @Test
    void aLostKeyForfeitsTheDeposit() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY);
        service.reportLost(key.getTag(), LATER, "left on a bus");
        assertThat(depositKinds()).containsExactly("charge", "forfeit");
        assertThat(key.getState()).isEqualTo("lost");
    }

    @Test
    void aKeyCannotComeBackTwice() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY);
        service.takeBack(key.getTag(), LATER);
        assertThatThrownBy(() -> service.takeBack(key.getTag(), LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aTagThatWasNeverIssued() {
        CounterService service = aCounter();
        assertThatThrownBy(() -> service.takeBack("99999", LATER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aKeyWithNoDepositSettlesQuietly() {
        CounterService service = aCounter();
        KeyRequestRow row = anApprovedRequest(service);
        IssuedKeyRow key = service.issue(row.getNumber(), TODAY, Optional.empty(), Optional.of(0));
        service.takeBack(key.getTag(), LATER);
        assertThat(session.all(DepositLineRow.class)).isEmpty();
    }

    @Test
    void tagsWalkInOrderAcrossSeveralIssues() {
        CounterService service = aCounter();
        List<String> tags = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            KeyRequestRow row = anApprovedRequest(service);
            tags.add(service.issue(row.getNumber(), TODAY).getTag());
        }
        assertThat(tags).containsExactly("41000", "41001", "41002");
    }
}
