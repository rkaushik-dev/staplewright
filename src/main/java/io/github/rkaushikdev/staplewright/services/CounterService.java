package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.DepositRates;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.EntryKind;
import io.github.rkaushikdev.staplewright.control.KeyState;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.RequestState;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.KeyRepository;
import io.github.rkaushikdev.staplewright.persistence.Numbering;
import io.github.rkaushikdev.staplewright.persistence.RequestRepository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.models.AuthorizerRow;
import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The counter: a request comes in, a signature happens, a key goes out.
 *
 * <p>Everything here is one transaction. Issuing a key writes the key row, takes the deposit, marks
 * the request filled and burns a tag number, and any of those happening without the others is a
 * mess somebody finds in an audit two years later.
 *
 * <p>The tag number is the reason this cannot be split. It comes off a gapless counter, so it has
 * to be taken inside the same transaction as the key row it goes on. Take it outside and a rolled
 * back issue leaves a hole in the tag series, which is the one thing the series exists to prevent.
 */
public final class CounterService {

    /** The series every key tag comes off. */
    public static final String TAG_SERIES = "key tags";

    /** The series every request number comes off. */
    public static final String REQUEST_SERIES = "key requests";

    private final Session session;
    private final Numbering numbering;
    private final RequestRepository requests;
    private final KeyRepository keys;
    private final DepositRates rates;

    public CounterService(Session session) {
        this(session, new DepositRates());
    }

    public CounterService(Session session, DepositRates rates) {
        this.session = session;
        this.numbering = new Numbering(session);
        this.requests = new RequestRepository(session);
        this.keys = new KeyRepository(session);
        this.rates = rates;
    }

    public Session session() {
        return session;
    }

    /** The requests on file. */
    public RequestRepository requests() {
        return requests;
    }

    /** The key ledger. */
    public KeyRepository keys() {
        return keys;
    }

    /** Everybody on the register and every signature on file, as they stand now. */
    public Directory directory() {
        return ControlMappers.directoryFromRows(session.all(KeyholderRow.class), session.all(AuthorizerRow.class));
    }

    /** Raise a request for an ordinary key. */
    public KeyRequestRow raiseRequest(String keyholder, String symbol, String building, String reason, LocalDate when) {
        return raiseRequest(keyholder, symbol, building, reason, when, false);
    }

    /**
     * Raise a request for one key, numbered off the request series.
     *
     * @throws NotFoundError when the person is not on the register
     * @throws InputError when no reason is given
     */
    public KeyRequestRow raiseRequest(
            String keyholder, String symbol, String building, String reason, LocalDate when, boolean isMaster) {
        directory().person(keyholder);
        if (Text.isBlank(reason)) {
            throw new InputError("a request needs a reason", Facts.of("keyholder", keyholder));
        }
        String number = numbering.next(REQUEST_SERIES);
        KeyRequestRow row = new KeyRequestRow(number, keyholder, symbol, building, when);
        row.setReason(reason);
        row.setState(RequestState.SUBMITTED.value());
        row.setMaster(isMaster);
        requests.add(row);
        session.flush();
        return row;
    }

    /** Sign a request off, with no note. */
    public KeyRequestRow approve(String number, String signer, LocalDate when) {
        return approve(number, signer, when, "");
    }

    /**
     * Sign a request off. The person has to be allowed keys, the signature has to reach the
     * department and the building, and a master needs a signature that may approve one.
     */
    public KeyRequestRow approve(String number, String signer, LocalDate when, String note) {
        KeyRequestRow row = submitted(number);
        Directory directory = directory();
        Keyholder person = directory.person(row.getKeyholder());
        if (!person.mayHoldKeys(when)) {
            throw new ConflictError(
                    "that keyholder may not hold keys",
                    Facts.of("number", number, "standing", person.standing().value()));
        }
        if (!directory.mayAuthorize(signer, person.department(), row.getBuilding(), when)) {
            throw new ConflictError(
                    "that signature does not reach this request", Facts.of("number", number, "signer", signer));
        }
        if (row.isMaster() && !directory.authorizer(signer).mayApproveMasters()) {
            throw new ConflictError("that signature is not allowed to approve a master", Facts.of("number", number));
        }
        decide(row, RequestState.APPROVED, signer, when, note);
        return row;
    }

    /**
     * Turn a request down.
     *
     * @throws InputError when the note does not say why
     */
    public KeyRequestRow deny(String number, String signer, LocalDate when, String note) {
        KeyRequestRow row = submitted(number);
        if (Text.isBlank(note)) {
            throw new InputError("a denial says why", Facts.of("number", number));
        }
        decide(row, RequestState.DENIED, signer, when, note);
        return row;
    }

    /** Hand the key over at the standing rate, with no due date. */
    public IssuedKeyRow issue(String number, LocalDate when) {
        return issue(number, when, Optional.empty(), Optional.empty());
    }

