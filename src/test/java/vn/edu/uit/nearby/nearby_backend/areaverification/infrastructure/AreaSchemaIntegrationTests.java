package vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class AreaSchemaIntegrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AppParameters appParameters;

	@Test
	void seedsTheAreaParametersOfTheSrs() {
		Map<String, String> expected = Map.ofEntries(Map.entry("area.max_count", "2"),
				Map.entry("area.validity_days", "30"), Map.entry("area.renewal_reminder_days", "3"),
				Map.entry("area.renew_radius_m", "1500"), Map.entry("area.accuracy_threshold_m", "100"),
				Map.entry("area.max_fix_age_s", "120"), Map.entry("area.max_speed_kmh", "300"),
				Map.entry("area.max_attempts_per_day", "5"), Map.entry("area.max_changes_per_30d", "3"),
				Map.entry("area.range_levels_km", "2,4,6"), Map.entry("area.grid_cell_m", "300"),
				Map.entry("area.attempt_retention_months", "24"), Map.entry("location.retention_days", "90"),
				Map.entry("listing.pin_radius_m", "1500"));

		expected.forEach((key, value) -> assertThat(appParameters.get(key)).as(key).isEqualTo(value));
	}

	@Test
	void dropsTheOutdatedValidityParameter() {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM app_parameters WHERE key IN ('area.verification_validity_days', 'area.retention_days')",
				Integer.class);

		assertThat(count).isZero();
	}

	@Test
	void allowsTwoLiveAreasPerUser() {
		UUID userId = UUID.randomUUID();

		insertArea(userId, 1, "VERIFIED");
		insertArea(userId, 2, "EXPIRED");

		assertThat(liveAreaCount(userId)).isEqualTo(2);
	}

	@Test
	void refusesASecondLiveAreaInTheSameSlot() {
		UUID userId = UUID.randomUUID();
		insertArea(userId, 1, "VERIFIED");

		assertThatThrownBy(() -> insertArea(userId, 1, "EXPIRING")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void freesTheSlotOnceTheAreaIsRemoved() {
		UUID userId = UUID.randomUUID();
		insertArea(userId, 1, "REMOVED");

		insertArea(userId, 1, "VERIFIED");

		assertThat(liveAreaCount(userId)).isEqualTo(1);
	}

	@Test
	void refusesAThirdSlot() {
		assertThatThrownBy(() -> insertArea(UUID.randomUUID(), 3, "VERIFIED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesTwoActiveAreasForOneUser() {
		UUID userId = UUID.randomUUID();
		insertActiveArea(userId, 1);

		assertThatThrownBy(() -> insertActiveArea(userId, 2)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesAPurgedAttemptThatStillHoldsItsLocation() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO area_verifications (id, user_id, kind, result, reported_location, grid_cell, accuracy_m,
				                                fix_time, mock_flag, idempotency_key, location_purged_at)
				VALUES (?, ?, 'INITIAL', 'ACCEPTED', ST_GeogFromText('POINT(106.8031 10.87)'), '300:1:1', 12,
				        now(), false, ?, now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertArea(UUID userId, int slot, String status) {
		jdbc.update("""
				INSERT INTO user_areas (id, user_id, slot, label, center, grid_cell, status, verified_at, expires_at,
				                        removed_at)
				VALUES (?, ?, ?, 'Nhà', ST_GeogFromText('POINT(106.8031 10.87)'), '300:1:1', ?, now(),
				        now() + interval '30 days', CASE WHEN ? = 'REMOVED' THEN now() END)
				""", UUID.randomUUID(), userId, slot, status, status);
	}

	private void insertActiveArea(UUID userId, int slot) {
		jdbc.update("""
				INSERT INTO user_areas (id, user_id, slot, label, center, grid_cell, status, is_active, verified_at,
				                        expires_at)
				VALUES (?, ?, ?, 'Nhà', ST_GeogFromText('POINT(106.8031 10.87)'), '300:1:1', 'VERIFIED', true, now(),
				        now() + interval '30 days')
				""", UUID.randomUUID(), userId, slot);
	}

	private int liveAreaCount(UUID userId) {
		return jdbc.queryForObject("SELECT count(*) FROM user_areas WHERE user_id = ? AND status <> 'REMOVED'",
				Integer.class, userId);
	}

}
