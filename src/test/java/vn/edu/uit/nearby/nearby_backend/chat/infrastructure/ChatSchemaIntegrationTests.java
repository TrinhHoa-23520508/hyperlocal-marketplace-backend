package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * V15: the CHECKs and unique keys of the chat and notification tables (data dictionary
 * 4.2, integrity rules 4.3).
 */
@IntegrationTest
class ChatSchemaIntegrationTests {

	@Autowired
	JdbcClient jdbc;

	@Nested
	@DisplayName("conversations (FR-CH-01)")
	class Conversations {

		@Test
		@DisplayName("one conversation per (listing, buyer)")
		void listingAndBuyerAreUnique() {
			UUID listing = UUID.randomUUID();
			UUID buyer = UUID.randomUUID();
			insertConversation(listing, buyer, UUID.randomUUID());

			assertThatThrownBy(() -> insertConversation(listing, buyer, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertConversation(listing, UUID.randomUUID(), UUID.randomUUID());
		}

		@Test
		void sellerCannotBeTheBuyer() {
			UUID user = UUID.randomUUID();

			assertThatThrownBy(() -> insertConversation(UUID.randomUUID(), user, user))
				.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		void participantRoleAndUnreadCountAreChecked() {
			UUID conversation = insertConversation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

			assertThatThrownBy(() -> insertParticipant(conversation, "ADMIN", 0))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertParticipant(conversation, "BUYER", -1))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertParticipant(conversation, "BUYER", 0);
			assertThat(jdbc.sql("SELECT last_read_message_id FROM conversation_participants WHERE conversation_id = :c")
				.param("c", conversation)
				.query(Long.class)
				.single()).isZero();
		}

	}

	@Nested
	@DisplayName("messages (FR-CH-02..05, FR-CH-08)")
	class Messages {

		private final UUID conversation = insertConversation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

		@Test
		void aTextMessageNeedsABody() {
			assertThatThrownBy(() -> insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		void anImageOrLocationMessageNeedsAPayload() {
			assertThatThrownBy(() -> insertMessage("IMAGE", UUID.randomUUID(), UUID.randomUUID(), null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertMessage("LOCATION", UUID.randomUUID(), UUID.randomUUID(), null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertMessage("LOCATION", UUID.randomUUID(), UUID.randomUUID(), null,
					"{\"lat\":10.77,\"lng\":106.7,\"placeName\":\"Cafe\"}");
		}

		@Test
		void aBodyOfMoreThanAThousandCharactersIsRefused() {
			assertThatThrownBy(
					() -> insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), "x".repeat(1_001), null))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), "x".repeat(1_000), null);
		}

		@Test
		void onlySystemMessagesMayHaveNoSenderAndNoClientId() {
			assertThatThrownBy(() -> insertMessage("TEXT", null, UUID.randomUUID(), "hi", null))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertMessage("TEXT", UUID.randomUUID(), null, "hi", null))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertMessage("SYSTEM", null, null, "Đã gửi đề nghị", "{\"kind\":\"OFFER_CREATED\"}");
		}

		@Test
		@DisplayName("FR-CH-02 / D4: the same client_msg_id is stored once per sender")
		void clientMessageIdIsUniquePerSender() {
			UUID sender = UUID.randomUUID();
			UUID clientMsgId = UUID.randomUUID();
			insertMessage("TEXT", sender, clientMsgId, "hi", null);

			assertThatThrownBy(() -> insertMessage("TEXT", sender, clientMsgId, "hi", null))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertMessage("TEXT", UUID.randomUUID(), clientMsgId, "hi", null);
		}

		@Test
		@DisplayName("FR-CH-07: ids only grow, which makes them the sync checkpoint")
		void idsIncrease() {
			long first = insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), "a", null);
			long second = insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), "b", null);

