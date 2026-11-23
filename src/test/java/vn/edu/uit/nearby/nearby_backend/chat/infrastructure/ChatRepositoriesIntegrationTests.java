package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Participant;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.ConversationRow;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.Opened;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository.Inserted;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The SQL of chat against PostgreSQL: the unique keys that make opening and sending
 * idempotent, the counters, and the cursor queries.
 */
@IntegrationTest
class ChatRepositoriesIntegrationTests {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	@Autowired
	ConversationRepository conversations;

	@Autowired
	MessageRepository messages;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private final UUID listing = UUID.randomUUID();

	@Nested
	@DisplayName("FR-CH-01: one conversation per (listing, buyer)")
	class Opening {

		@Test
		void openingTwiceReturnsTheSameConversationAndCreatesOneParticipantPair() {
			Opened first = conversations.insertIfAbsent(listing, buyer, seller, T0);
			Opened second = conversations.insertIfAbsent(listing, buyer, seller, T0.plusSeconds(5));

			assertThat(first.created()).isTrue();
			assertThat(second.created()).isFalse();
			assertThat(second.conversation().id()).isEqualTo(first.conversation().id());
			assertThat(conversations.findParticipant(first.conversation().id(), buyer)).isPresent();
			assertThat(conversations.findParticipant(first.conversation().id(), seller)).isPresent();
		}

		@Test
		void anotherBuyerOfTheSameListingGetsAnotherConversation() {
			Opened mine = conversations.insertIfAbsent(listing, buyer, seller, T0);
			Opened theirs = conversations.insertIfAbsent(listing, UUID.randomUUID(), seller, T0);

			assertThat(theirs.created()).isTrue();
			assertThat(theirs.conversation().id()).isNotEqualTo(mine.conversation().id());
		}

	}

	@Nested
	@DisplayName("FR-CH-02: the repeat of a send returns the stored message")
	class Sending {

		private final UUID conversation = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();

		@Test
		void theSameClientMessageIdIsStoredOnce() {
			UUID clientMsgId = UUID.randomUUID();
			Inserted first = messages.insert(conversation, buyer, MessageContent.text("Còn hàng không?", 1000),
					clientMsgId, T0);
			Inserted repeat = messages.insert(conversation, buyer, MessageContent.text("Còn hàng không?", 1000),
					clientMsgId, T0.plusSeconds(3));

			assertThat(first.created()).isTrue();
			assertThat(repeat.created()).isFalse();
			assertThat(repeat.message().id()).isEqualTo(first.message().id());
			assertThat(messages.page(conversation, null, null, 50)).hasSize(1);
		}

		@Test
		void aDifferentSenderMayUseTheSameClientMessageId() {
			UUID clientMsgId = UUID.randomUUID();

			Inserted mine = messages.insert(conversation, buyer, MessageContent.text("a", 1000), clientMsgId, T0);
			Inserted theirs = messages.insert(conversation, seller, MessageContent.text("b", 1000), clientMsgId, T0);

			assertThat(theirs.created()).isTrue();
			assertThat(theirs.message().id()).isGreaterThan(mine.message().id());
		}

		@Test
		void everyTypeRoundTripsItsPayload() {
			messages.insert(conversation, buyer, MessageContent.images(List.of(new ChatImage("k", "t", 1080, 720))),
					UUID.randomUUID(), T0);
			messages.insert(conversation, buyer, MessageContent.meetingPoint(10.77, 106.7, "Cafe"), UUID.randomUUID(),
					T0);
			messages.insert(conversation, null,
					MessageContent.systemNote("OFFER_CREATED", "Đã gửi đề nghị", java.util.Map.of("a", "b")), null, T0);

			List<StoredMessage> stored = messages.page(conversation, null, null, 50);

			assertThat(stored).extracting(StoredMessage::type)
				.containsExactly(MessageType.IMAGE, MessageType.LOCATION, MessageType.SYSTEM);
			assertThat(stored.get(0).payload())
				.isEqualTo(List.of(java.util.Map.of("key", "k", "thumbKey", "t", "w", 1080, "h", 720)));
			assertThat(stored.get(1).payload())
				.isEqualTo(java.util.Map.of("lat", 10.77, "lng", 106.7, "placeName", "Cafe"));
			assertThat(stored.get(2).senderId()).isNull();
			assertThat(stored.get(2).clientMsgId()).isNull();
		}

	}

