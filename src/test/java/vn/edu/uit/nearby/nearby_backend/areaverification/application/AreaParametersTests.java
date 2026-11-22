package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Period;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.AntiSpoofingThresholds;

/**
 * Values are the ones of SRS table {@code tab:params}, as seeded by V8 and V12.
 */
class AreaParametersTests {

	static final Map<String, String> SEEDED = Map.ofEntries(Map.entry("area.max_count", "2"),
			Map.entry("area.validity_days", "30"), Map.entry("area.renewal_reminder_days", "3"),
			Map.entry("area.renew_radius_m", "1500"), Map.entry("area.accuracy_threshold_m", "100"),
			Map.entry("area.max_fix_age_s", "120"), Map.entry("area.max_speed_kmh", "300"),
			Map.entry("area.max_attempts_per_day", "5"), Map.entry("area.max_changes_per_30d", "3"),
			Map.entry("area.range_levels_km", "2,4,6"), Map.entry("area.grid_cell_m", "300"),
			Map.entry("area.attempt_retention_months", "24"), Map.entry("location.retention_days", "90"),
			Map.entry("listing.pin_radius_m", "1500"));

	private final AreaParameters parameters = new AreaParameters(SEEDED::get);

	@Test
	void areasLastThirtyDaysWithAReminderThreeDaysBefore() {
		assertThat(parameters.validity()).isEqualTo(Duration.ofDays(30));
		assertThat(parameters.renewalReminderWindow()).isEqualTo(Duration.ofDays(3));
	}

	@Test
	void aUserHasAtMostTwoAreaSlots() {
		assertThat(parameters.areaSlots().firstFree(List.of(1, 2))).isEmpty();
		assertThat(parameters.areaSlots().firstFree(List.of(1))).hasValue(2);
	}

	@Test
	void renewalRadiusIsOneThousandFiveHundredMeters() {
		assertThat(parameters.renewalRadius().meters()).isEqualTo(1500);
	}

	@Test
	void hardRuleThresholdsAreOneHundredMetersTwoMinutesAndThreeHundredKmh() {
		assertThat(parameters.antiSpoofingThresholds())
			.isEqualTo(new AntiSpoofingThresholds(100, Duration.ofSeconds(120), 300));
	}

	@Test
	void attemptLimitsAreFivePerDayAndThreeChangesPerThirtyDays() {
		assertThat(parameters.maxAttemptsPerDay()).isEqualTo(5);
		assertThat(parameters.maxAreaChanges()).isEqualTo(3);
		assertThat(parameters.areaChangesWindow()).isEqualTo(Duration.ofDays(30));
	}

	@Test
	void rangeLevelsAreTwoFourAndSixKilometers() {
		assertThat(parameters.rangeLevels().radiusMetersOf(RangeLevel.NEAR)).isEqualTo(2000);
		assertThat(parameters.rangeLevels().radiusMetersOf(RangeLevel.FAR)).isEqualTo(6000);
	}

	@Test
	void areaCentersSnapToThreeHundredMeterCells() {
		assertThat(parameters.gridSnapper().cellSizeMeters()).isEqualTo(300);
	}

	@Test
	void retentionIsNinetyDaysForLocationsAndTwentyFourMonthsForAttempts() {
		assertThat(parameters.preciseLocationRetention()).isEqualTo(Duration.ofDays(90));
		assertThat(parameters.attemptRetention()).isEqualTo(Period.ofMonths(24));
	}

	@Test
	void listingPinsMustBeWithinOneThousandFiveHundredMeters() {
		assertThat(parameters.listingPinRadiusMeters()).isEqualTo(1500);
	}

}
