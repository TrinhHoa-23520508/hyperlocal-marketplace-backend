package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;

/**
 * {@code notification_preferences}, with an optimistic lock on {@code version}.
 */
@Repository
public class NotificationPreferencesRepository {

	private static final String COLUMNS = """
			user_id, chat_push, trade_push, keyword_alert_push, marketing_push, dnd_enabled, dnd_start, dnd_end,
			time_zone, version""";

	private final JdbcClient jdbc;

	public NotificationPreferencesRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<NotificationPreferences> find(UUID userId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM notification_preferences WHERE user_id = :u")
			.param("u", userId)
			.query(NotificationPreferencesRepository::rowOf)
			.optional();
	}

	/**
	 * Creates the default row on registration or on first save; does nothing when the row
	 * exists, so a replayed {@code UserRegistered} is harmless.
	 */
	public void createDefaultsIfAbsent(UUID userId, Instant now) {
		jdbc.sql("""
				INSERT INTO notification_preferences (user_id, updated_at) VALUES (:u, :now)
				ON CONFLICT (user_id) DO NOTHING
				""").param("u", userId).param("now", java.sql.Timestamp.from(now)).update();
	}

	/**
	 * @param expectedVersion the version the caller read
	 * @return the saved state with the next version, or empty when someone saved in
	 * between (the caller answers 409)
	 */
	public Optional<NotificationPreferences> saveIfVersion(NotificationPreferences preferences, long expectedVersion,
			Instant now) {
		return jdbc.sql("""
				UPDATE notification_preferences
				SET chat_push = :chat, trade_push = :trade, keyword_alert_push = :keyword, marketing_push = :marketing,
				    dnd_enabled = :dndEnabled, dnd_start = :dndStart, dnd_end = :dndEnd, time_zone = :zone,
				    updated_at = :now, version = version + 1
				WHERE user_id = :u AND version = :expected
				RETURNING %s
				""".formatted(COLUMNS))
			.param("chat", preferences.chatPush())
			.param("trade", preferences.tradePush())
			.param("keyword", preferences.keywordAlertPush())
			.param("marketing", preferences.marketingPush())
			.param("dndEnabled", preferences.dndEnabled())
			.param("dndStart", Time.valueOf(preferences.dndStart()))
			.param("dndEnd", Time.valueOf(preferences.dndEnd()))
			.param("zone", preferences.timeZone().getId())
			.param("now", java.sql.Timestamp.from(now))
			.param("u", preferences.userId())
			.param("expected", expectedVersion)
			.query(NotificationPreferencesRepository::rowOf)
			.optional();
	}

	public void delete(UUID userId) {
		jdbc.sql("DELETE FROM notification_preferences WHERE user_id = :u").param("u", userId).update();
	}

	private static NotificationPreferences rowOf(ResultSet row, int index) throws SQLException {
		return new NotificationPreferences(row.getObject("user_id", UUID.class), row.getBoolean("chat_push"),
				row.getBoolean("trade_push"), row.getBoolean("keyword_alert_push"), row.getBoolean("marketing_push"),
				row.getBoolean("dnd_enabled"), row.getTime("dnd_start").toLocalTime(),
				row.getTime("dnd_end").toLocalTime(), ZoneId.of(row.getString("time_zone")), row.getLong("version"));
	}

}
