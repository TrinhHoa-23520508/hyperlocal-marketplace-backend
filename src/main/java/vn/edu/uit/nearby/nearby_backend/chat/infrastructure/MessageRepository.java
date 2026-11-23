package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;

/**
 * {@code messages}. The primary key is (id, created_at) so the table can be partitioned
 * later; the unique key (sender, client_msg_id) makes a repeated send return the stored
 * message (FR-CH-02, UC-CH-02 A3).
 */
@Repository
public class MessageRepository {

	private static final String COLUMNS = "id, conversation_id, sender_id, type, body, CAST(payload AS text) AS payload, client_msg_id, created_at";

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	public MessageRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param created false when the (sender, client message id) pair was already stored
	 * and {@code message} is the earlier row
	 */
	public record Inserted(StoredMessage message, boolean created) {
	}

	public Inserted insert(UUID conversationId, @Nullable UUID senderId, MessageContent content,
			@Nullable UUID clientMsgId, Instant now) {
		Long id = jdbc.sql("""
				INSERT INTO messages (conversation_id, sender_id, type, body, payload, client_msg_id, created_at)
				VALUES (:c, :sender, :type, :body, CAST(:payload AS jsonb), :clientMsgId, :now)
				ON CONFLICT DO NOTHING
				RETURNING id
				""")
			.param("c", conversationId)
			.param("sender", senderId)
			.param("type", content.type().name())
			.param("body", content.body())
			.param("payload", payloadJson(content))
			.param("clientMsgId", clientMsgId)
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.optional()
			.orElse(null);
		if (id == null) {
			return new Inserted(storedEarlier(conversationId, senderId, clientMsgId), false);
		}
		return new Inserted(new StoredMessage(id, conversationId, senderId, content.type(), content.body(),
				content.payload(), clientMsgId, now), true);
	}

	/**
	 * The row that made this insert a repeat: the sender's earlier message with the same
	 * client message id, or for a SYSTEM message the one already posted for the same
	 * event.
	 */
	private StoredMessage storedEarlier(UUID conversationId, @Nullable UUID senderId, @Nullable UUID clientMsgId) {
		if (senderId != null) {
			return findBySenderAndClientMsgId(senderId, clientMsgId).orElseThrow();
		}
		return jdbc
			.sql("SELECT " + COLUMNS
					+ " FROM messages WHERE conversation_id = :c AND type = 'SYSTEM' AND client_msg_id = :id")
			.param("c", conversationId)
			.param("id", clientMsgId)
			.query(this::messageOf)
			.single();
	}

	public Optional<StoredMessage> findBySenderAndClientMsgId(@Nullable UUID senderId, @Nullable UUID clientMsgId) {
		if (senderId == null || clientMsgId == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE sender_id = :s AND client_msg_id = :c")
			.param("s", senderId)
			.param("c", clientMsgId)
			.query(this::messageOf)
			.optional();
	}

	public Optional<StoredMessage> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE id = :id")
			.param("id", id)
			.query(this::messageOf)
			.optional();
	}

	/**
	 * The {@code limit} messages just before {@code messageId}, oldest first: the context
	 * attached to a report (FR-CH-12).
	 */
	public List<StoredMessage> before(UUID conversationId, long messageId, int limit) {
		return page(conversationId, messageId, null, limit);
	}

	public long newestIdOf(UUID conversationId) {
		return jdbc.sql("SELECT COALESCE(max(id), 0) FROM messages WHERE conversation_id = :c")
			.param("c", conversationId)
			.query(Long.class)
			.single();
	}

	public long newestId() {
		return jdbc.sql("SELECT COALESCE(max(id), 0) FROM messages").query(Long.class).single();
	}

	/**
	 * Messages of one conversation in ascending id order: the newest {@code limit} when
	 * neither bound is given, the {@code limit} just before {@code before}, or the first
	 * {@code limit} after {@code after}.
	 */
	public List<StoredMessage> page(UUID conversationId, @Nullable Long before, @Nullable Long after, int limit) {
		if (after != null) {
			return jdbc
				.sql("SELECT " + COLUMNS
						+ " FROM messages WHERE conversation_id = :c AND id > :after ORDER BY id ASC LIMIT :limit")
				.param("c", conversationId)
				.param("after", after)
				.param("limit", limit)
				.query(this::messageOf)
				.list();
		}
		long upperBound = before == null ? Long.MAX_VALUE : before;
		List<StoredMessage> newestFirst = new ArrayList<>(jdbc
			.sql("SELECT " + COLUMNS
					+ " FROM messages WHERE conversation_id = :c AND id < :before ORDER BY id DESC LIMIT :limit")
			.param("c", conversationId)
			.param("before", upperBound)
			.param("limit", limit)
			.query(this::messageOf)
			.list());
		Collections.reverse(newestFirst);
		return newestFirst;
	}

	/**
	 * Everything the user missed in every one of their conversations, oldest first
	 * (FR-CH-07).
	 */
	public List<StoredMessage> missedBy(UUID userId, long afterMessageId, int limit) {
		return jdbc.sql("""
				SELECT m.id, m.conversation_id, m.sender_id, m.type, m.body, CAST(m.payload AS text) AS payload,
				       m.client_msg_id, m.created_at
				FROM messages m
				JOIN conversation_participants p ON p.conversation_id = m.conversation_id AND p.user_id = :u
				WHERE m.id > :after
				ORDER BY m.id ASC
				LIMIT :limit
				""")
			.param("u", userId)
			.param("after", afterMessageId)
			.param("limit", limit)
			.query(this::messageOf)
			.list();
	}

	/**
	 * @return how many messages {@code userId} did not send in the conversation after
	 * {@code messageId}
	 */
	public int countUnreadAfter(UUID conversationId, UUID userId, long messageId) {
		return jdbc.sql("""
				SELECT count(*) FROM messages
				WHERE conversation_id = :c AND id > :m AND sender_id IS DISTINCT FROM :u
				""").param("c", conversationId).param("m", messageId).param("u", userId).query(Integer.class).single();
	}

	private @Nullable String payloadJson(MessageContent content) {
		Object payload = content.payload();
		return payload == null ? null : jsonMapper.writeValueAsString(payload);
	}

	private StoredMessage messageOf(ResultSet row, int index) throws SQLException {
		String payload = row.getString("payload");
		return new StoredMessage(row.getLong("id"), row.getObject("conversation_id", UUID.class),
				row.getObject("sender_id", UUID.class), MessageType.valueOf(row.getString("type")),
				row.getString("body"), payload == null ? null : jsonMapper.readValue(payload, Object.class),
				row.getObject("client_msg_id", UUID.class), row.getTimestamp("created_at").toInstant());
	}

}
