package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.BlindCode;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.Marking;
import io.github.rkaushikdev.staplewright.keys.Stamp;
import io.github.rkaushikdev.staplewright.keys.StampPolicy;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlindCodeRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceStampingTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);
    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");
    private static final KeySymbol MASTER = KeySymbol.parse("AA");
    private static final KeySymbol TOP = KeySymbol.parse("GGM");

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private StampingService aService() {
        return new StampingService(session);
    }

    private StampingService aService(StampPolicy policy) {
        return new StampingService(session, policy);
    }

    @Test
    void anEmptyBookStartsAtTheSeriesStart() {
        assertThat(aService().assign(MASTER, TODAY).getCode()).isEqualTo("4000");
    }

    @Test
    void aSymbolKeepsTheCodeItWasGiven() {
        StampingService service = aService();
        String first = service.assign(MASTER, TODAY).getCode();
        assertThat(service.assign(MASTER, TODAY).getCode()).isEqualTo(first);
    }

    @Test
    void theNextSymbolTakesTheNextCode() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        assertThat(service.assign(TOP, TODAY).getCode()).isEqualTo("4007");
    }

    @Test
    void theBookSurvivesAReload() {
        aService().assign(MASTER, TODAY);
        assertThat(aService().assign(TOP, TODAY).getCode()).isEqualTo("4007");
    }

    @Test
    void aCodeCarriesWhoAssignedIt() {
        BlindCodeRow row = aService().assign(MASTER, TODAY, "rvirta");
        assertThat(row.getAssignedBy()).isEqualTo("rvirta");
        assertThat(row.getAssignedOn()).isEqualTo(TODAY);
    }

    @Test
    void theCodeForASymbolComesBack() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        assertThat(service.codeFor(MASTER)).isEqualTo("4000");
    }

    @Test
    void aSymbolNobodyStampedHasNoCode() {
        assertThatThrownBy(() -> aService().codeFor(MASTER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aNumberOnAKeyReadsBackAsASymbol() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        assertThat(service.identify("4000")).isEqualTo("AA");
    }

    @Test
    void aNumberWithSpaceRoundItStillReads() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        assertThat(service.identify("  4000 ")).isEqualTo("AA");
    }

    @Test
    void aNumberNobodyStampedIsA404() {
        assertThatThrownBy(() -> aService().identify("9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theWholeBookComesBackWhenAskedByName() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        service.assign(TOP, TODAY);
        assertThat(service.theWholeBook()).containsExactly(new BlindCode("4000", "AA"), new BlindCode("4007", "GGM"));
    }

    @Test
    void stampingAChangeKeyNeedsNoCode() {
        StampingService service = aService();
        assertThat(service.stampKey("41207", TODAY).text()).isEqualTo("AA1");
        assertThat(service.theWholeBook()).isEmpty();
    }

    @Test
    void stampingAMasterAssignsACode() {
        StampingService service = aService();
        Stamp found = service.stampKey("41209", TODAY);
        assertThat(found.isBlind()).isTrue();
        assertThat(service.identify(found.text())).isEqualTo("AA");
    }

    @Test
    void aKeyNobodyIssuedIsA404() {
        assertThatThrownBy(() -> aService().stampKey("99999", TODAY)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aBatchComesBackInTagOrder() {
        assertThat(aService().stampBatch(List.of("41211", "41207"), TODAY).stream()
                        .map(Stamp::tag))
                .containsExactly("41207", "41211");
    }

    @Test
    void twoKeysToOneSymbolShareACode() {
        List<Stamp> found = aService().stampBatch(List.of("41207", "41208"), TODAY);
        assertThat(found.get(0).text()).isEqualTo(found.get(1).text());
    }

    @Test
    void aBatchWithAMissingKeyIsA404() {
        assertThatThrownBy(() -> aService().stampBatch(List.of("41207", "99999"), TODAY))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSheetCountsTheRun() {
        assertThat(aService().sheet(List.of("41207", "41211"), TODAY).get(0)).isEqualTo("2 keys to stamp");
    }

    @Test
    void theSheetSaysHowManyAreBlind() {
        List<String> lines = aService().sheet(List.of("41207", "41211"), TODAY);
        assertThat(lines.get(lines.size() - 1)).startsWith("1 of them are blind");
    }

    @Test
    void everythingOutCoversEveryIssuedKey() {
        assertThat(aService().everythingOut(TODAY)).hasSize(5);
    }

    @Test
    void aPolicyThatStampsNothingLeavesKeysBlank() {
        StampPolicy policy = new StampPolicy()
                .with(stamps -> stamps.changeKeys(Marking.NOTHING).masters(Marking.NOTHING));
        assertThat(aService(policy).blankKeys(TODAY)).hasSize(5);
    }

    @Test
    void theDefaultPolicyLeavesNothingBlank() {
        assertThat(aService().blankKeys(TODAY)).isEmpty();
    }

    @Test
    void aCodeCannotBeRetiredWhileKeysCarryIt() {
        StampingService service = aService();
        service.assign(MASTER, TODAY);
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> service.retireCode(MASTER));
        assertThat(caught.facts()).containsEntry("keys", 1);
    }

    @Test
    void aCodeNobodyCarriesCanBeRetired() {
        StampingService service = aService();
        service.assign(KeySymbol.parse("AB1"), TODAY);
        service.retireCode(KeySymbol.parse("AB1"));
        assertThat(service.theWholeBook()).isEmpty();
    }

    @Test
    void retiringACodeNobodyHasIsA404() {
        assertThatThrownBy(() -> aService().retireCode(MASTER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theRepositoryFindsARowByItsCode() {
        aService().assign(MASTER, TODAY);
        assertThat(new BlindCodeRepository(session).byCode("4000").orElseThrow().getSymbol())
                .isEqualTo("AA");
    }

    @Test
    void aCodeNobodyAssignedFindsNothing() {
        assertThat(new BlindCodeRepository(session).byCode("9999")).isEmpty();
    }

    @Test
    void oneCodeCannotStandForTwoSymbols() {
        session.add(new BlindCodeRow("AA", "4000", TODAY));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(new BlindCodeRow("AB", "4000", TODAY));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anEmptyCodeIsRefused() {
        assertThatThrownBy(() -> {
                    session.add(new BlindCodeRow("AA", "", TODAY));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theKeysCutToASymbolComeBack() {
        assertThat(StampingService.keysForSymbol(session, CHANGE_KEY)).containsExactly("41207", "41208");
    }

    @Test
    void aSymbolNobodyHoldsHasNoKeys() {
        assertThat(StampingService.keysForSymbol(session, KeySymbol.parse("AB1")))
                .isEmpty();
    }

    @Test
    void theSymbolsOutAreTheOnesNeedingACode() {
        assertThat(StampingService.issuedSymbols(session)).containsExactly("A", "AA", "AA1", "GGM");
    }

    @Test
    void aPolicyThatStampsNothingIsVisibleWithoutTheBook() {
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.changeKeys(Marking.NOTHING));
        assertThat(StampingService.unstampedOut(session, policy)).containsExactly("41207", "41208");
    }

    @Test
    void theDefaultPolicyLeavesNothingUnstamped() {
        assertThat(StampingService.unstampedOut(session, new StampPolicy())).isEmpty();
    }
}
