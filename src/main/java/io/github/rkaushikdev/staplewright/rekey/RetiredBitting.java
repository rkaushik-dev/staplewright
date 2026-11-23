package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.time.LocalDate;
import java.time.Period;

/**
 * One cut that is out of service, and the story behind it.
 *
 * @param bitting the cut
 * @param reason why it left service
 * @param retiredOn the day it did
 * @param symbol the symbol it was cut for, when it had one
 * @param note anything else worth writing down
 */
public record RetiredBitting(
        Bitting bitting, RetirementReason reason, LocalDate retiredOn, String symbol, String note) {

    /** A retirement with no symbol and nothing written about it. */
    public RetiredBitting(Bitting bitting, RetirementReason reason, LocalDate retiredOn) {
        this(bitting, reason, retiredOn, "", "");
    }

    /** Whether the cut is out of service for good. */
    public boolean isPermanent() {
        return reason.isPermanent();
    }

    /**
     * Whether a cut that is not out for good has sat out long enough to come back: at least
     * {@code afterYears} whole years between the day it was retired and {@code today}.
     */
    public boolean releasedBy(LocalDate today, int afterYears) {
        return !isPermanent() && Period.between(retiredOn, today).getYears() >= afterYears;
    }
}
