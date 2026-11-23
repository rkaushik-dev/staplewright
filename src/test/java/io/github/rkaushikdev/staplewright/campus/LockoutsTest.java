package io.github.rkaushikdev.staplewright.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LockoutsTest {

    private static final LocalDateTime CALLED = LocalDateTime.of(2025, 4, 8, 14, 12);
    private static final LocalDateTime ATTENDED = LocalDateTime.of(2025, 4, 8, 14, 31);
    private static final LocalDateTime CLOSED = LocalDateTime.of(2025, 4, 8, 14, 35);
    private static final LocalDateTime NIGHT = LocalDateTime.of(2025, 4, 8, 22, 40);

    private static Lockout aCall() {
        return aCall("L-001");
    }

    private static Lockout aCall(String number) {
        return new Lockout(number, "MOO-214", "kmaki", CALLED);
    }

    private static Lockout aCall(UnaryOperator<Lockout.Builder> overrides) {
        return aCall("L-001", overrides);
    }

    private static Lockout aCall(String number, UnaryOperator<Lockout.Builder> overrides) {
        return overrides.apply(aCall(number).toBuilder()).build();
    }

    private static LockoutLog aLog() {
        LockoutLog log = new LockoutLog();
        log.log(aCall());
        return log;
    }

    private static LockoutLog attended(LockoutLog log) {
        return attended(log, "L-001");
    }

    private static LockoutLog attended(LockoutLog log, String number) {
        log.attend(number, "jkoivu", ATTENDED);
        return log;
    }

    private static List<String> numbers(Iterable<Lockout> calls) {
        return StreamSupport.stream(calls.spliterator(), false)
                .map(Lockout::number)
                .toList();
    }

    @Test
    void aCallNeedsANumber() {
        assertThatThrownBy(() -> aCall(" ")).isInstanceOf(InputError.class);
    }

    @Test
    void aNewCallIsOpen() {
        Lockout call = aLog().call("L-001");
        assertThat(call.state()).isSameAs(CallState.LOGGED);
        assertThat(call.isOpen()).isTrue();
        assertThat(call.responseMinutes()).isEmpty();
    }

    @Test
    void aCallDescribesItselfForTheLog() {
        assertThat(aLog().call("L-001").describe()).startsWith("L-001 MOO-214 kmaki");
    }

    @Test
    void aCallCannotBeLoggedAlreadyAttended() {
        assertThatThrownBy(() -> new LockoutLog().log(aCall(call -> call.state(CallState.ATTENDED))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void attendingRecordsWhoWentAndWhen() {
        Lockout call = attended(aLog()).call("L-001");
        assertThat(call.technician()).isEqualTo("jkoivu");
        assertThat(call.responseMinutes()).contains(19);
    }

    @Test
    void somebodyHasToAttend() {
        LockoutLog log = aLog();
        assertThatThrownBy(() -> log.attend("L-001", "  ", ATTENDED)).isInstanceOf(InputError.class);
    }

    @Test
    void nobodyAttendedBeforeTheCall() {
        LockoutLog log = aLog();
        assertThatThrownBy(() -> log.attend("L-001", "jkoivu", LocalDateTime.of(2025, 4, 8, 13, 0)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCallCannotBeAttendedTwice() {
        LockoutLog log = attended(aLog());
        assertThatThrownBy(() -> log.attend("L-001", "phamm", ATTENDED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void openingTheDoorRecordsHowTheyWereIdentified() {
        LockoutLog log = attended(aLog());
        Lockout call = log.openDoor("L-001", Identification.CAMPUS_CARD, CLOSED);
        assertThat(call.state()).isSameAs(CallState.OPENED);
        assertThat(call.identifiedBy()).isSameAs(Identification.CAMPUS_CARD);
        assertThat(call.isOpen()).isFalse();
    }

    @Test
    void anUnidentifiedPersonDoesNotGetLetIn() {
        LockoutLog log = attended(aLog());
        assertThatThrownBy(() -> log.openDoor("L-001", Identification.NONE, CLOSED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aDoorCannotBeOpenedBeforeAnybodyAttends() {
        LockoutLog log = aLog();
        assertThatThrownBy(() -> log.openDoor("L-001", Identification.CAMPUS_CARD, CLOSED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aRefusalSaysWhy() {
        LockoutLog log = attended(aLog());
        assertThatThrownBy(() -> log.refuse("L-001", CLOSED, " ")).isInstanceOf(InputError.class);
        Lockout call = log.refuse("L-001", CLOSED, "not on the roster and nobody would vouch");
        assertThat(call.state()).isSameAs(CallState.REFUSED);
    }

    @Test
    void aCallCanBeWithdrawnBeforeAnybodyGoes() {
        Lockout call = aLog().withdraw("L-001", CLOSED, "found the key in a coat");
        assertThat(call.state()).isSameAs(CallState.WITHDRAWN);
        assertThat(call.isOpen()).isFalse();
    }

    @Test
    void aClosedCallCannotBeWithdrawn() {
        LockoutLog log = attended(aLog());
        log.openDoor("L-001", Identification.ROSTER, CLOSED);
        assertThatThrownBy(() -> log.withdraw("L-001", CLOSED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aCallThatIsNotThere() {
        assertThatThrownBy(() -> aLog().call("L-999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aDaytimeCallIsNotAfterHours() {
        assertThat(aLog().call("L-001").afterHours()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 22, 2, 6})
    void anEveningOrNightCallIsAfterHours(int hour) {
        Lockout call = aCall(item -> item.calledAt(LocalDateTime.of(2025, 4, 8, hour, 0)));
        assertThat(call.afterHours()).isTrue();
    }

    @Test
    void theAfterHoursListIsOnlyThose() {
        LockoutLog log = aLog();
        log.log(aCall("L-002", call -> call.calledAt(NIGHT)));
        assertThat(numbers(log.afterHours())).containsExactly("L-002");
    }

    @Test
    void theOpenCallsAreTheOnesNobodyClosed() {
        LockoutLog log = attended(aLog());
        log.log(aCall("L-002"));
        log.openDoor("L-001", Identification.ROSTER, CLOSED);
        assertThat(numbers(log.openCalls())).containsExactly("L-002");
    }

    @Test
    void callsComeBackByDoorAndByPerson() {
        LockoutLog log = aLog();
        log.log(aCall("L-002", call -> call.opening("MOO-216").keyholder("dsalo")));
        assertThat(numbers(log.forOpening("MOO-214"))).containsExactly("L-001");
        assertThat(numbers(log.forKeyholder("dsalo"))).containsExactly("L-002");
    }

    @Test
    void theWorstDoorComesFirst() {
        LockoutLog log = aLog();
        log.log(aCall("L-002"));
        log.log(aCall("L-003", call -> call.opening("MOO-216")));
        Map<String, Integer> counts = log.countsByOpening();
        assertThat(counts.keySet()).containsExactly("MOO-214", "MOO-216");
        assertThat(counts).containsEntry("MOO-214", 2);
    }

    @Test
    void aRepeatCallerIsNamed() {
        LockoutLog log = aLog();
        log.log(aCall("L-002"));
        log.log(aCall("L-003"));
        log.log(aCall("L-004", call -> call.keyholder("dsalo")));
        assertThat(log.repeatCallers()).containsExactly("kmaki");
        assertThat(log.repeatCallers(1)).containsExactly("dsalo", "kmaki");
    }

    @Test
    void repeatCallsAreCountedFromTheStartOfTheTerm() {
        LockoutLog log = aLog();
        log.log(aCall("L-002", call -> call.calledAt(LocalDateTime.of(2025, 9, 3, 9, 0))));
        log.log(aCall("L-003", call -> call.calledAt(LocalDateTime.of(2025, 9, 20, 9, 0))));
        log.log(aCall("L-004", call -> call.keyholder("dsalo").calledAt(LocalDateTime.of(2025, 9, 1, 0, 0))));
        log.log(aCall("L-005", call -> call.keyholder("dsalo").calledAt(LocalDateTime.of(2025, 10, 1, 8, 0))));
        LocalDate term = LocalDate.of(2025, 9, 1);
        assertThat(log.repeatCallers()).containsExactly("kmaki");
        assertThat(log.repeatCallers(3, term)).isEmpty();
        assertThat(log.repeatCallers(2, term)).containsExactly("dsalo", "kmaki");
        assertThat(log.repeatCallers(2, LocalDate.of(2025, 9, 2))).containsExactly("kmaki");
    }

    @Test
    void theDoorsAreCountedFromADayToo() {
        LockoutLog log = aLog();
        log.log(aCall("L-002"));
        log.log(aCall("L-003", call -> call.opening("MOO-216").calledAt(LocalDateTime.of(2025, 9, 3, 9, 0))));
        Map<String, Integer> autumn = log.countsByOpening(LocalDate.of(2025, 9, 1));
        assertThat(autumn).containsExactly(Map.entry("MOO-216", 1));
        assertThat(log.countsByOpening(LocalDate.of(2025, 4, 8))).containsEntry("MOO-214", 2);
        assertThat(log.countsByOpening(LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 13})
    void aWeekendCallIsAfterHoursAtAnyHour(int day) {
        Lockout midday = aCall(item -> item.calledAt(LocalDateTime.of(2025, 4, day, 12, 0)));
        assertThat(midday.afterHours()).isTrue();
    }

    @Test
    void aWeekdayMiddayCallIsStillInHours() {
        assertThat(aCall(item -> item.calledAt(LocalDateTime.of(2025, 4, 11, 12, 0)))
                        .afterHours())
                .isFalse();
        assertThat(aCall(item -> item.calledAt(LocalDateTime.of(2025, 4, 14, 7, 0)))
                        .afterHours())
                .isFalse();
    }

    @Test
    void theAverageResponseIsOverTheAttendedCalls() {
        LockoutLog log = attended(aLog());
        log.log(aCall("L-002"));
        log.attend("L-002", "jkoivu", LocalDateTime.of(2025, 4, 8, 14, 42));
        assertThat(log.averageResponse()).contains(24);
    }

    @Test
    void nothingAttendedHasNoAverage() {
        assertThat(aLog().averageResponse()).isEmpty();
    }

    @Test
    void theLogIsAContainer() {
        LockoutLog log = aLog();
        assertThat(log.contains("L-001")).isTrue();
        assertThat(log.contains("L-999")).isFalse();
        assertThat(log.size()).isEqualTo(1);
        assertThat(numbers(log)).containsExactly("L-001");
    }
}
