package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.edu.uit.nearby.nearby_backend.chat.ReportableMessages;
import vn.edu.uit.nearby.nearby_backend.chat.ReportableMessages.MessageReportContext;
import vn.edu.uit.nearby.nearby_backend.chat.ReportableMessages.ReportedMessage;
import vn.edu.uit.nearby.nearby_backend.chat.application.SystemMessagePoster.SystemMessage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * What other modules get from chat: the report context for Moderation (CH-W2), the system
 * message and the conversation of an offer for Transaction (CH-W1, CH-W5) and the "is a
 * chat partner" fact (CH-W4).
 */
@IntegrationTest
class ChatModuleApiIntegrationTests {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	@Autowired
	ReportableMessages reportable;

	@Autowired
	SystemMessagePoster poster;

	@Autowired
	ConversationService conversationService;

	@Autowired
	ConversationRepository conversations;

	@Autowired
	MessageRepository messages;

	@Autowired
	MessageViews views;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private final UUID listing = UUID.randomUUID();

	private Conversation conversation() {
		return conversations.insertIfAbsent(listing, buyer, seller, T0).conversation();
	}

	private long text(Conversation conversation, UUID sender, String body) {
		return messages.insert(conversation.id(), sender, MessageContent.text(body, 1000), UUID.randomUUID(), T0)
			.message()
			.id();
	}

	@Nested
	@DisplayName("CH-W2 / FR-CH-12: reporting a message")
	class Reporting {

		@Test
		@DisplayName("the report carries the message and the 20 messages before it, oldest first")
		void carriesTwentyMessagesOfContext() {
			Conversation conversation = conversation();
			List<Long> ids = IntStream.range(0, 25)
				.mapToObj(i -> text(conversation, i % 2 == 0 ? seller : buyer, "tin " + i))
				.toList();
			long reported = text(conversation, seller, "Chuyển cọc trước nhé");

			MessageReportContext context = reportable.contextOf(reported, buyer);

			assertThat(context.message().id()).isEqualTo(reported);
			assertThat(context.message().summary()).isEqualTo("Chuyển cọc trước nhé");
			assertThat(context.reportedUserId()).isEqualTo(seller);
			assertThat(context.listingId()).isEqualTo(listing);
			assertThat(context.previous()).hasSize(20)
				.extracting(ReportedMessage::id)
				.containsExactlyElementsOf(ids.subList(5, 25));
		}

		@Test
		void aShortConversationHasLessContext() {
			Conversation conversation = conversation();
			text(conversation, buyer, "một");
			long reported = text(conversation, seller, "hai");

			assertThat(reportable.contextOf(reported, buyer).previous()).hasSize(1);
		}

