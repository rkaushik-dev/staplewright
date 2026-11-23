package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.List;
import org.junit.jupiter.api.Test;

class StampingTest {

    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");
    private static final KeySymbol MASTER = KeySymbol.parse("AA");
    private static final KeySymbol TOP = KeySymbol.parse("GGM");
    private static final KeySymbol GRAND_MASTER = KeySymbol.parse("A");
    private static final StampPolicy STANDING = new StampPolicy();

    private static BlindCodeBook aBook() {
        return new BlindCodeBook();
    }

    private static List<TaggedKey> oneChangeKey() {
        return List.of(new TaggedKey("41207", CHANGE_KEY));
    }

    @Test
    void theGrandMastersFollowTheMastersUnlessTheyAreGivenTheirOwnMarking() {
        assertThat(STANDING.markingFor(GRAND_MASTER)).isSameAs(Marking.BLIND);
        assertThat(STANDING.markingFor(TOP)).isSameAs(Marking.BLIND);
        StampPolicy symbols = new StampPolicy().with(stamps -> stamps.masters(Marking.SYMBOL));
        assertThat(symbols.markingFor(GRAND_MASTER)).isSameAs(Marking.SYMBOL);
        StampPolicy tagOnly = symbols.with(stamps -> stamps.grandMasters(Marking.TAG));
        assertThat(tagOnly.markingFor(GRAND_MASTER)).isSameAs(Marking.TAG);
        assertThat(tagOnly.markingFor(TOP)).isSameAs(Marking.TAG);
        assertThat(tagOnly.markingFor(MASTER)).isSameAs(Marking.SYMBOL);
        assertThat(tagOnly.markingFor(CHANGE_KEY)).isSameAs(Marking.SYMBOL);
    }

