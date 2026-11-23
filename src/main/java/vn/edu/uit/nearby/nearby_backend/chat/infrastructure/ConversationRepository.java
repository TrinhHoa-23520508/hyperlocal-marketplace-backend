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

import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Participant;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ParticipantRole;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * {@code conversations} and {@code conversation_participants}. Counters move in single
 * UPDATE statements so two sends at once do not lose an increment.
 */
@Repository
public class ConversationRepository {

	private static final String PARTICIPANT_COLUMNS = "conversation_id, user_id, role, last_read_message_id, unread_count";

	private static final String ROW_SELECT = """
			SELECT c.id, c.listing_id, c.buyer_id, c.seller_id, c.last_message_id, c.last_message_at,
			       c.last_message_preview, COALESCE(c.last_message_at, c.created_at) AS sort_at, p.unread_count
			FROM conversations c
			JOIN conversation_participants p ON p.conversation_id = c.id AND p.user_id = :u
			""";

	private final JdbcClient jdbc;

	public ConversationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * One row of the conversation list (A17).
	 *
	 * @param sortAt last message time, or the creation time for a conversation without
	 * messages; the order and the cursor of the list
	 */
	public record ConversationRow(Conversation conversation, @Nullable Long lastMessageId,
			@Nullable Instant lastMessageAt, @Nullable String lastMessagePreview, Instant sortAt, int unreadCount) {
	}

	public record Opened(Conversation conversation, boolean created) {
	}

	public Optional<Conversation> findById(UUID id) {
		return jdbc.sql("SELECT id, listing_id, buyer_id, seller_id FROM conversations WHERE id = :id")
			.param("id", id)
			.query(ConversationRepository::conversationOf)
			.optional();
	}

