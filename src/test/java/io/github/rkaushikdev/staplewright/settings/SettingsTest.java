package io.github.rkaushikdev.staplewright.settings;

import static io.github.rkaushikdev.staplewright.settings.Settings.DEFAULTS;
import static io.github.rkaushikdev.staplewright.settings.Settings.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.DepositRates;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.Marking;
import io.github.rkaushikdev.staplewright.util.Records;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsTest {

    /** A mapping of prefixed variable names, from alternating names and values. */
    static Map<String, String> env(Object... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            out.put(PREFIX + pairs[index], String.valueOf(pairs[index + 1]));
        }
        return out;
    }

    @Test
    void anEmptyMappingGivesTheDefaults() {
        assertThat(Settings.fromMapping(Map.of())).isEqualTo(DEFAULTS);
    }

    @Test
    void aSettingAbsentFromTheMappingKeepsItsDefault() {
        ShopSettings found = Settings.fromMapping(env("MAX_RUN", 5));
        assertThat(found.cutting().maxRun()).isEqualTo(5);
        assertThat(found.cutting().minDistinct()).isEqualTo(DEFAULTS.cutting().minDistinct());
    }

    @Test
    void theDepositsComeOffTheMapping() {
        ShopSettings found = Settings.fromMapping(env("DEPOSIT_CHANGE_CENTS", 1000, "DEPOSIT_MASTER_CENTS", 9000));
        assertThat(found.deposits().forKey(KeySymbol.parse("AA1"), false)).isEqualTo(1000);
        assertThat(found.deposits().forKey(KeySymbol.parse("AA"), true)).isEqualTo(9000);
        assertThat(found.deposits().forKey(KeySymbol.parse("A"), true)).isEqualTo(9000);
    }

    @Test
    void theNoticeLadderComesOffTheMapping() {
        ShopSettings found =
                Settings.fromMapping(env("REMINDER_DAYS", 1, "FIRST_NOTICE_DAYS", 2, "SECOND_NOTICE_DAYS", 3));
        assertThat(found.notices().reminderDays()).isEqualTo(1);
        assertThat(found.notices().secondDays()).isEqualTo(3);
    }

    @Test
    void aNoticeLadderOutOfOrderIsRefused() {
        assertThatThrownBy(() -> Settings.fromMapping(env("REMINDER_DAYS", 99))).isInstanceOf(ConflictError.class);
    }

    @Test
    void theScopeFlagsReadTrueAndFalse() {
        assertThat(Settings.fromMapping(env("STRICT_REPIN", "true")).scope().recutAllOnRepin())
                .isTrue();
        assertThat(Settings.fromMapping(env("STRICT_REPIN", "no")).scope().recutAllOnRepin())
                .isFalse();
        assertThat(Settings.fromMapping(env("REKEY_MASTERS", "1")).scope().alwaysRekeyMasters())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "on", "TRUE", "1"})
    void everySpellingOfTrue(String value) {
        assertThat(Settings.fromMapping(env("STRICT_REPIN", value)).scope().recutAllOnRepin())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "off", "FALSE", "0"})
    void everySpellingOfFalse(String value) {
        assertThat(Settings.fromMapping(env("STRICT_REPIN", value)).scope().recutAllOnRepin())
                .isFalse();
    }

    @Test
    void aFlagThatIsNeitherIsRefused() {
        InputError caught =
                catchThrowableOfType(InputError.class, () -> Settings.fromMapping(env("STRICT_REPIN", "perhaps")));
        assertThat(caught.facts()).containsEntry("setting", "STRICT_REPIN");
    }

    @Test
    void aNumberThatIsNotANumberIsRefused() {
        InputError caught = catchThrowableOfType(InputError.class, () -> Settings.fromMapping(env("MAX_RUN", "a few")));
        assertThat(caught.facts()).containsEntry("value", "a few");
    }

    @Test
    void theRateCardComesOffTheMapping() {
        ShopSettings found = Settings.fromMapping(env("LABOUR_CENTS_HOUR", 2600, "BLANK_CENTS", 90));
        assertThat(found.rates().labourCentsPerHour()).isEqualTo(2600);
        assertThat(found.rates().blankCents()).isEqualTo(90);
    }

    @Test
    void theQuarantineWindowAndTheStep() {
        ShopSettings found = Settings.fromMapping(env("QUARANTINE_YEARS", 3, "PROGRESSION_STEP", 1));
        assertThat(found.quarantineYears()).isEqualTo(3);
        assertThat(found.progressionStep()).isEqualTo(1);
    }

    static Stream<Supplier<ShopSettings>> settingsThatForbidEverythingAreRefused() {
        return Stream.of(
                () -> Records.replace(DEFAULTS, Map.of("quarantineYears", 0)),
                () -> Records.replace(DEFAULTS, Map.of("progressionStep", 0)));
    }

    @ParameterizedTest
    @MethodSource
    void settingsThatForbidEverythingAreRefused(Supplier<ShopSettings> broken) {
        assertThatThrownBy(broken::get).isInstanceOf(InputError.class);
    }

    @Test
    void theDepositsCanBeChangedWithoutTouchingTheRest() {
        ShopSettings found = DEFAULTS.withDeposits(1000, 9000);
        assertThat(found.deposits().forKey(KeySymbol.parse("AA"), true)).isEqualTo(9000);
        assertThat(found.notices()).isEqualTo(DEFAULTS.notices());
    }

    @Test
    void aNegativeDepositRateIsRefused() {
        assertThatThrownBy(() -> DEFAULTS.withDeposits(-1, 5000)).isInstanceOf(InputError.class);
    }

    @Test
    void theDescriptionNamesEveryGroup() {
        List<String> lines = DEFAULTS.describe();
        assertThat(lines).hasSize(9);
        assertThat(lines).anyMatch(line -> line.startsWith("deposits:"));
        assertThat(lines).anyMatch(line -> line.startsWith("quarantine:"));
    }

    @Test
    void theEnvironmentIsReadTheSameWay() {
        Map<String, String> environment = Map.of(PREFIX + "MAX_RUN", "5");
        assertThat(Settings.fromLookup(environment::get).cutting().maxRun()).isEqualTo(5);
    }

    @Test
    void anEnvironmentWithNothingSetGivesTheDefaults() {
        for (String name : DEFAULTS.describe()) {
            assertThat(name).isNotEmpty();
        }
        assertThat(Settings.fromLookup(name -> null).cutting().maxRun())
                .isEqualTo(DEFAULTS.cutting().maxRun());
        if (System.getenv(PREFIX + "MAX_RUN") == null) {
            assertThat(Settings.fromEnvironment().cutting().maxRun())
                    .isEqualTo(DEFAULTS.cutting().maxRun());
        }
    }

    @Test
    void theBoxPolicyComesOffTheEnvironment() {
        ShopSettings settings = Settings.fromMapping(Map.of(
                "STAPLEWRIGHT_BOX_LOWEST_LEVEL", "1",
                "STAPLEWRIGHT_BOX_CHECK_DAYS", "90",
                "STAPLEWRIGHT_BOX_RESEAL_DAYS", "0"));
        assertThat(settings.boxes().lowestLevel()).isEqualTo(1);
        assertThat(settings.boxes().checkDays()).isEqualTo(90);
        assertThat(settings.boxes().resealDays()).isZero();
    }

    @Test
    void aBoxPolicyNobodySetKeepsItsDefault() {
        assertThat(Settings.fromMapping(Map.of()).boxes().checkDays()).isEqualTo(180);
    }

    @Test
    void aBoxPolicyThatMakesNoSenseIsRefused() {
        assertThatThrownBy(() -> Settings.fromMapping(Map.of("STAPLEWRIGHT_BOX_LOWEST_LEVEL", "0")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theDescriptionSaysWhereTheBoxesStand() {
        assertThat(DEFAULTS.describe()).anyMatch(line -> line.startsWith("boxes:"));
    }

    @Test
    void theHandoverPolicyComesOffTheEnvironment() {
        ShopSettings settings =
                Settings.fromMapping(Map.of("STAPLEWRIGHT_CONFIRM_DAYS", "30", "STAPLEWRIGHT_MASTER_HANDOVER", "yes"));
        assertThat(settings.transfers().confirmDays()).isEqualTo(30);
        assertThat(settings.transfers().allowMasterTransfer()).isTrue();
    }

    @Test
    void aHandoverPolicyNobodySetKeepsItsDefault() {
        assertThat(Settings.fromMapping(Map.of()).transfers().confirmDays()).isEqualTo(14);
    }

    @Test
    void aHandoverWindowOfNothingIsRefused() {
        assertThatThrownBy(() -> Settings.fromMapping(Map.of("STAPLEWRIGHT_CONFIRM_DAYS", "0")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theDescriptionSaysHowLongAClaimStands() {
        assertThat(DEFAULTS.describe()).anyMatch(line -> line.startsWith("handovers:"));
    }

    @Test
    void everySettingNamesAFieldThatExists() {
        for (Setting item : Settings.SETTINGS) {
            Record group = Settings.groupOf(DEFAULTS, item.group());
            assertThat(Records.component(group.getClass(), Records.camel(item.attribute())))
                    .as(item.variable())
                    .isPresent();
        }
    }

    @Test
    void noTwoSettingsShareAVariable() {
        List<String> names = Settings.SETTINGS.stream().map(Setting::variable).toList();
        assertThat(new HashSet<>(names)).hasSize(names.size());
    }

    @Test
    void noTwoSettingsWriteTheSameField() {
        List<String> fields = Settings.SETTINGS.stream()
                .map(item -> item.group() + "." + item.attribute())
                .toList();
        assertThat(new HashSet<>(fields)).hasSize(fields.size());
    }

    @Test
    void everyVariableIsReadFromTheEnvironment() {
        int checked = 0;
        for (Setting item : Settings.SETTINGS) {
            Object fallback = Settings.valueOf(DEFAULTS, item);
            String written;
            if (fallback instanceof Boolean flag) {
                written = flag ? "no" : "yes";
            } else if (fallback instanceof Integer number) {
                written = String.valueOf(number + 1);
            } else {
                continue;
            }
            ShopSettings settings = Settings.fromMapping(Map.of(PREFIX + item.variable(), written));
            assertThat(String.valueOf(Settings.valueOf(settings, item)))
                    .as(item.variable())
                    .isNotEqualToIgnoringCase(String.valueOf(fallback));
            checked++;
        }
        assertThat(checked).isGreaterThan(20);
    }

    @Test
    void theVariableNamesAreDocumented() {
        List<String> found = Settings.variableNames();
        assertThat(found).contains("STAPLEWRIGHT_MAX_RUN").isSorted();
    }

    @Test
    void aMappingWithNothingInItIsTheDefaults() {
        assertThat(Settings.fromMapping(Map.of())).isEqualTo(DEFAULTS);
    }

    @Test
    void oneSettingLeavesTheRestAlone() {
        ShopSettings settings = Settings.fromMapping(Map.of("STAPLEWRIGHT_MAX_RUN", "5"));
        assertThat(settings.cutting().maxRun()).isEqualTo(5);
        assertThat(settings.cutting().minDistinct())
                .isEqualTo(DEFAULTS.cutting().minDistinct());
        assertThat(settings.notices()).isEqualTo(DEFAULTS.notices());
    }

    @Test
    void theSettingsPageSaysWhenTheGrandMastersAreTreatedApart() {
        assertThat(DEFAULTS.describe()).contains("deposits: 2500 and 5000 cents");
        assertThat(DEFAULTS.describe())
                .contains("stamping: change keys carry the key symbol, masters carry a blind code");
        ShopSettings apart =
                DEFAULTS.withStamping(DEFAULTS.stamping().with(stamps -> stamps.grandMasters(Marking.TAG)));
        assertThat(apart.describe())
                .contains("stamping: change keys carry the key symbol, masters carry a blind code, grand masters carry "
                        + Marking.TAG.value());
        assertThat(new DepositRates().withGrandMasterCents(15000).describe())
                .isEqualTo("2500 and 5000 cents, grand masters 15000");
    }
}
