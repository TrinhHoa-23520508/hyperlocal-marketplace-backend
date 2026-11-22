package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Duration;
import java.time.Period;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaSlots;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevels;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RenewalRadius;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.AntiSpoofingThresholds;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;

/**
 * Typed access to the area module's runtime parameters (SRS table {@code tab:params});
 * values are seeded by Flyway V8 and V12. Read on every call so a changed value applies
 * without a restart.
 */
@Component
public class AreaParameters {

	private static final String MAX_COUNT = "area.max_count";

	private static final String VALIDITY_DAYS = "area.validity_days";

	private static final String RENEWAL_REMINDER_DAYS = "area.renewal_reminder_days";

	private static final String RENEW_RADIUS_METERS = "area.renew_radius_m";

	private static final String ACCURACY_THRESHOLD_METERS = "area.accuracy_threshold_m";

	private static final String MAX_FIX_AGE_SECONDS = "area.max_fix_age_s";

	private static final String MAX_SPEED_KMH = "area.max_speed_kmh";

	private static final String MAX_ATTEMPTS_PER_DAY = "area.max_attempts_per_day";

	private static final String MAX_CHANGES_PER_30_DAYS = "area.max_changes_per_30d";

	private static final String RANGE_LEVELS_KM = "area.range_levels_km";

	private static final String GRID_CELL_METERS = "area.grid_cell_m";

	private static final String ATTEMPT_RETENTION_MONTHS = "area.attempt_retention_months";

	private static final String LOCATION_RETENTION_DAYS = "location.retention_days";

	private static final String PIN_RADIUS_METERS = "listing.pin_radius_m";

	/**
	 * The window is part of the key's meaning ({@code area.max_changes_per_30d}), not a
	 * separate parameter.
	 */
	private static final Duration AREA_CHANGES_WINDOW = Duration.ofDays(30);

	private final AppParameters parameters;

	public AreaParameters(AppParameters parameters) {
		this.parameters = parameters;
	}

	public AreaSlots areaSlots() {
		return new AreaSlots(parameters.getInt(MAX_COUNT));
	}

	public Duration validity() {
		return Duration.ofDays(parameters.getInt(VALIDITY_DAYS));
	}

	public Duration renewalReminderWindow() {
		return Duration.ofDays(parameters.getInt(RENEWAL_REMINDER_DAYS));
	}

	public RenewalRadius renewalRadius() {
		return new RenewalRadius(parameters.getDouble(RENEW_RADIUS_METERS));
	}

	public AntiSpoofingThresholds antiSpoofingThresholds() {
		return new AntiSpoofingThresholds(parameters.getDouble(ACCURACY_THRESHOLD_METERS),
				Duration.ofSeconds(parameters.getInt(MAX_FIX_AGE_SECONDS)), parameters.getDouble(MAX_SPEED_KMH));
	}

	public int maxAttemptsPerDay() {
		return parameters.getInt(MAX_ATTEMPTS_PER_DAY);
	}

	public int maxAreaChanges() {
		return parameters.getInt(MAX_CHANGES_PER_30_DAYS);
	}

	public Duration areaChangesWindow() {
		return AREA_CHANGES_WINDOW;
	}

	public RangeLevels rangeLevels() {
		return RangeLevels.parse(parameters.get(RANGE_LEVELS_KM));
	}

	public GridSnapper gridSnapper() {
		return new GridSnapper(parameters.getInt(GRID_CELL_METERS));
	}

	public Period attemptRetention() {
		return Period.ofMonths(parameters.getInt(ATTEMPT_RETENTION_MONTHS));
	}

	public Duration preciseLocationRetention() {
		return Duration.ofDays(parameters.getInt(LOCATION_RETENTION_DAYS));
	}

	public double listingPinRadiusMeters() {
		return parameters.getDouble(PIN_RADIUS_METERS);
	}

}
