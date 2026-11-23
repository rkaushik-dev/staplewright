package io.github.rkaushikdev.staplewright.settings;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.DepositRates;
import io.github.rkaushikdev.staplewright.control.NoticePolicy;
import io.github.rkaushikdev.staplewright.control.TransferPolicy;
import io.github.rkaushikdev.staplewright.keys.StampPolicy;
import io.github.rkaushikdev.staplewright.rekey.RateCard;
import io.github.rkaushikdev.staplewright.rekey.ScopePolicy;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import java.util.List;

/**
 * The whole of shop policy, gathered: every number the shop changes, in one place instead of
 * nine.
 *
 * <p>The modules that use these take them as arguments rather than reaching for a global, and
 * every group keeps its own defaults.
 *
 * @param cutting what a cut sequence may look like
 * @param deposits what a deposit costs
 * @param notices when each step of the notice ladder happens
 * @param scope what the shop does about missing keys
 * @param rates what the shop charges
 * @param boxes what goes in a box on a wall
 * @param transfers how long a handover claim stands
 * @param stamping what goes on the brass
 * @param quarantineYears how long a retired cut stays out of the array
 * @param progressionStep how far apart progressed depths sit
 */
public record ShopSettings(
        CuttingRules cutting,
        DepositRates deposits,
        NoticePolicy notices,
        ScopePolicy scope,
        RateCard rates,
        BoxPolicy boxes,
        TransferPolicy transfers,
        StampPolicy stamping,
        int quarantineYears,
        int progressionStep) {

    public ShopSettings {
        if (quarantineYears < 1) {
            throw new InputError("a quarantine window is at least a year");
        }
        if (progressionStep < 1) {
            throw new InputError("a progression step is at least one");
        }
    }

    /** Every group at its own default, a seven year quarantine and a step of two. */
    public ShopSettings() {
        this(
                new CuttingRules(),
                new DepositRates(),
                new NoticePolicy(),
                new ScopePolicy(),
                new RateCard(),
                new BoxPolicy(),
                new TransferPolicy(),
                new StampPolicy(),
                7,
                2);
    }

    /** The same settings with the deposits changed and nothing else. */
    public ShopSettings withDeposits(int changeKeyCents, int masterCents) {
        return new ShopSettings(
                cutting,
                new DepositRates(changeKeyCents, masterCents),
                notices,
                scope,
                rates,
                boxes,
                transfers,
                stamping,
                quarantineYears,
                progressionStep);
    }

    /** The same settings with the cutting rules changed and nothing else. */
    public ShopSettings withCutting(CuttingRules value) {
        return new ShopSettings(
                value, deposits, notices, scope, rates, boxes, transfers, stamping, quarantineYears, progressionStep);
    }

    /** The same settings with the scope policy changed and nothing else. */
    public ShopSettings withScope(ScopePolicy value) {
        return new ShopSettings(
                cutting, deposits, notices, value, rates, boxes, transfers, stamping, quarantineYears, progressionStep);
    }

    /** The same settings with the stamping policy changed and nothing else. */
    public ShopSettings withStamping(StampPolicy value) {
        return new ShopSettings(
                cutting, deposits, notices, scope, rates, boxes, transfers, value, quarantineYears, progressionStep);
    }

    /** What the settings are, one line a group, for the top of a report. */
    public List<String> describe() {
        return List.of(
                "macs policy: runs to " + cutting.maxRun() + ", " + cutting.minDistinct() + " depths minimum",
                "deposits: " + deposits.describe(),
                "notices: reminder at " + notices.reminderDays() + " days, write off at " + notices.writeOffDays(),
                "rekey: cap " + scope.cylinderCap().map(String::valueOf).orElse("None") + ", strict repin "
                        + (scope.recutAllOnRepin() ? "True" : "False"),
                "boxes: no wider than level " + boxes.lowestLevel() + ", checked every " + boxes.checkDays() + " days",
                "handovers: confirmed inside " + transfers.confirmDays() + " days",
                "stamping: change keys carry " + stamping.changeKeys().value() + ", masters carry "
                        + stamping.masters().value()
                        + stamping.grandMasters()
                                .map(marking -> ", grand masters carry " + marking.value())
                                .orElse(""),
                "quarantine: " + quarantineYears + " years",
                "progression: " + progressionStep + " steps");
    }
}