    @Test
    void aGrandMasterStampedByTagCarriesNoCodeTheCounterCouldRead() {
        BlindCodeBook book = aBook();
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.grandMasters(Marking.TAG));
        List<Stamp> found = Stamping.stampRun(
                List.of(new TaggedKey("41301", GRAND_MASTER), new TaggedKey("41211", MASTER)), policy, book);
        assertThat(found).extracting(Stamp::tag).containsExactly("41211", "41301");
        assertThat(found.get(0).marking()).isSameAs(Marking.BLIND);
        assertThat(found.get(1).marking()).isSameAs(Marking.TAG);
        assertThat(found.get(1).text()).isEqualTo("41301");
        assertThat(book.size()).isEqualTo(1);
        assertThat(new StampPolicy().with(stamps -> stamps)).isEqualTo(STANDING);
    }

    @Test
    void aBowHasToHoldSomething() {
        assertThatThrownBy(() -> new StampPolicy().with(stamps -> stamps.bowCharacters(3)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aChangeKeyGetsItsSymbolByDefault() {
        assertThat(STANDING.markingFor(CHANGE_KEY)).isSameAs(Marking.SYMBOL);
    }

    @Test
    void aMasterGoesBlindByDefault() {
        assertThat(STANDING.markingFor(MASTER)).isSameAs(Marking.BLIND);
    }

    @Test
    void aShopCanStampEverythingTheSameWay() {
        StampPolicy policy =
                new StampPolicy().with(stamps -> stamps.changeKeys(Marking.TAG).masters(Marking.TAG));
        assertThat(policy.markingFor(MASTER)).isSameAs(Marking.TAG);
    }

    @Test
    void aStampGoesOnATaggedKey() {
        assertThatThrownBy(() -> Stamping.stampFor(" ", CHANGE_KEY, STANDING)).isInstanceOf(InputError.class);
    }

    @Test
    void aChangeKeyCarriesItsSymbol() {
        Stamp found = Stamping.stampFor("41207", CHANGE_KEY, STANDING);
        assertThat(found.text()).isEqualTo("AA1");
        assertThat(found.isBlind()).isFalse();
    }

    @Test
    void aMasterCarriesANumberFromTheBook() {
        BlindCodeBook book = aBook();
        Stamp found = Stamping.stampFor("41211", MASTER, STANDING, book);
        assertThat(found.text()).isEqualTo("4000");
        assertThat(found.isBlind()).isTrue();
    }

    @Test
    void aBlindStampWithoutTheBookIsRefused() {
        assertThatThrownBy(() -> Stamping.stampFor("41211", MASTER, STANDING)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyCanCarryItsTagInstead() {
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.changeKeys(Marking.TAG));
        assertThat(Stamping.stampFor("41207", CHANGE_KEY, policy).text()).isEqualTo("41207");
    }

    @Test
    void aKeyCanCarryNothing() {
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.changeKeys(Marking.NOTHING));
        Stamp found = Stamping.stampFor("41207", CHANGE_KEY, policy);
        assertThat(found.text()).isEmpty();
        assertThat(found.describe()).startsWith("41207 blank");
    }

    @Test
    void somethingTooLongForTheBowIsRefused() {
        StampPolicy policy =
                new StampPolicy().with(stamps -> stamps.changeKeys(Marking.TAG).bowCharacters(4));
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> Stamping.stampFor("412075555", CHANGE_KEY, policy));
        assertThat(caught.facts()).containsEntry("room", 4);
    }

    @Test
    void aStampReadsBackAsOneLine() {
        Stamp found = Stamping.stampFor("41207", CHANGE_KEY, STANDING);
        assertThat(found.describe()).isEqualTo("41207 AA1 (the key symbol)");
    }

    @Test
    void aBlindSeriesStartsWhereItWasTold() {
        BlindCodeBook book = new BlindCodeBook(9000, 3);
        assertThat(book.assign(MASTER)).isEqualTo("9000");
        assertThat(book.assign(TOP)).isEqualTo("9003");
    }

    @Test
    void aSeriesStartsAtOneOrLater() {
        assertThatThrownBy(() -> new BlindCodeBook(0, BlindCodeBook.STEP)).isInstanceOf(InputError.class);
    }

    @Test
    void aSeriesStepsForward() {
        assertThatThrownBy(() -> new BlindCodeBook(BlindCodeBook.START, 0)).isInstanceOf(InputError.class);
    }

    @Test
    void aSymbolKeepsTheCodeItWasGiven() {
        BlindCodeBook book = aBook();
        String first = book.assign(MASTER);
        assertThat(book.assign(MASTER)).isEqualTo(first);
    }

    @Test
    void theCodeForASymbolComesBack() {
        BlindCodeBook book = aBook();
        book.assign(MASTER);
        assertThat(book.codeFor(MASTER)).isEqualTo("4000");
    }

    @Test
    void aSymbolNobodyStampedHasNoCode() {
        assertThatThrownBy(() -> aBook().codeFor(MASTER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aNumberOnAKeyComesBackAsASymbol() {
        BlindCodeBook book = aBook();
        String code = book.assign(MASTER);
        assertThat(Stamping.identify(code, book)).isEqualTo("AA");
    }

    @Test
    void aNumberWithSpaceRoundItStillReads() {
        BlindCodeBook book = aBook();
        String code = book.assign(MASTER);
        assertThat(book.symbolFor("  " + code + " ")).isEqualTo("AA");
    }

    @Test
    void aNumberNobodyStampedIsA404() {
        assertThatThrownBy(() -> Stamping.identify("9999", aBook())).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aStoredPairComesBack() {
        BlindCodeBook book = aBook();
        book.load("AA", "4000");
        assertThat(book.symbolFor("4000")).isEqualTo("AA");
    }

    @Test
    void aStoredPairMovesTheSeriesPastIt() {
        BlindCodeBook book = aBook();
        book.load("AA", "4021");
        assertThat(book.assign(TOP)).isEqualTo("4028");
    }

    @Test
    void aCodeCannotBeReusedForAnotherSymbol() {
        BlindCodeBook book = aBook();
        book.load("AA", "4000");
        assertThatThrownBy(() -> book.load("AB", "4000")).isInstanceOf(ConflictError.class);
    }

    @Test
    void loadingTheSamePairTwiceIsFine() {
        BlindCodeBook book = aBook();
        book.load("AA", "4000");
        book.load("AA", "4000");
        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void theBookCountsAndContains() {
        BlindCodeBook book = aBook();
        book.assign(MASTER);
        assertThat(book.size()).isEqualTo(1);
        assertThat(book.contains("4000")).isTrue();
        assertThat(book).containsExactly("4000");
    }

    @Test
    void thePairsComeBackSymbolFirst() {
        BlindCodeBook book = aBook();
        book.assign(TOP);
        book.assign(MASTER);
        assertThat(book.codes()).containsExactly(new BlindCode("4007", "AA"), new BlindCode("4000", "GGM"));
    }

    @Test
    void aRunComesBackInTagOrder() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(
                List.of(new TaggedKey("41211", MASTER), new TaggedKey("41207", CHANGE_KEY)), STANDING, book);
        assertThat(found.stream().map(Stamp::tag)).containsExactly("41207", "41211");
    }

    @Test
    void aRunGivesOneCodePerSymbol() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(
                List.of(new TaggedKey("41211", MASTER), new TaggedKey("41212", MASTER)), STANDING, book);
        assertThat(found.get(0).text()).isEqualTo(found.get(1).text());
    }

    @Test
    void anEmptySheetSaysSo() {
        assertThat(Stamping.stampSheet(List.of(), STANDING)).containsExactly("nothing to stamp");
    }

    @Test
    void theSheetCountsTheKeys() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), STANDING, book);
        List<String> lines = Stamping.stampSheet(found, STANDING);
        assertThat(lines.get(0)).isEqualTo("1 keys to stamp");
    }

    @Test
    void theSheetAddsTheDoNotDuplicateLine() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), STANDING, book);
        assertThat(Stamping.stampSheet(found, STANDING).get(1)).contains("DND");
    }

    @Test
    void aShopThatDoesNotBotherLeavesItOff() {
        BlindCodeBook book = aBook();
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.addDoNotDuplicate(false));
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), policy, book);
        assertThat(Stamping.stampSheet(found, policy).get(1)).doesNotContain("DND");
    }

    @Test
    void theSheetSaysHowManyAreBlind() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(List.of(new TaggedKey("41211", MASTER)), STANDING, book);
        List<String> lines = Stamping.stampSheet(found, STANDING);
        assertThat(lines.get(lines.size() - 1)).startsWith("1 of them are blind");
    }

    @Test
    void aSheetWithNothingBlindSaysNothingAboutIt() {
        BlindCodeBook book = aBook();
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), STANDING, book);
        assertThat(Stamping.stampSheet(found, STANDING)).noneMatch(line -> line.contains("blind"));
    }

    @Test
    void theUnstampedKeysAreNamed() {
        StampPolicy policy = new StampPolicy().with(stamps -> stamps.changeKeys(Marking.NOTHING));
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), policy, aBook());
        assertThat(Stamping.unstamped(found)).containsExactly("41207");
    }

    @Test
    void aStampedKeyIsNotOnThatList() {
        List<Stamp> found = Stamping.stampRun(oneChangeKey(), STANDING, aBook());
        assertThat(Stamping.unstamped(found)).isEmpty();
    }
}