	/**
	 * Whether the buyer has a conversation about the listing: the "is a chat partner"
	 * fact Transaction needs for a sale marked to a buyer (register row CH-W4, FR-LS-09
	 * (a)).
	 */
	public boolean existsByListingAndBuyer(UUID listingId, UUID buyerId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM conversations WHERE listing_id = :l AND buyer_id = :b)")
			.param("l", listingId)
			.param("b", buyerId)
			.query(Boolean.class)
			.single();
	}

	public Optional<Conversation> findByListingAndBuyer(UUID listingId, UUID buyerId) {
		return jdbc.sql(
				"SELECT id, listing_id, buyer_id, seller_id FROM conversations WHERE listing_id = :l AND buyer_id = :b")
			.param("l", listingId)
			.param("b", buyerId)
			.query(ConversationRepository::conversationOf)
			.optional();
	}

	/**
	 * Creates the conversation and its two participants, or returns the one that won a
	 * race on the (listing, buyer) key (UC-CH-01).
	 */
	public Opened insertIfAbsent(UUID listingId, UUID buyerId, UUID sellerId, Instant now) {
		UUID id = UUID.randomUUID();
		int inserted = jdbc.sql("""
				INSERT INTO conversations (id, listing_id, buyer_id, seller_id, created_at, updated_at)
				VALUES (:id, :l, :b, :s, :now, :now)
				ON CONFLICT (listing_id, buyer_id) DO NOTHING
				""")
			.param("id", id)
			.param("l", listingId)
			.param("b", buyerId)
			.param("s", sellerId)
			.param("now", Timestamp.from(now))
			.update();
		if (inserted == 0) {
			return new Opened(findByListingAndBuyer(listingId, buyerId).orElseThrow(), false);
		}
		insertParticipant(id, buyerId, ParticipantRole.BUYER, now);
		insertParticipant(id, sellerId, ParticipantRole.SELLER, now);
		return new Opened(new Conversation(id, listingId, buyerId, sellerId), true);
	}

	public Optional<Participant> findParticipant(UUID conversationId, UUID userId) {
		return jdbc
			.sql("SELECT " + PARTICIPANT_COLUMNS
					+ " FROM conversation_participants WHERE conversation_id = :c AND user_id = :u")
			.param("c", conversationId)
			.param("u", userId)
			.query(ConversationRepository::participantOf)
			.optional();
	}

	public void recordLastMessage(UUID conversationId, long messageId, Instant at, String preview) {
		jdbc.sql("""
				UPDATE conversations
				SET last_message_id = :m, last_message_at = :at, last_message_preview = :preview, updated_at = :at
				WHERE id = :c
				""")
			.param("m", messageId)
			.param("at", Timestamp.from(at))
			.param("preview", preview)
			.param("c", conversationId)
			.update();
	}

	/**
	 * One more unread message for every member but the sender (FR-CH-06); a SYSTEM
	 * message has no sender, so both members get it.
	 */
	public void addUnreadForOthers(UUID conversationId, @Nullable UUID senderId) {
		jdbc.sql("""
				UPDATE conversation_participants SET unread_count = unread_count + 1
				WHERE conversation_id = :c AND (CAST(:sender AS uuid) IS NULL OR user_id <> CAST(:sender AS uuid))
				""").param("c", conversationId).param("sender", senderId).update();
	}

	/**
	 * Moves the read marker forward and recounts what is left unread (FR-CH-06).
	 * @return the new state, or empty when the marker is not newer than the stored one
	 * and nothing changed
	 */
	public Optional<Participant> markRead(UUID conversationId, UUID userId, long messageId) {
		return jdbc.sql("""
				UPDATE conversation_participants p
				SET last_read_message_id = :m,
				    unread_count = (SELECT count(*) FROM messages m
				                    WHERE m.conversation_id = p.conversation_id AND m.id > :m
				                      AND m.sender_id IS DISTINCT FROM p.user_id)
				WHERE p.conversation_id = :c AND p.user_id = :u AND p.last_read_message_id < :m
				RETURNING %s
				""".formatted(PARTICIPANT_COLUMNS))
			.param("m", messageId)
			.param("c", conversationId)
			.param("u", userId)
			.query(ConversationRepository::participantOf)
			.optional();
	}

	public Optional<ConversationRow> findRow(UUID conversationId, UUID userId) {
		return jdbc.sql(ROW_SELECT + " WHERE c.id = :c")
			.param("u", userId)
			.param("c", conversationId)
			.query(ConversationRepository::rowOf)
			.optional();
	}

	public List<ConversationRow> pageOf(UUID userId, @Nullable Cursor after, int limit) {
		String cursorFilter = after == null ? ""
				: "AND (COALESCE(c.last_message_at, c.created_at), c.id) < (:afterAt, :afterId)";
		var statement = jdbc
			.sql(ROW_SELECT + " WHERE p.left_at IS NULL " + cursorFilter
					+ " ORDER BY sort_at DESC, c.id DESC LIMIT :limit")
			.param("u", userId)
			.param("limit", limit);
		if (after != null) {
			statement = statement.param("afterAt", Timestamp.from(after.createdAt())).param("afterId", after.id());
		}
		return statement.query(ConversationRepository::rowOf).list();
	}

	/**
	 * One conversation of a user and the other member.
	 */
	public record ConversationOf(UUID conversationId, UUID counterpartId) {
	}

	public List<ConversationOf> conversationsOf(UUID userId) {
		return jdbc.sql("""
				SELECT id, CASE WHEN buyer_id = :u THEN seller_id ELSE buyer_id END AS counterpart_id
				FROM conversations WHERE buyer_id = :u OR seller_id = :u
				""")
			.param("u", userId)
			.query((row, index) -> new ConversationOf(row.getObject("id", UUID.class),
					row.getObject("counterpart_id", UUID.class)))
			.list();
	}

	/**
	 * Deletes the conversations with their messages, participants and image rows (they
	 * cascade).
	 */
	public void deleteAll(Collection<UUID> conversationIds) {
		if (!conversationIds.isEmpty()) {
			jdbc.sql("DELETE FROM conversations WHERE id IN (:ids)").param("ids", conversationIds).update();
		}
	}

	public int unreadTotalOf(UUID userId) {
		return jdbc.sql("SELECT COALESCE(sum(unread_count), 0) FROM conversation_participants WHERE user_id = :u")
			.param("u", userId)
			.query(Integer.class)
			.single();
	}

	private void insertParticipant(UUID conversationId, UUID userId, ParticipantRole role, Instant now) {
		jdbc.sql("""
				INSERT INTO conversation_participants (conversation_id, user_id, role, joined_at)
				VALUES (:c, :u, :role, :now)
				""")
			.param("c", conversationId)
			.param("u", userId)
			.param("role", role.name())
			.param("now", Timestamp.from(now))
			.update();
	}

	private static Conversation conversationOf(ResultSet row, int index) throws SQLException {
		return new Conversation(row.getObject("id", UUID.class), row.getObject("listing_id", UUID.class),
				row.getObject("buyer_id", UUID.class), row.getObject("seller_id", UUID.class));
	}

	private static Participant participantOf(ResultSet row, int index) throws SQLException {
		return new Participant(row.getObject("conversation_id", UUID.class), row.getObject("user_id", UUID.class),
				ParticipantRole.valueOf(row.getString("role")), row.getLong("last_read_message_id"),
				row.getInt("unread_count"));
	}

	private static ConversationRow rowOf(ResultSet row, int index) throws SQLException {
		long lastMessageId = row.getLong("last_message_id");
		Long boxedLastMessageId = row.wasNull() ? null : lastMessageId;
		Timestamp lastMessageAt = row.getTimestamp("last_message_at");
		return new ConversationRow(conversationOf(row, index), boxedLastMessageId,
				lastMessageAt == null ? null : lastMessageAt.toInstant(), row.getString("last_message_preview"),
				row.getTimestamp("sort_at").toInstant(), row.getInt("unread_count"));
	}

}
