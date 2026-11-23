package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationQueries.MessageWindow;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationQueries.SyncResult;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Sent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Participant;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.Opened;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.support.InMemoryImageStorage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;
import vn.edu.uit.nearby.nearby_backend.support.TestImages;

/**
 * UC-CH-01..05 end to end on the real database and Redis: open, send, repeat, rate limit,
 * read, sync, lock, images and the notification of an offline recipient. The facades of
 * identity, listing and area verification are mocks, because their own tests cover them.
 */
@IntegrationTest
@Import(ChatUpstreamFacades.class)
class ChatMessagingIntegrationTests {

	@Autowired
	ListingLookup listings;

	@Autowired
	ListingCounters listingCounters;

	@Autowired
	UserBlocks blocks;

	@Autowired
	AccountStanding standing;

	@Autowired
	TradingGate tradingGate;

	@Autowired
	InteractionRange interactionRange;

	@Autowired
	ConversationService conversationService;

	@Autowired
	MessageService messageService;

	@Autowired
	ReadService readService;

	@Autowired
	ConversationQueries queries;

	@Autowired
	ChatImageService imageService;

	@Autowired
	ChatImageRepository chatImages;

	@Autowired
	ConversationRepository conversations;

	@Autowired
	NotificationRepository notifications;

	@Autowired
	InMemoryImageStorage storage;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private final UUID listingId = UUID.randomUUID();

	private final UUID area = UUID.randomUUID();

	@BeforeEach
	void givenAnActiveListing() {
		reset(listings, listingCounters, blocks, standing, tradingGate, interactionRange);
		givenListingStatus("ACTIVE");
		when(standing.isActiveWithVerifiedPhone(any())).thenReturn(true);
		when(interactionRange.allowsNewContacts(area)).thenReturn(true);
	}

	private void givenListingStatus(String status) {
		ListingSnapshot snapshot = new ListingSnapshot(listingId, seller, area, "Ghế gỗ", status, 150_000, true,
				new GeoPoint(10.77, 106.70), null, null);
		when(listings.find(listingId)).thenReturn(java.util.Optional.of(snapshot));
		when(listings.findAll(any())).thenReturn(Map.of(listingId, snapshot));
	}

	private Conversation open() {
		return conversationService.open(buyer, listingId).conversation();
	}

	private Sent send(Conversation conversation, UUID sender, String text) {
		return messageService.sendText(new Request(sender, conversation.id(), UUID.randomUUID(), false), text);
	}

	@Nested
	@DisplayName("UC-CH-01: opening")
	class Opening {

		@Test
		@DisplayName("FR-CH-01 / LS-W6: the first open creates the conversation and counts it; the second returns it")
		void openingTwiceGivesTheSameConversation() {
			Opened first = conversationService.open(buyer, listingId);
			Opened second = conversationService.open(buyer, listingId);

			assertThat(first.created()).isTrue();
			assertThat(second.created()).isFalse();
			assertThat(second.conversation().id()).isEqualTo(first.conversation().id());
			assertThat(first.conversation().sellerId()).isEqualTo(seller);
			verify(listingCounters, org.mockito.Mockito.times(1)).incrementChatCount(listingId);
		}

		@Test
		@DisplayName("FR-CH-11: after the listing is sold the conversation is still returned and readable")
		void aSoldListingStaysReachable() {
			Conversation conversation = open();
			send(conversation, buyer, "Còn không bạn?");

			givenListingStatus("SOLD");

			assertThat(conversationService.open(buyer, listingId).conversation().id()).isEqualTo(conversation.id());
			assertThat(queries.messages(buyer, conversation.id(), MessageWindow.newest())).hasSize(1);
		}

	}

	@Nested
	@DisplayName("UC-CH-02: sending")
	class Sending {