    /**
     * Hand the key over, take the deposit and close the request.
     *
     * @param dueOn the day the key is due back, if it is
     * @param deposit the deposit in cents, or the standing rate for the key when empty; nothing
     *     taken writes no deposit line
     */
    public IssuedKeyRow issue(String number, LocalDate when, Optional<LocalDate> dueOn, Optional<Integer> deposit) {
        KeyRequestRow row = requests.find(number)
                .orElseThrow(() -> new NotFoundError("no such request", Facts.of("number", number)));
        if (!row.getState().equals(RequestState.APPROVED.value())) {
            throw new ConflictError(
                    "only an approved request gets filled", Facts.of("number", number, "state", row.getState()));
        }
        Keyholder person = directory().person(row.getKeyholder());
        if (!person.mayHoldKeys(when)) {
            throw new ConflictError(
                    "that keyholder may not hold keys",
                    Facts.of("number", number, "standing", person.standing().value()));
        }
        int cents = deposit.orElseGet(() -> rates.forKey(KeySymbol.parse(row.getSymbol()), row.isMaster()));
        String tag = numbering.next(TAG_SERIES);
        IssuedKeyRow key = new IssuedKeyRow(tag, row.getSymbol(), row.getKeyholder(), when);
        key.setDueOn(dueOn.orElse(null));
        key.setDepositCents(cents);
        keys.add(key);
        if (cents != 0) {
            DepositLineRow line = new DepositLineRow(tag, row.getKeyholder(), EntryKind.CHARGE.value(), cents, when);
            line.setNote("issued against " + number);
            session.add(line);
        }
        row.setState(RequestState.FILLED.value());
        session.flush();
        return key;
    }

    /** A key comes back, with no note. */
    public IssuedKeyRow takeBack(String tag, LocalDate when) {
        return takeBack(tag, when, "");
    }

    /** A key comes back, and the deposit goes with it. */
    public IssuedKeyRow takeBack(String tag, LocalDate when, String note) {
        IssuedKeyRow key = out(tag);
        close(key, KeyState.RETURNED, when, note);
        settle(key, EntryKind.REFUND, when, note);
        session.flush();
        return key;
    }

    /** A key is gone, with no note. */
    public IssuedKeyRow reportLost(String tag, LocalDate when) {
        return reportLost(tag, when, "");
    }

    /** A key is gone. The deposit stays with the shop. */
    public IssuedKeyRow reportLost(String tag, LocalDate when, String note) {
        IssuedKeyRow key = out(tag);
        close(key, KeyState.LOST, when, note);
        settle(key, EntryKind.FORFEIT, when, note.isEmpty() ? "key never came back" : note);
        session.flush();
        return key;
    }

    /** Open the counters this service walks. Called once, at setup. */
    public void openSeries() {
        numbering.openSeries(TAG_SERIES, "", 5, 41000);
        numbering.openSeries(REQUEST_SERIES, "R-", 4, 1);
    }

    private void decide(KeyRequestRow row, RequestState state, String signer, LocalDate when, String note) {
        row.setState(state.value());
        row.setDecidedOn(when);
        row.setDecidedBy(signer);
        row.setNote(note);
        session.flush();
    }

    private static void close(IssuedKeyRow key, KeyState state, LocalDate when, String note) {
        key.setState(state.value());
        key.setClosedOn(when);
        key.setNote(note);
    }

    private void settle(IssuedKeyRow key, EntryKind kind, LocalDate when, String note) {
        if (key.getDepositCents() == 0) {
            return;
        }
        List<DepositLineRow> lines = session.list(
                "select d from DepositLineRow d where d.tag = ?1 order by d.id", DepositLineRow.class, key.getTag());
        if (lines.stream().anyMatch(line -> !line.getKind().equals(EntryKind.CHARGE.value()))) {
            throw new ConflictError("that deposit is already settled", Facts.of("tag", key.getTag()));
        }
        DepositLineRow line =
                new DepositLineRow(key.getTag(), key.getKeyholder(), kind.value(), key.getDepositCents(), when);
        line.setNote(note);
        session.add(line);
    }

    private KeyRequestRow submitted(String number) {
        KeyRequestRow row = requests.find(number)
                .orElseThrow(() -> new NotFoundError("no such request", Facts.of("number", number)));
        if (!row.getState().equals(RequestState.SUBMITTED.value())) {
            throw new ConflictError(
                    "only a submitted request can be decided", Facts.of("number", number, "state", row.getState()));
        }
        return row;
    }

    private IssuedKeyRow out(String tag) {
        IssuedKeyRow key =
                keys.find(tag).orElseThrow(() -> new NotFoundError("no such key in the ledger", Facts.of("tag", tag)));
        if (!key.isOut()) {
            throw new ConflictError("that key is not out", Facts.of("tag", tag, "state", key.getState()));
        }
        return key;
    }
}
