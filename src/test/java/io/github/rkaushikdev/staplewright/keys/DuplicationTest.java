package io.github.rkaushikdev.staplewright.keys;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DuplicationTest {

    private static final LocalDate SIGNED = LocalDate.of(2025, 4, 1);
    private static final List<String> PEOPLE = List.of("kmaki", "dsalo", "rvirta", "phamm");

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    /** A ledger holding this many keys cut to each symbol, tagged from 41000 in the order given. */
    @SafeVarargs
    private static KeyLedger aLedger(Map.Entry<String, Integer>... counts) {
        KeyLedger ledger = new KeyLedger(aDirectory());
        int tag = 41000;
        for (Map.Entry<String, Integer> count : counts) {
            for (int index = 0; index < count.getValue(); index++) {
                ledger.issue(String.valueOf(tag), count.getKey(), PEOPLE.get(index % PEOPLE.size()), TODAY);
                tag++;
            }
        }
        return ledger;
    }

    private static Authorisation anAuthorisation() {
        return anAuthorisation("AA1", 6);
    }

    private static Authorisation anAuthorisation(int ceiling) {
        return anAuthorisation("AA1", ceiling);
    }

    private static Authorisation anAuthorisation(String symbol) {
        return anAuthorisation(symbol, 6);
    }

    private static Authorisation anAuthorisation(String symbol, int ceiling) {
        return new Authorisation(symbol, ceiling, "phamm", SIGNED, "the lab runs three shifts");
    }

    @Test
    void theStandingCeilingsComeOffThePolicy() {
        DuplicationPolicy policy = new DuplicationPolicy();
        assertThat(policy.ceilingFor(sym("AA1"))).isEqualTo(4);
        assertThat(policy.ceilingFor(sym("AA"))).isEqualTo(2);
        assertThat(policy.ceilingFor(sym("ABC"))).isEqualTo(2);
        assertThat(policy.ceilingFor(sym("A"))).isEqualTo(1);
        assertThat(policy.ceilingFor(sym("GGM"))).isEqualTo(1);
    }

    @Test
    void aGrandMasterHasItsOwnCeilingApartFromTheMastersUnderIt() {
        DuplicationPolicy policy = new DuplicationPolicy()
                .with(ceilings -> ceilings.grandMasterLimit(3).masterLimit(5));
        assertThat(policy.ceilingFor(sym("B"))).isEqualTo(3);
        assertThat(policy.ceilingFor(sym("BA"))).isEqualTo(5);
        assertThat(policy.ceilingFor(sym("BA1"))).isEqualTo(4);
        assertThat(policy.ceilingFor(sym("GGM"))).isEqualTo(1);
        assertThat(new DuplicationPolicy().with(ceilings -> ceilings)).isEqualTo(DuplicationPolicy.DEFAULT);
    }

    @Test
    void aShopCanSetItsOwnCeilings() {
        DuplicationPolicy policy = new DuplicationPolicy()
                .with(ceilings -> ceilings.changeKeyLimit(8).masterLimit(3));
        assertThat(policy.ceilingFor(sym("AA1"))).isEqualTo(8);
        assertThat(policy.ceilingFor(sym("AB"))).isEqualTo(3);
    }

    static Stream<Supplier<DuplicationPolicy>> aCeilingOfNothingIsRefused() {
        return Stream.of(
                () -> new DuplicationPolicy().with(ceilings -> ceilings.changeKeyLimit(0)),
                () -> new DuplicationPolicy().with(ceilings -> ceilings.topKeyLimit(0)),
                () -> new DuplicationPolicy().with(ceilings -> ceilings.grandMasterLimit(0)));
    }

    @ParameterizedTest
    @MethodSource
    void aCeilingOfNothingIsRefused(Supplier<DuplicationPolicy> broken) {
        assertThatThrownBy(broken::get).isInstanceOf(InputError.class);
    }

    @Test
    void anAuthorisationSaysWhyAndRaisesSomething() {
        assertThatThrownBy(() -> new Authorisation("AA1", 6, "phamm", SIGNED, "  "))
                .isInstanceOf(InputError.class);
        assertThatThrownBy(() -> anAuthorisation(0)).isInstanceOf(InputError.class);
    }

    @Test
    void anAuthorisationRaisesTheCeiling() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation());
        assertThat(register.ceilingFor(sym("AA1"))).isEqualTo(6);
        assertThat(register.isAuthorised(sym("AA1"))).isTrue();
    }

    @Test
    void aSymbolNobodySignedForKeepsThePolicyCeiling() {
        DuplicationRegister register = new DuplicationRegister();
        assertThat(register.ceilingFor(sym("AB1"))).isEqualTo(4);
        assertThat(register.isAuthorised(sym("AB1"))).isFalse();
        assertThat(register.authorisation("AB1")).isEmpty();
    }

    @Test
    void aCeilingCannotBeLoweredHere() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation(6));
        assertThatThrownBy(() -> register.authorise(anAuthorisation(5))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aCeilingCanBeRaisedAgain() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation(6));
        register.authorise(anAuthorisation(9));
        assertThat(register.ceilingFor(sym("AA1"))).isEqualTo(9);
    }

    @Test
    void nothingIsOverWhenTheCountsAreUnder() {
        DuplicationRegister register = new DuplicationRegister();
        assertThat(register.overruns(aLedger(Map.entry("AA1", 3)))).isEmpty();
    }

    @Test
    void aSymbolOverItsCeilingIsReported() {
        DuplicationRegister register = new DuplicationRegister();
        List<Overrun> found = register.overruns(aLedger(Map.entry("AA1", 6)));
        assertThat(found.get(0).symbol()).isEqualTo("AA1");
        assertThat(found.get(0).out()).isEqualTo(6);
        assertThat(found.get(0).overBy()).isEqualTo(2);
        assertThat(found.get(0).authorised()).isFalse();
    }

    @Test
    void anAuthorisedSymbolIsMeasuredAgainstItsOwnCeiling() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation(6));
        assertThat(register.overruns(aLedger(Map.entry("AA1", 6)))).isEmpty();
    }

    @Test
    void anAuthorisedSymbolCanStillGoOver() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation(6));
        List<Overrun> found = register.overruns(aLedger(Map.entry("AA1", 8)));
        assertThat(found.get(0).authorised()).isTrue();
        assertThat(found.get(0).describe()).contains("authorised ceiling 6");
    }

    @Test
    void theWorstOverrunComesFirst() {
        DuplicationRegister register = new DuplicationRegister();
        List<Overrun> found = register.overruns(aLedger(Map.entry("AA1", 8), Map.entry("AA", 4)));
        assertThat(found.stream().map(Overrun::symbol)).containsExactly("AA1", "AA");
    }

    @Test
    void aReturnedKeyIsNotCounted() {
        DuplicationRegister register = new DuplicationRegister();
        KeyLedger ledger = aLedger(Map.entry("AA1", 5));
        ledger.takeBack("41000", SIGNED);
        assertThat(register.overruns(ledger)).isEmpty();
    }

    @Test
    void theRoomLeftIsTheCeilingLessWhatIsOut() {
        DuplicationRegister register = new DuplicationRegister();
        KeyLedger ledger = aLedger(Map.entry("AA1", 3));
        assertThat(register.roomFor(sym("AA1"), ledger)).isEqualTo(1);
        assertThat(register.wouldExceed(sym("AA1"), ledger)).isFalse();
    }

    @Test
    void aSymbolAtItsCeilingHasNoRoom() {
        DuplicationRegister register = new DuplicationRegister();
        KeyLedger ledger = aLedger(Map.entry("AA1", 4));
        assertThat(register.roomFor(sym("AA1"), ledger)).isZero();
        assertThat(register.wouldExceed(sym("AA1"), ledger)).isTrue();
    }

    @Test
    void aSymbolOverItsCeilingHasNoNegativeRoom() {
        DuplicationRegister register = new DuplicationRegister();
        assertThat(register.roomFor(sym("AA1"), aLedger(Map.entry("AA1", 9)))).isZero();
    }

    @Test
    void theAuthorisationsComeBackInSymbolOrder() {
        DuplicationRegister register = new DuplicationRegister();
        register.authorise(anAuthorisation("AB1"));
        register.authorise(anAuthorisation("AA1"));
        assertThat(register.authorisations().stream().map(Authorisation::symbol))
                .containsExactly("AA1", "AB1");
    }

    @Test
    void mastersNeedARestrictedBlankAndChangeKeysDoNot() {
        DuplicationPolicy policy = new DuplicationPolicy();
        assertThat(Duplication.restrictedNeeded(policy, sym("AA"))).isTrue();
        assertThat(Duplication.restrictedNeeded(policy, sym("GGM"))).isTrue();
        assertThat(Duplication.restrictedNeeded(policy, sym("AA1"))).isFalse();
    }

    @Test
    void aShopCanRestrictEverything() {
        DuplicationPolicy policy = new DuplicationPolicy().with(ceilings -> ceilings.restrictedOnlyForMasters(false));
        assertThat(Duplication.restrictedNeeded(policy, sym("AA1"))).isTrue();
    }

    @Test
    void theStockCheckListsWhatNeedsARestrictedBlank() {
        List<KeySymbol> symbols = List.of(sym("GGM"), sym("A"), sym("AA1"));
        assertThat(Duplication.unstamped(symbols, new DuplicationPolicy())).containsExactly("GGM", "A");
    }
}