	@Nested
	@DisplayName("FR-CH-06: read marker and unread count")
	class Reading {

		private final UUID conversation = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();

		@Test
		void aMessageAddsOneUnreadForTheCounterpartOnly() {
			messages.insert(conversation, buyer, MessageContent.text("a", 1000), UUID.randomUUID(), T0);
			conversations.addUnreadForOthers(conversation, buyer);

			assertThat(unreadOf(seller)).isEqualTo(1);
			assertThat(unreadOf(buyer)).isZero();
		}

		@Test
		void aSystemMessageIsUnreadForBothMembers() {
			conversations.addUnreadForOthers(conversation, null);

			assertThat(unreadOf(seller)).isEqualTo(1);
			assertThat(unreadOf(buyer)).isEqualTo(1);
		}

		@Test
		void readingRecountsWhatIsLeftAfterTheMarker() {
			long first = send(buyer, "1");
			send(buyer, "2");
			send(buyer, "3");

			Participant afterFirst = conversations.markRead(conversation, seller, first).orElseThrow();

			assertThat(afterFirst.lastReadMessageId()).isEqualTo(first);
			assertThat(afterFirst.unreadCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("a marker that goes backwards or stays changes nothing")
		void anOlderMarkerIsIgnored() {
			long first = send(buyer, "1");
			long second = send(buyer, "2");
			conversations.markRead(conversation, seller, second);

			assertThat(conversations.markRead(conversation, seller, first)).isEmpty();
			assertThat(conversations.markRead(conversation, seller, second)).isEmpty();
			assertThat(unreadOf(seller)).isZero();
		}

		@Test
		void ownMessagesNeverCountAsUnread() {
			long mine = send(seller, "mine");
			send(buyer, "theirs");

			Participant read = conversations.markRead(conversation, seller, mine).orElseThrow();

			assertThat(read.unreadCount()).isEqualTo(1);
		}

		@Test
		void theBadgeSumsEveryConversationOfTheUser() {
			UUID other = conversations.insertIfAbsent(UUID.randomUUID(), buyer, UUID.randomUUID(), T0)
				.conversation()
				.id();
			messages.insert(other, seller, MessageContent.text("x", 1000), UUID.randomUUID(), T0);
			conversations.addUnreadForOthers(other, seller);
			send(seller, "y");

			assertThat(conversations.unreadTotalOf(buyer)).isEqualTo(2);
		}

		private long send(UUID sender, String text) {
			long id = messages.insert(conversation, sender, MessageContent.text(text, 1000), UUID.randomUUID(), T0)
				.message()
				.id();
			conversations.addUnreadForOthers(conversation, sender);
			return id;
		}

		private int unreadOf(UUID user) {
			return conversations.findParticipant(conversation, user).orElseThrow().unreadCount();
		}

	}

	@Nested
	@DisplayName("FR-CH-07: sync and paging")
	class Syncing {

		private final UUID conversation = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();

		@Test
		@DisplayName("the missed messages come in id order, without gaps or repeats, across conversations")
		void missedMessagesAcrossConversations() {
			UUID second = conversations.insertIfAbsent(UUID.randomUUID(), buyer, UUID.randomUUID(), T0)
				.conversation()
				.id();
			long a = messages.insert(conversation, seller, MessageContent.text("a", 1000), UUID.randomUUID(), T0)
				.message()
				.id();
			long b = messages.insert(second, buyer, MessageContent.text("b", 1000), UUID.randomUUID(), T0)
				.message()
				.id();
			long c = messages.insert(conversation, seller, MessageContent.text("c", 1000), UUID.randomUUID(), T0)
				.message()
				.id();
			messages.insert(conversations.insertIfAbsent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T0)
				.conversation()
				.id(), seller, MessageContent.text("not mine", 1000), UUID.randomUUID(), T0);

			assertThat(messages.missedBy(buyer, a - 1, 50)).extracting(StoredMessage::id).containsExactly(a, b, c);
			assertThat(messages.missedBy(buyer, a, 50)).extracting(StoredMessage::id).containsExactly(b, c);
			assertThat(messages.missedBy(buyer, c, 50)).isEmpty();
			assertThat(messages.missedBy(buyer, a - 1, 2)).hasSize(2);
		}

		@Test
		void pagingWalksBackwardsAndForwards() {
			List<Long> ids = java.util.stream.IntStream.range(0, 5)
				.mapToObj(i -> messages
					.insert(conversation, buyer, MessageContent.text("m" + i, 1000), UUID.randomUUID(), T0)
					.message()
					.id())
				.toList();

			assertThat(messages.page(conversation, null, null, 2)).extracting(StoredMessage::id)
				.containsExactly(ids.get(3), ids.get(4));
			assertThat(messages.page(conversation, ids.get(3), null, 2)).extracting(StoredMessage::id)
				.containsExactly(ids.get(1), ids.get(2));
			assertThat(messages.page(conversation, null, ids.get(1), 2)).extracting(StoredMessage::id)
				.containsExactly(ids.get(2), ids.get(3));
			assertThat(messages.newestIdOf(conversation)).isEqualTo(ids.get(4));
		}

	}