			assertThat(second).isGreaterThan(first);
		}

		@Test
		void typeAndRiskFlagHaveTheirDefaults() {
			assertThatThrownBy(() -> insertMessage("VIDEO", UUID.randomUUID(), UUID.randomUUID(), "hi", "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
			long id = insertMessage("TEXT", UUID.randomUUID(), UUID.randomUUID(), "hi", null);

			assertThat(jdbc.sql("SELECT risk_flag FROM messages WHERE id = :id")
				.param("id", id)
				.query(Boolean.class)
				.single()).isFalse();
		}

		private long insertMessage(String type, UUID sender, UUID clientMsgId, String body, String payload) {
			return jdbc.sql("""
					INSERT INTO messages (conversation_id, sender_id, type, body, payload, client_msg_id)
					VALUES (:conversation, :sender, :type, :body, CAST(:payload AS jsonb), :clientMsgId)
					RETURNING id
					""")
				.param("conversation", conversation)
				.param("sender", sender)
				.param("type", type)
				.param("body", body)
				.param("payload", payload)
				.param("clientMsgId", clientMsgId)
				.query(Long.class)
				.single();
		}

	}

	@Nested
	@DisplayName("notifications (FR-NT-01, FR-NT-04)")
	class Notifications {

		@Test
		@DisplayName("FR-NT-04: a replayed event creates no second notification for the same recipient")
		void sourceEventAndRecipientAreUnique() {
			UUID event = UUID.randomUUID();
			UUID recipient = UUID.randomUUID();
			insertNotification(event, recipient, "SYSTEM", "PENDING");

			assertThatThrownBy(() -> insertNotification(event, recipient, "SYSTEM", "PENDING"))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertNotification(event, UUID.randomUUID(), "SYSTEM", "PENDING");
		}

		@Test
		void categoryAndPushStatusAreChecked() {
			assertThatThrownBy(() -> insertNotification(UUID.randomUUID(), UUID.randomUUID(), "SPAM", "PENDING"))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertNotification(UUID.randomUUID(), UUID.randomUUID(), "CHAT", "QUEUED"))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertNotification(UUID.randomUUID(), UUID.randomUUID(), "KEYWORD_ALERT", "SKIPPED");
		}

		private void insertNotification(UUID event, UUID recipient, String category, String pushStatus) {
			jdbc.sql(
					"""
							INSERT INTO notifications (id, recipient_id, category, type, title, body, source_event_id, push_status)
							VALUES (:id, :recipient, :category, 'WELCOME', 'Chào mừng', 'Xin chào', :event, :pushStatus)
							""")
				.param("id", UUID.randomUUID())
				.param("recipient", recipient)
				.param("category", category)
				.param("event", event)
				.param("pushStatus", pushStatus)
				.update();
		}

	}

	@Nested
	@DisplayName("device tokens and preferences (FR-NT-02, FR-NT-03)")
	class DevicesAndPreferences {

		@Test
		@DisplayName("FR-NT-02: a device has one token and a token belongs to one row")
		void oneTokenPerDevice() {
			UUID user = UUID.randomUUID();
			insertToken(user, "device-1", "token-" + user, "ANDROID");

			assertThatThrownBy(() -> insertToken(user, "device-1", "other-" + user, "ANDROID"))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertToken(UUID.randomUUID(), "device-2", "token-" + user, "ANDROID"))
				.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> insertToken(user, "device-3", "ios-" + user, "IOS"))
				.isInstanceOf(DataIntegrityViolationException.class);
			insertToken(user, "device-2", "web-" + user, "WEB");
		}

		@Test
		@DisplayName("FR-NT-03: preferences default to chat and trade on, marketing off, quiet hours 22:00-07:00")
		void preferencesHaveTheDictionaryDefaults() {
			UUID user = UUID.randomUUID();
			jdbc.sql("INSERT INTO notification_preferences (user_id) VALUES (:user)").param("user", user).update();

			var row = jdbc.sql("""
					SELECT chat_push, trade_push, keyword_alert_push, marketing_push, dnd_enabled,
					       CAST(dnd_start AS text) AS dnd_start, CAST(dnd_end AS text) AS dnd_end, time_zone
					FROM notification_preferences WHERE user_id = :user
					""").param("user", user).query().singleRow();

			assertThat(row).containsEntry("chat_push", true)
				.containsEntry("trade_push", true)
				.containsEntry("keyword_alert_push", true)
				.containsEntry("marketing_push", false)
				.containsEntry("dnd_enabled", false)
				.containsEntry("dnd_start", "22:00:00")
				.containsEntry("dnd_end", "07:00:00")
				.containsEntry("time_zone", "Asia/Ho_Chi_Minh");
		}

		private void insertToken(UUID user, String deviceId, String token, String platform) {
			jdbc.sql("""
					INSERT INTO device_tokens (id, user_id, device_id, fcm_token, platform)
					VALUES (:id, :user, :deviceId, :token, :platform)
					""")
				.param("id", UUID.randomUUID())
				.param("user", user)
				.param("deviceId", deviceId)
				.param("token", token)
				.param("platform", platform)
				.update();
		}

	}

	@Test
	@DisplayName("tab:params: the three chat and notification parameters are seeded")
	void parametersAreSeeded() {
		assertThat(jdbc.sql("""
				SELECT key || '=' || value FROM app_parameters
				WHERE key IN ('chat.max_message_chars', 'chat.rate_limit_per_min', 'notification.retention_months')
				""").query(String.class).list()).containsExactlyInAnyOrder("chat.max_message_chars=1000",
				"chat.rate_limit_per_min=30", "notification.retention_months=6");
	}

	private UUID insertConversation(UUID listing, UUID buyer, UUID seller) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO conversations (id, listing_id, buyer_id, seller_id) VALUES (:id, :l, :b, :s)")
			.param("id", id)
			.param("l", listing)
			.param("b", buyer)
			.param("s", seller)
			.update();
		return id;
	}

	private void insertParticipant(UUID conversation, String role, int unread) {
		jdbc.sql("""
				INSERT INTO conversation_participants (conversation_id, user_id, role, unread_count)
				VALUES (:c, :u, :role, :unread)
				""")
			.param("c", conversation)
			.param("u", UUID.randomUUID())
			.param("role", role)
			.param("unread", unread)
			.update();
	}

}
