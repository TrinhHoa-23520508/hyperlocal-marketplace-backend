package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * {@code notifications}. The unique key (source event, recipient) makes a replayed event
 * create nothing (FR-NT-04).
 */
@Repository
public class NotificationRepository {

	private static final String COLUMNS = """
			id, created_at, recipient_id, category, type, title, body, deep_link, CAST(payload AS text) AS payload,
			push_status, pushed_at, read_at""";

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	public NotificationRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * A row to insert.
	 *
	 * @param pushStatus PENDING when a push will follow, otherwise SKIPPED
	 * @param payload the identifiers a screen needs, or null
	 */
	public record NewNotification(UUID id, UUID recipientId, NotificationCategory category, String type, String title,
			String body, @Nullable String deepLink, @Nullable Object payload, UUID sourceEventId, String pushStatus,
			Instant createdAt) {
	}

	public record StoredNotification(UUID id, Instant createdAt, UUID recipientId, NotificationCategory category,
			String type, String title, String body, @Nullable String deepLink, @Nullable Object payload,
			String pushStatus, @Nullable Instant pushedAt, @Nullable Instant readAt) {

		public boolean isRead() {
			return readAt != null;
		}

	}

	/**
	 * @return false when the same event was already notified to the same recipient
	 */
	public boolean insertIfAbsent(NewNotification notification) {
		return jdbc
			.sql("""
					INSERT INTO notifications (id, created_at, recipient_id, category, type, title, body, deep_link, payload,
					                           source_event_id, push_status)
					VALUES (:id, :createdAt, :recipient, :category, :type, :title, :body, :deepLink, CAST(:payload AS jsonb),
					        :source, :pushStatus)
					ON CONFLICT (source_event_id, recipient_id) DO NOTHING
					""")
			.param("id", notification.id())
			.param("createdAt", Timestamp.from(notification.createdAt()))
			.param("recipient", notification.recipientId())
			.param("category", notification.category().name())
			.param("type", notification.type())
			.param("title", notification.title())
			.param("body", notification.body())
			.param("deepLink", notification.deepLink())
			.param("payload",
					notification.payload() == null ? null : jsonMapper.writeValueAsString(notification.payload()))
			.param("source", notification.sourceEventId())
			.param("pushStatus", notification.pushStatus())
			.update() == 1;
	}

	public List<StoredNotification> pageOf(UUID recipientId, @Nullable Cursor after, int limit) {
		String cursorFilter = after == null ? "" : "AND (created_at, id) < (:afterAt, :afterId)";
		var statement = jdbc
			.sql("SELECT " + COLUMNS + " FROM notifications WHERE recipient_id = :r " + cursorFilter
					+ " ORDER BY created_at DESC, id DESC LIMIT :limit")
			.param("r", recipientId)
			.param("limit", limit);
		if (after != null) {
			statement = statement.param("afterAt", Timestamp.from(after.createdAt())).param("afterId", after.id());
		}
		return statement.query(this::rowOf).list();
	}

	public Optional<StoredNotification> findOwned(UUID id, UUID recipientId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM notifications WHERE id = :id AND recipient_id = :r")
			.param("id", id)
			.param("r", recipientId)
			.query(this::rowOf)
			.optional();
	}

	/**
	 * @return true when the notification exists and is the recipient's; marking an
	 * already read one again keeps the first read time
	 */
	public boolean markRead(UUID id, UUID recipientId, Instant now) {
		boolean exists = jdbc.sql("SELECT count(*) FROM notifications WHERE id = :id AND recipient_id = :r")
			.param("id", id)
			.param("r", recipientId)
			.query(Integer.class)
			.single() > 0;
		jdbc.sql("UPDATE notifications SET read_at = :now WHERE id = :id AND recipient_id = :r AND read_at IS NULL")
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("r", recipientId)
			.update();
		return exists;
	}

	public int markAllRead(UUID recipientId, Instant now) {
		return jdbc.sql("UPDATE notifications SET read_at = :now WHERE recipient_id = :r AND read_at IS NULL")
			.param("now", Timestamp.from(now))
			.param("r", recipientId)
			.update();
	}

	public int unreadCountOf(UUID recipientId) {
		return jdbc.sql("SELECT count(*) FROM notifications WHERE recipient_id = :r AND read_at IS NULL")
			.param("r", recipientId)
			.query(Integer.class)
			.single();
	}

	public void recordPush(UUID id, String pushStatus, @Nullable Instant pushedAt) {
		jdbc.sql("UPDATE notifications SET push_status = :status, pushed_at = :at WHERE id = :id")
			.param("status", pushStatus)
			.param("at", pushedAt == null ? null : Timestamp.from(pushedAt))
			.param("id", id)
			.update();
	}

	/**
	 * Retention (ch4): notifications older than {@code notification.retention_months}.
	 */
	public int deleteCreatedBefore(Instant cutoff) {
		return jdbc.sql("DELETE FROM notifications WHERE created_at < :cutoff")
			.param("cutoff", Timestamp.from(cutoff))
			.update();
	}

	public int deleteOf(UUID recipientId) {
		return jdbc.sql("DELETE FROM notifications WHERE recipient_id = :r").param("r", recipientId).update();
	}

	private StoredNotification rowOf(ResultSet row, int index) throws SQLException {
		String payload = row.getString("payload");
		Timestamp pushedAt = row.getTimestamp("pushed_at");
		Timestamp readAt = row.getTimestamp("read_at");
		return new StoredNotification(row.getObject("id", UUID.class), row.getTimestamp("created_at").toInstant(),
				row.getObject("recipient_id", UUID.class), NotificationCategory.valueOf(row.getString("category")),
				row.getString("type"), row.getString("title"), row.getString("body"), row.getString("deep_link"),
				payload == null ? null : jsonMapper.readValue(payload, Object.class), row.getString("push_status"),
				pushedAt == null ? null : pushedAt.toInstant(), readAt == null ? null : readAt.toInstant());
	}

}