		@Test
		@DisplayName("FR-CH-12: your own message cannot be reported")
		void aReporterCannotReportTheirOwnMessage() {
			Conversation conversation = conversation();
			long own = text(conversation, buyer, "của tôi");

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> reportable.contextOf(own, buyer));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.SELF_ACTION_NOT_ALLOWED);
		}

		@Test
		@DisplayName("FR-ID-18: a stranger and an unknown id both get CONVERSATION_NOT_FOUND")
		void strangersLearnNothing() {
			Conversation conversation = conversation();
			long message = text(conversation, seller, "riêng tư");

			assertThat(catchThrowableOfType(BusinessException.class,
					() -> reportable.contextOf(message, UUID.randomUUID()))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
			assertThat(catchThrowableOfType(BusinessException.class, () -> reportable.contextOf(Long.MAX_VALUE, buyer))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
		}

		@Test
		void aSystemMessageCannotBeReported() {
			Conversation conversation = conversation();
			long system = messages
				.insert(conversation.id(), null, MessageContent.systemNote("OFFER_CREATED", "Đã gửi đề nghị", Map.of()),
						UUID.randomUUID(), T0)
				.message()
				.id();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> reportable.contextOf(system, buyer));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		@DisplayName("an image, a point and a system note appear in the context as text a moderator can read")
		void nonTextMessagesAreSummarised() {
			Conversation conversation = conversation();
			messages.insert(conversation.id(), seller, MessageContent.images(List.of(new ChatImage("k", "t", 10, 10))),
					UUID.randomUUID(), T0);
			messages.insert(conversation.id(), seller, MessageContent.meetingPoint(10.77, 106.7, "Cafe"),
					UUID.randomUUID(), T0);
			messages.insert(conversation.id(), null,
					MessageContent.systemNote("OFFER_CREATED", "Đã gửi đề nghị", Map.of()), UUID.randomUUID(), T0);
			long reported = text(conversation, seller, "báo cáo tin này");

			MessageReportContext context = reportable.contextOf(reported, buyer);

			assertThat(context.previous()).extracting(ReportedMessage::summary)
				.containsExactly("[Hình ảnh]", "[Vị trí]", "Đã gửi đề nghị");
			assertThat(context.previous()).extracting(ReportedMessage::type)
				.containsExactly("IMAGE", "LOCATION", "SYSTEM");
		}

	}

	@Nested
	@DisplayName("CH-W1 / CH-W5 / FR-CH-08: system messages")
	class SystemMessages {

		@Test
		@DisplayName("UC-TX-01 step 7: an offer creates the conversation and posts the first system message")
		void anOfferCreatesTheConversation() {
			UUID eventId = UUID.randomUUID();

			boolean posted = poster.post(new SystemMessage(listing, buyer, seller, "OFFER_CREATED",
					"Đã gửi đề nghị 150.000đ", Map.of("transactionId", "t-1"), eventId));

			assertThat(posted).isTrue();
			Conversation created = conversations.findByListingAndBuyer(listing, buyer).orElseThrow();
			assertThat(created.sellerId()).isEqualTo(seller);
			List<StoredMessage> stored = messages.page(created.id(), null, null, 10);
			assertThat(stored).hasSize(1);
			assertThat(stored.getFirst().type()).isEqualTo(MessageType.SYSTEM);
			assertThat(stored.getFirst().senderId()).isNull();
			assertThat(views.of(stored.getFirst()).system().kind()).isEqualTo("OFFER_CREATED");
			assertThat(views.of(stored.getFirst()).system().facts()).isEqualTo(Map.of("transactionId", "t-1"));
			assertThat(conversations.findParticipant(created.id(), buyer).orElseThrow().unreadCount()).isEqualTo(1);
			assertThat(conversations.findParticipant(created.id(), seller).orElseThrow().unreadCount()).isEqualTo(1);
			assertThat(conversations.findRow(created.id(), buyer).orElseThrow().lastMessagePreview())
				.isEqualTo("Đã gửi đề nghị 150.000đ");
		}

		@Test
		@DisplayName("FR-CH-08: the same event posts exactly one message, however often it is delivered")
		void aReplayedEventPostsOnce() {
			UUID eventId = UUID.randomUUID();

			assertThat(poster
				.post(new SystemMessage(listing, buyer, seller, "OFFER_CREATED", "Đã gửi đề nghị", Map.of(), eventId)))
				.isTrue();
			assertThat(poster
				.post(new SystemMessage(listing, buyer, seller, "OFFER_CREATED", "Đã gửi đề nghị", Map.of(), eventId)))
				.isFalse();

			Conversation created = conversations.findByListingAndBuyer(listing, buyer).orElseThrow();
			assertThat(messages.page(created.id(), null, null, 10)).hasSize(1);
			assertThat(conversations.findParticipant(created.id(), buyer).orElseThrow().unreadCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("each transition of a transaction is its own event and its own message")
		void differentEventsPostSeparately() {
			poster.post(new SystemMessage(listing, buyer, seller, "OFFER_CREATED", "Đã gửi đề nghị", Map.of(),
					UUID.randomUUID()));
			poster.post(new SystemMessage(listing, buyer, seller, "OFFER_ACCEPTED", "Người bán đã chấp nhận", Map.of(),
					UUID.randomUUID()));

			Conversation created = conversations.findByListingAndBuyer(listing, buyer).orElseThrow();
			assertThat(messages.page(created.id(), null, null, 10)).extracting(StoredMessage::body)
				.containsExactly("Đã gửi đề nghị", "Người bán đã chấp nhận");
		}

		@Test
		@DisplayName("a system message goes into the conversation that exists and does not create one")
		void postingIntoAnExistingConversationOnly() {
			UUID eventId = UUID.randomUUID();
			assertThat(poster.postIfConversationExists(new SystemMessage(listing, buyer, seller, "SALE_MARKED",
					"Người bán đánh dấu đã bán", Map.of(), eventId)))
				.isFalse();
			assertThat(conversations.findByListingAndBuyer(listing, buyer)).isEmpty();

			Conversation existing = conversation();

			assertThat(poster.postIfConversationExists(new SystemMessage(listing, buyer, seller, "SALE_MARKED",
					"Người bán đánh dấu đã bán", Map.of(), eventId)))
				.isTrue();
			assertThat(poster.postIfConversationExists(new SystemMessage(listing, buyer, seller, "SALE_MARKED",
					"Người bán đánh dấu đã bán", Map.of(), eventId)))
				.isFalse();
			assertThat(messages.page(existing.id(), null, null, 10)).hasSize(1);
		}

		@Test
		@DisplayName("a conversation that already exists is reused and not counted again")
		void openForTradeReusesTheConversation() {
			Conversation existing = conversation();

			Conversation opened = conversationService.openForTrade(listing, buyer, seller);

			assertThat(opened.id()).isEqualTo(existing.id());
		}

	}

	@Test
	@DisplayName("CH-W4 / FR-LS-09 (a): a buyer who chatted about the listing is a chat partner, anyone else is not")
	void chatPartnersAreKnown() {
		assertThat(conversationService.isChatPartner(listing, buyer)).isFalse();

		conversation();

		assertThat(conversationService.isChatPartner(listing, buyer)).isTrue();
		assertThat(conversationService.isChatPartner(listing, UUID.randomUUID())).isFalse();
		assertThat(conversationService.isChatPartner(UUID.randomUUID(), buyer)).isFalse();
		assertThat(conversations.existsByListingAndBuyer(listing, buyer)).isTrue();
	}

}