		@Test
		@DisplayName("FR-CH-02 / FR-CH-06: the message is stored, the counterpart has one unread, the list shows the preview")
		void aTextMessageIsStoredAndCounted() {
			Conversation conversation = open();

			Sent sent = send(conversation, buyer, "Còn hàng không bạn?");

			assertThat(sent.created()).isTrue();
			assertThat(sent.message().type()).isEqualTo("TEXT");
			assertThat(sent.message().body()).isEqualTo("Còn hàng không bạn?");
			assertThat(sent.message().senderId()).isEqualTo(buyer);
			assertThat(queries.detail(seller, conversation.id()).unreadCount()).isEqualTo(1);
			assertThat(queries.detail(buyer, conversation.id()).unreadCount()).isZero();
			CursorPage<ConversationView> sellersList = queries.list(seller, null, null);
			assertThat(sellersList.items()).hasSize(1);
			assertThat(sellersList.items().getFirst().lastMessagePreview()).isEqualTo("Còn hàng không bạn?");
			assertThat(sellersList.items().getFirst().listing().title()).isEqualTo("Ghế gỗ");
		}

		@Test
		@DisplayName("UC-CH-02 A3: a retry with the same client message id returns the stored message once")
		void aRetryIsStoredOnce() {
			Conversation conversation = open();
			Request request = new Request(buyer, conversation.id(), UUID.randomUUID(), false);

			Sent first = messageService.sendText(request, "một");
			Sent repeat = messageService.sendText(request, "một");

			assertThat(repeat.created()).isFalse();
			assertThat(repeat.message().id()).isEqualTo(first.message().id());
			assertThat(queries.messages(buyer, conversation.id(), MessageWindow.newest())).hasSize(1);
			assertThat(queries.detail(seller, conversation.id()).unreadCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("FR-CH-03: the 31st message in a minute is 429 CHAT_RATE_LIMITED with a wait time")
		void theThirtyFirstMessageIsRateLimited() {
			Conversation conversation = open();
			for (int i = 0; i < 30; i++) {
				send(conversation, buyer, "tin " + i);
			}

			RateLimitExceededException error = catchThrowableOfType(RateLimitExceededException.class,
					() -> send(conversation, buyer, "tin 31"));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CHAT_RATE_LIMITED);
			assertThat(error.getRetryAfter()).hasValueSatisfying(wait -> assertThat(wait).isPositive());
			assertThat(queries.messages(buyer, conversation.id(), MessageWindow.newest(100))).hasSize(30);
			assertThat(send(conversation, seller, "người kia vẫn gửi được").created()).isTrue();
		}

		@Test
		@DisplayName("FR-CH-03: 1 001 characters is 422 MESSAGE_TOO_LONG")
		void aTooLongMessageIsRefused() {
			Conversation conversation = open();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> send(conversation, buyer, "a".repeat(1_001)));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.MESSAGE_TOO_LONG);
		}