	@Nested
	@DisplayName("retention: deleting a conversation")
	class Deleting {

		@Autowired
		ChatImageRepository chatImages;

		@Test
		@DisplayName("messages, participants and image rows go with the conversation, and the image files are listed first")
		void deletingCascades() {
			UUID id = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();
			UUID other = conversations.insertIfAbsent(UUID.randomUUID(), buyer, seller, T0).conversation().id();
			messages.insert(id, buyer, MessageContent.text("a", 1000), UUID.randomUUID(), T0);
			UUID imageId = UUID.randomUUID();
			chatImages.insertUploading(imageId, buyer, id, "chat-incoming/o.jpg", T0);
			chatImages.markReady(imageId, "chat-images/t.webp", "chat-images/l.webp", 10, 10, T0);

			assertThat(conversations.conversationsOf(buyer))
				.extracting(ConversationRepository.ConversationOf::conversationId)
				.contains(id, other);
			assertThat(chatImages.objectKeysOf(List.of(id))).containsExactlyInAnyOrder("chat-incoming/o.jpg",
					"chat-images/t.webp", "chat-images/l.webp");

			conversations.deleteAll(List.of(id));

			assertThat(conversations.findById(id)).isEmpty();
			assertThat(conversations.findParticipant(id, buyer)).isEmpty();
			assertThat(messages.page(id, null, null, 10)).isEmpty();
			assertThat(chatImages.findById(imageId)).isEmpty();
			assertThat(conversations.findById(other)).isPresent();
			assertThat(chatImages.objectKeysOf(List.of())).isEmpty();
			conversations.deleteAll(List.of());
		}

		@Test
		void theCounterpartIsTheOtherMember() {
			UUID id = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();

			assertThat(conversations.conversationsOf(seller)).filteredOn(c -> c.conversationId().equals(id))
				.extracting(ConversationRepository.ConversationOf::counterpartId)
				.containsExactly(buyer);
		}

	}

	@Nested
	@DisplayName("A17: the conversation list")
	class Listing {

		@Test
		void newestActivityFirstWithACursor() {
			UUID older = conversations.insertIfAbsent(UUID.randomUUID(), buyer, UUID.randomUUID(), T0)
				.conversation()
				.id();
			UUID newer = conversations.insertIfAbsent(UUID.randomUUID(), buyer, UUID.randomUUID(), T0.plusSeconds(10))
				.conversation()
				.id();
			UUID quiet = conversations.insertIfAbsent(UUID.randomUUID(), buyer, UUID.randomUUID(), T0.minusSeconds(60))
				.conversation()
				.id();
			conversations.recordLastMessage(older, 1, T0.plusSeconds(100), "Xin chào");

			List<ConversationRow> all = conversations.pageOf(buyer, null, 10);

			assertThat(all).extracting(row -> row.conversation().id()).containsExactly(older, newer, quiet);
			assertThat(all.getFirst().lastMessagePreview()).isEqualTo("Xin chào");
			ConversationRow first = all.getFirst();
			var cursor = new vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor(first.sortAt(),
					first.conversation().id());
			assertThat(conversations.pageOf(buyer, cursor, 10)).extracting(row -> row.conversation().id())
				.containsExactly(newer, quiet);
		}

		@Test
		void theRowOfOneConversationCarriesTheReadersUnreadCount() {
			UUID id = conversations.insertIfAbsent(listing, buyer, seller, T0).conversation().id();
			conversations.addUnreadForOthers(id, seller);

			assertThat(conversations.findRow(id, buyer))
				.hasValueSatisfying(row -> assertThat(row.unreadCount()).isEqualTo(1));
			assertThat(conversations.findRow(id, UUID.randomUUID())).isEmpty();
		}

	}

}
