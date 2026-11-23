package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImageStatus;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;

/**
 * {@code chat_images}: an uploaded chat image from the upload URL to the message that
 * uses it (FR-CH-04). Not in the data dictionary; the dictionary stores only the keys in
 * {@code messages.payload} (plan DEC-CH-8).
 */
@Repository
public class ChatImageRepository {

	private static final String COLUMNS = """
			id, owner_id, conversation_id, original_key, thumb_key, large_key, width, height, processing_status,
			reject_reason, message_id, created_at""";

	private final JdbcClient jdbc;

	public ChatImageRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record ChatImageRow(UUID id, UUID ownerId, UUID conversationId, String originalKey,
			@Nullable String thumbKey, @Nullable String largeKey, @Nullable Integer width, @Nullable Integer height,
			ChatImageStatus status, @Nullable String rejectReason, @Nullable Long messageId, Instant createdAt) {

		public boolean isAttached() {
			return messageId != null;
		}

		public boolean isProcessed() {
			return status != ChatImageStatus.UPLOADING;
		}

	}

	public void insertUploading(UUID id, UUID ownerId, UUID conversationId, String originalKey, Instant now) {
		jdbc.sql("""
				INSERT INTO chat_images (id, owner_id, conversation_id, original_key, created_at)
				VALUES (:id, :owner, :conversation, :key, :now)
				""")
			.param("id", id)
			.param("owner", ownerId)
			.param("conversation", conversationId)
			.param("key", originalKey)
			.param("now", Timestamp.from(now))
			.update();
	}

	public Optional<ChatImageRow> findById(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM chat_images WHERE id = :id")
			.param("id", id)
			.query(ChatImageRepository::rowOf)
			.optional();
	}

	public List<ChatImageRow> findAllById(Collection<UUID> ids) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM chat_images WHERE id IN (:ids)")
			.param("ids", ids)
			.query(ChatImageRepository::rowOf)
			.list();
	}

	public void markReady(UUID id, String thumbKey, String largeKey, int width, int height, Instant now) {
		jdbc.sql("""
				UPDATE chat_images
				SET processing_status = 'READY', thumb_key = :thumb, large_key = :large, width = :w, height = :h,
				    processed_at = :now
				WHERE id = :id
				""")
			.param("thumb", thumbKey)
			.param("large", largeKey)
			.param("w", width)
			.param("h", height)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update();
	}

	public void markRejected(UUID id, ImageRejectReason reason, Instant now) {
		jdbc.sql("""
				UPDATE chat_images SET processing_status = 'REJECTED', reject_reason = :reason, processed_at = :now
				WHERE id = :id
				""").param("reason", reason.name()).param("now", Timestamp.from(now)).param("id", id).update();
	}

	/**
	 * Marks the images as used by a message.
	 * @return how many were attached; fewer than asked means one was already used
	 */
	public int attach(Collection<UUID> ids, long messageId) {
		return jdbc.sql("UPDATE chat_images SET message_id = :m WHERE id IN (:ids) AND message_id IS NULL")
			.param("m", messageId)
			.param("ids", ids)
			.update();
	}

	public List<ChatImageRow> findOrphans(Instant createdBefore, int limit) {
		return jdbc.sql("SELECT " + COLUMNS
				+ " FROM chat_images WHERE message_id IS NULL AND created_at < :before ORDER BY created_at LIMIT :limit")
			.param("before", Timestamp.from(createdBefore))
			.param("limit", limit)
			.query(ChatImageRepository::rowOf)
			.list();
	}

	/**
	 * Every stored object (original and derivatives) of the images of these
	 * conversations.
	 */
	public List<String> objectKeysOf(Collection<UUID> conversationIds) {
		if (conversationIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT unnest(ARRAY[original_key, thumb_key, large_key]) FROM chat_images
				WHERE conversation_id IN (:ids)
				""")
			.param("ids", conversationIds)
			.query(String.class)
			.list()
			.stream()
			.filter(key -> key != null)
			.toList();
	}

	public void delete(Collection<UUID> ids) {
		if (!ids.isEmpty()) {
			jdbc.sql("DELETE FROM chat_images WHERE id IN (:ids)").param("ids", ids).update();
		}
	}

	private static ChatImageRow rowOf(ResultSet row, int index) throws SQLException {
		return new ChatImageRow(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
				row.getObject("conversation_id", UUID.class), row.getString("original_key"), row.getString("thumb_key"),
				row.getString("large_key"), nullableInt(row, "width"), nullableInt(row, "height"),
				ChatImageStatus.valueOf(row.getString("processing_status")), row.getString("reject_reason"),
				nullableLong(row, "message_id"), row.getTimestamp("created_at").toInstant());
	}

	private static @Nullable Integer nullableInt(ResultSet row, String column) throws SQLException {
		int value = row.getInt(column);
		return row.wasNull() ? null : value;
	}

	private static @Nullable Long nullableLong(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
	}

}