		@Test
		@DisplayName("FR-CH-02 E4: a stranger gets CONVERSATION_NOT_FOUND")
		void aStrangerCannotSend() {
			Conversation conversation = open();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> send(conversation, UUID.randomUUID(), "xin chào"));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
		}

		@Test
		@DisplayName("FR-CH-05: a meeting point is stored for the two members")
		void aMeetingPointIsShared() {
			Conversation conversation = open();

			Sent sent = messageService.sendMeetingPoint(new Request(buyer, conversation.id(), UUID.randomUUID(), false),
					10.7769, 106.7009, "Công viên 23/9");

			assertThat(sent.message().type()).isEqualTo("LOCATION");
			assertThat(sent.message().location().placeName()).isEqualTo("Công viên 23/9");
			assertThat(queries.detail(seller, conversation.id()).lastMessagePreview()).isEqualTo("[Vị trí]");
		}

		@Test
		@DisplayName("FR-NT-01: a message to a user who is offline becomes a notification that opens the conversation")
		void anOfflineRecipientGetsANotification() {
			Conversation conversation = open();

			send(conversation, buyer, "Còn hàng không bạn?");

			await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
				var rows = notifications.pageOf(seller, null, 10);
				assertThat(rows).hasSize(1);
				assertThat(rows.getFirst().deepLink()).isEqualTo("app://conversations/" + conversation.id());
				assertThat(rows.getFirst().body()).isEqualTo("Còn hàng không bạn?");
			});
			assertThat(notifications.pageOf(buyer, null, 10)).isEmpty();
		}

	}

	@Nested
	@DisplayName("FR-CH-11: the send lock")
	class Locking {

		@Test
		@DisplayName("after SOLD both sides can still write; after REMOVED nobody can")
		void soldStaysOpenRemovedCloses() {
			Conversation conversation = open();

			givenListingStatus("SOLD");
			assertThat(send(conversation, buyer, "Mình đã nhận hàng").created()).isTrue();
			assertThat(queries.detail(seller, conversation.id()).sendOpen()).isTrue();

			givenListingStatus("REMOVED");
			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> send(conversation, seller, "Cảm ơn bạn"));
			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_CLOSED);
			ConversationView header = queries.detail(seller, conversation.id());
			assertThat(header.sendOpen()).isFalse();
			assertThat(header.closedNotice()).isNotBlank().doesNotContainIgnoringCase("chặn");
			assertThat(queries.messages(seller, conversation.id(), MessageWindow.newest())).hasSize(1);
		}

		@Test
		@DisplayName("a block either way closes it with the same neutral answer")
		void aBlockClosesIt() {
			Conversation conversation = open();
			when(blocks.isBlockedEitherWay(buyer, seller)).thenReturn(true);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> send(conversation, buyer, "xin chào"));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_CLOSED);
			assertThat(error.getMessage()).doesNotContainIgnoringCase("block");
		}

		@Test
		@DisplayName("FR-ID-14: after the block is lifted the conversation can be written in again")
		void unblockingReopensIt() {
			Conversation conversation = open();
			when(blocks.isBlockedEitherWay(buyer, seller)).thenReturn(true);
			assertThat(catchThrowableOfType(BusinessException.class, () -> send(conversation, buyer, "xin chào"))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_CLOSED);

			when(blocks.isBlockedEitherWay(buyer, seller)).thenReturn(false);

			assertThat(send(conversation, buyer, "xin chào lại").created()).isTrue();
			assertThat(queries.detail(buyer, conversation.id()).sendOpen()).isTrue();
		}

		@Test
		@DisplayName("a suspended, banned or deleted member closes it")
		void aRestrictedMemberClosesIt() {
			Conversation conversation = open();
			when(standing.isActiveWithVerifiedPhone(seller)).thenReturn(false);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> send(conversation, buyer, "xin chào"));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_CLOSED);
			verify(listingCounters, never()).incrementChatCount(UUID.randomUUID());
		}

	}

	@Nested
	@DisplayName("UC-CH-05 and FR-CH-07: reading and syncing")
	class ReadingAndSyncing {

		@Test
		@DisplayName("FR-CH-06: opening the conversation sets the reader's unread count to 0")
		void readingClearsTheUnreadCount() {
			Conversation conversation = open();
			send(conversation, buyer, "một");
			long newest = send(conversation, buyer, "hai").message().id();

			Participant read = readService.markRead(seller, conversation.id(), newest);

			assertThat(read.unreadCount()).isZero();
			assertThat(read.lastReadMessageId()).isEqualTo(newest);
			assertThat(queries.detail(seller, conversation.id()).unreadCount()).isZero();
		}

		@Test
		@DisplayName("FR-CH-06: a marker that goes backwards is ignored")
		void anOlderMarkerChangesNothing() {
			Conversation conversation = open();
			long first = send(conversation, buyer, "một").message().id();
			long second = send(conversation, buyer, "hai").message().id();
			readService.markRead(seller, conversation.id(), second);

			Participant after = readService.markRead(seller, conversation.id(), first);

			assertThat(after.lastReadMessageId()).isEqualTo(second);
		}

		@Test
		void aMarkerBeyondTheNewestIsTheNewest() {
			Conversation conversation = open();
			long newest = send(conversation, buyer, "một").message().id();

			Participant read = readService.markRead(seller, conversation.id(), newest + 1_000);

			assertThat(read.lastReadMessageId()).isEqualTo(newest);
		}

		@Test
		@DisplayName("FR-CH-07: the messages missed after a checkpoint come in order, once")
		void syncReturnsTheMissedMessages() {
			Conversation conversation = open();
			long first = send(conversation, buyer, "một").message().id();
			long second = send(conversation, seller, "hai").message().id();
			long third = send(conversation, buyer, "ba").message().id();

			SyncResult afterFirst = queries.sync(seller, first);

			assertThat(afterFirst.messages()).extracting(MessageView::id).containsExactly(second, third);
			assertThat(afterFirst.hasMore()).isFalse();
			assertThat(queries.sync(seller, third).messages()).isEmpty();
		}

		@Test
		@DisplayName("FR-CH-07: an unknown checkpoint is 400 INVALID_CURSOR, and a stranger syncs nothing of others")
		void anUnknownCheckpointIsRejected() {
			Conversation conversation = open();
			long newest = send(conversation, buyer, "một").message().id();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> queries.sync(seller, newest + 1_000_000));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.INVALID_CURSOR);
			assertThat(queries.sync(UUID.randomUUID(), 0).messages()).isEmpty();
		}

		@Test
		void messagesPageBackwardsAndRefuseBothBounds() {
			Conversation conversation = open();
			List<Long> ids = java.util.stream.IntStream.range(0, 4)
				.mapToObj(i -> send(conversation, buyer, "m" + i).message().id())
				.toList();

			assertThat(queries.messages(buyer, conversation.id(), new MessageWindow(ids.get(3), null, 2)))
				.extracting(MessageView::id)
				.containsExactly(ids.get(1), ids.get(2));
			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> queries.messages(buyer, conversation.id(), new MessageWindow(5L, 1L, null)));
			assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		void aStrangerCannotReadOrOpenTheHeader() {
			Conversation conversation = open();
			UUID stranger = UUID.randomUUID();

			assertThat(catchThrowableOfType(BusinessException.class,
					() -> queries.messages(stranger, conversation.id(), MessageWindow.newest()))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
			assertThat(catchThrowableOfType(BusinessException.class, () -> queries.detail(stranger, conversation.id()))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
			assertThat(catchThrowableOfType(BusinessException.class,
					() -> readService.markRead(stranger, conversation.id(), 1))
				.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
		}

	}

	@Nested
	@DisplayName("FR-CH-04: image messages")
	class Images {

		@Test
		@DisplayName("an uploaded and processed image is sent once; the counterpart then sees it through short-lived links")
		void anImageMessageEndToEnd() {
			Conversation conversation = open();
			var ticket = imageService.requestUpload(buyer, conversation.id(), "image/jpeg", 100_000);
			String originalKey = chatImages.findById(ticket.imageId()).orElseThrow().originalKey();
			storage.simulateUpload(originalKey, "image/jpeg", TestImages.jpeg(800, 600));
			imageService.completeUpload(buyer, ticket.imageId());
			await().atMost(Duration.ofSeconds(20))
				.until(() -> chatImages.findById(ticket.imageId()).orElseThrow().isProcessed());

			Sent sent = messageService.sendImages(new Request(buyer, conversation.id(), UUID.randomUUID(), false),
					List.of(ticket.imageId()));

			assertThat(sent.message().type()).isEqualTo("IMAGE");
			assertThat(sent.message().images()).hasSize(1);
			assertThat(sent.message().images().getFirst().url())
				.contains("chat-images/" + ticket.imageId() + "_1080.webp");
			assertThat(sent.message().images().getFirst().width()).isEqualTo(800);
			assertThat(queries.detail(seller, conversation.id()).lastMessagePreview()).isEqualTo("[Hình ảnh]");
			assertThat(imageService.view(seller, ticket.imageId()).url()).isNotNull();
			BusinessException again = catchThrowableOfType(BusinessException.class,
					() -> messageService.sendImages(new Request(buyer, conversation.id(), UUID.randomUUID(), false),
							List.of(ticket.imageId())));
			assertThat(again.getCode()).isEqualTo(ErrorCodes.IMAGE_INVALID);
		}

	}

}
