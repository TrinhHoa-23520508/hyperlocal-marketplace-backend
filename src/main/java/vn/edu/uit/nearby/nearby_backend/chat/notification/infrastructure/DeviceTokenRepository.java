package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code device_tokens}: one FCM token per (user, device) (FR-NT-02, FR-ID-03).
 */
@Repository
public class DeviceTokenRepository {

	private final JdbcClient jdbc;

	public DeviceTokenRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Saves the device's current token. The token belongs to one row, so when it was
	 * registered by another user or device (a shared phone, a reinstall) that row is
	 * dropped first; the device's earlier token is replaced.
	 */
	public void upsert(UUID userId, String deviceId, String fcmToken, String platform, @Nullable String appVersion,
			Instant now) {
		jdbc.sql("DELETE FROM device_tokens WHERE fcm_token = :token AND NOT (user_id = :u AND device_id = :d)")
			.param("token", fcmToken)
			.param("u", userId)
			.param("d", deviceId)
			.update();
		jdbc.sql("""
				INSERT INTO device_tokens (id, user_id, device_id, fcm_token, platform, app_version, last_seen_at,
				                           created_at, updated_at)
				VALUES (:id, :u, :d, :token, :platform, :version, :now, :now, :now)
				ON CONFLICT (user_id, device_id) DO UPDATE
				SET fcm_token = EXCLUDED.fcm_token, platform = EXCLUDED.platform, app_version = EXCLUDED.app_version,
				    last_seen_at = EXCLUDED.last_seen_at, updated_at = EXCLUDED.updated_at
				""")
			.param("id", UUID.randomUUID())
			.param("u", userId)
			.param("d", deviceId)
			.param("token", fcmToken)
			.param("platform", platform)
			.param("version", appVersion)
			.param("now", Timestamp.from(now))
			.update();
	}

	public List<String> tokensOf(UUID userId) {
		return jdbc.sql("SELECT fcm_token FROM device_tokens WHERE user_id = :u ORDER BY created_at")
			.param("u", userId)
			.query(String.class)
			.list();
	}

	public boolean deleteDevice(UUID userId, String deviceId) {
		return jdbc.sql("DELETE FROM device_tokens WHERE user_id = :u AND device_id = :d")
			.param("u", userId)
			.param("d", deviceId)
			.update() > 0;
	}

	/**
	 * FR-NT-02: FCM said the token is no longer valid.
	 */
	public void deleteByToken(String fcmToken) {
		jdbc.sql("DELETE FROM device_tokens WHERE fcm_token = :token").param("token", fcmToken).update();
	}

}
