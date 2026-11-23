package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Sent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository.Inserted;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

@ExtendWith(MockitoExtension.class)
class MessageServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	private static final UUID BUYER = UUID.randomUUID();

	private static final UUID SELLER = UUID.randomUUID();

	private final Conversation conversation = new Conversation(UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER);

	private final UUID clientMsgId = UUID.randomUUID();

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	@Mock
	MessageWriter writer;

	@Mock
	SendGuard guard;

	@Mock
	RateLimiter rateLimiter;

	@Mock
	ChatParameters parameters;

	@Mock
	MessageViews views;

	@Mock
	ChatImageService chatImages;

	private MessageService service;

	@BeforeEach
	void setUp() {
		service = new MessageService(conversations, messages, writer, guard, rateLimiter, parameters, views,
				chatImages);
	}

	private void givenMember() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(messages.findBySenderAndClientMsgId(BUYER, clientMsgId)).thenReturn(Optional.empty());
	}

	private void givenLimits() {
		when(parameters.maxMessageChars()).thenReturn(1_000);
		givenRateLimit();
	}

	private void givenRateLimit() {
		when(parameters.messagesPerMinute()).thenReturn(30);
	}

	private Request request(boolean viaSocket) {
		return new Request(BUYER, conversation.id(), clientMsgId, viaSocket);
	}

	private StoredMessage stored(UUID conversationId) {
		return new StoredMessage(7, conversationId, BUYER, MessageType.TEXT, "hi", null, clientMsgId, NOW);
	}

	@Test
	@DisplayName("FR-CH-02 E4: a stranger gets 404 CONVERSATION_NOT_FOUND, like an unknown conversation")
	void aStrangerIsNotAMember() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		Request stranger = new Request(UUID.randomUUID(), conversation.id(), clientMsgId, false);

		BusinessException error = catchThrowableOfType(BusinessException.class, () -> service.sendText(stranger, "hi"));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
		verifyNoInteractions(writer, rateLimiter, guard);
	}

	@Test
	void anUnknownConversationIsNotFound() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.empty());

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendText(request(false), "hi"));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
	}

	@Test
	@DisplayName("UC-CH-02 A3: a repeated client message id returns the stored message before the lock and the limit")
	void aRepeatReturnsTheStoredMessage() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		StoredMessage earlier = stored(conversation.id());
		when(messages.findBySenderAndClientMsgId(BUYER, clientMsgId)).thenReturn(Optional.of(earlier));
		MessageView view = org.mockito.Mockito.mock(MessageView.class);
		when(views.of(earlier)).thenReturn(view);

		Sent sent = service.sendText(request(true), "hi");

		assertThat(sent.created()).isFalse();
		assertThat(sent.message()).isSameAs(view);
		verifyNoInteractions(writer, rateLimiter, guard);
	}

	@Test
	void aClientMessageIdOfAnotherConversationIsRefused() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(messages.findBySenderAndClientMsgId(BUYER, clientMsgId))
			.thenReturn(Optional.of(stored(UUID.randomUUID())));

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendText(request(false), "hi"));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		verifyNoInteractions(writer);
	}

	@Test
	@DisplayName("FR-CH-11: a locked conversation is 403 CONVERSATION_CLOSED, checked before the content and the limit")
	void aLockedConversationIsClosed() {
		givenMember();
		doThrow(new OperationNotAllowedException(ErrorCodes.CONVERSATION_CLOSED, "closed")).when(guard)
			.requireOpen(conversation);

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendText(request(false), "x".repeat(5_000)));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_CLOSED);
		verifyNoInteractions(writer, rateLimiter);
	}

	@Test
	@DisplayName("FR-CH-03: 1 001 characters is 422 MESSAGE_TOO_LONG and costs no rate budget")
	void aTooLongTextIsRefusedBeforeTheLimit() {
		givenMember();
		when(parameters.maxMessageChars()).thenReturn(1_000);

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendText(request(false), "x".repeat(1_001)));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.MESSAGE_TOO_LONG);
		verifyNoInteractions(rateLimiter, writer);
	}

	@Test
	void anEmptyTextIsInvalid() {
		givenMember();
		when(parameters.maxMessageChars()).thenReturn(1_000);

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendText(request(false), "   "));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
	}

	@Test
	@DisplayName("FR-CH-03: the 31st message in a minute is 429 CHAT_RATE_LIMITED with the wait time")
	void theRateLimitAnswers429() {
		givenMember();
		givenLimits();
		String key = "chat-rate:" + BUYER;
		when(rateLimiter.tryAcquire(key, 30, Duration.ofMinutes(1))).thenReturn(false);
		when(rateLimiter.remainingWindow(key)).thenReturn(Duration.ofSeconds(42));

		RateLimitExceededException error = catchThrowableOfType(RateLimitExceededException.class,
				() -> service.sendText(request(false), "hi"));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.CHAT_RATE_LIMITED);
		assertThat(error.getRetryAfter()).contains(Duration.ofSeconds(42));
		verifyNoInteractions(writer);
	}

	@Test
	@DisplayName("UC-CH-02 step 3: membership, repeat, lock, content, rate limit, then the write")
	void theChecksRunInOrder() {
		givenMember();
		givenLimits();
		when(rateLimiter.tryAcquire(any(), eq(30), any())).thenReturn(true);
		StoredMessage message = stored(conversation.id());
		when(writer.write(eq(conversation), eq(BUYER), any(), eq(clientMsgId), anyBoolean()))
			.thenReturn(new Inserted(message, true));

		Sent sent = service.sendText(request(true), "Còn hàng không bạn?");

		assertThat(sent.created()).isTrue();
		InOrder order = inOrder(messages, guard, rateLimiter, writer);
		order.verify(messages).findBySenderAndClientMsgId(BUYER, clientMsgId);
		order.verify(guard).requireOpen(conversation);
		order.verify(rateLimiter).tryAcquire(any(), eq(30), any());
		ArgumentCaptor<MessageContent> content = ArgumentCaptor.forClass(MessageContent.class);
		order.verify(writer).write(eq(conversation), eq(BUYER), content.capture(), eq(clientMsgId), eq(true));
		assertThat(content.getValue()).isEqualTo(new MessageContent.Text("Còn hàng không bạn?"));
	}

	@Test
	@DisplayName("a race on the unique key returns the stored message and is not a new message")
	void aRaceOnTheClientMessageIdIsARepeat() {
		givenMember();
		givenLimits();
		when(rateLimiter.tryAcquire(any(), eq(30), any())).thenReturn(true);
		when(writer.write(any(), any(), any(), any(), anyBoolean()))
			.thenReturn(new Inserted(stored(conversation.id()), false));

		assertThat(service.sendText(request(false), "hi").created()).isFalse();
	}

	@Test
	@DisplayName("FR-CH-04: the images are chosen after the lock and the content checks, then attached to the new message")
	void imagesAreAttachedToTheNewMessage() {
		givenMember();
		givenRateLimit();
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(chatImages.readyImagesOf(BUYER, conversation.id(), ids))
			.thenReturn(List.of(new ChatImage("a", "a-t", 10, 10), new ChatImage("b", "b-t", 20, 20)));
		when(rateLimiter.tryAcquire(any(), eq(30), any())).thenReturn(true);
		StoredMessage message = stored(conversation.id());
		when(writer.write(eq(conversation), eq(BUYER), any(), eq(clientMsgId), anyBoolean()))
			.thenReturn(new Inserted(message, true));
		when(views.of(message)).thenReturn(
				new MessageView(7, conversation.id(), BUYER, "IMAGE", null, null, null, null, clientMsgId, NOW));

		Sent sent = service.sendImages(request(false), ids);

		assertThat(sent.created()).isTrue();
		InOrder order = inOrder(guard, chatImages, writer);
		order.verify(guard).requireOpen(conversation);
		order.verify(chatImages).readyImagesOf(BUYER, conversation.id(), ids);
		order.verify(writer).write(eq(conversation), eq(BUYER), any(), eq(clientMsgId), eq(false));
		order.verify(chatImages).attach(ids, 7);
	}

	@Test
	@DisplayName("FR-CH-02: a repeat of an image send attaches nothing a second time")
	void aRepeatedImageSendAttachesNothing() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		StoredMessage earlier = stored(conversation.id());
		when(messages.findBySenderAndClientMsgId(BUYER, clientMsgId)).thenReturn(Optional.of(earlier));
		when(views.of(earlier)).thenReturn(
				new MessageView(7, conversation.id(), BUYER, "IMAGE", null, null, null, null, clientMsgId, NOW));

		Sent sent = service.sendImages(request(false), List.of(UUID.randomUUID()));

		assertThat(sent.created()).isFalse();
		verify(chatImages, never()).attach(any(), org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	@DisplayName("FR-CH-05: a meeting point outside Vietnam is 400 and nothing is written")
	void aPointOutsideVietnamIsInvalid() {
		givenMember();

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service.sendMeetingPoint(request(false), 48.85, 2.35, "Paris"));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		verify(writer, never()).write(any(), any(), any(), any(), anyBoolean());
	}

	@Test
	void aMeetingPointInsideVietnamIsWritten() {
		givenMember();
		givenRateLimit();
		when(rateLimiter.tryAcquire(any(), eq(30), any())).thenReturn(true);
		when(writer.write(any(), any(), any(), any(), anyBoolean()))
			.thenReturn(new Inserted(stored(conversation.id()), true));

		service.sendMeetingPoint(request(false), 10.77, 106.70, "Công viên 23/9");

		ArgumentCaptor<MessageContent> content = ArgumentCaptor.forClass(MessageContent.class);
		verify(writer).write(eq(conversation), eq(BUYER), content.capture(), eq(clientMsgId), eq(false));
		assertThat(content.getValue()).isEqualTo(new MessageContent.MeetingPoint(10.77, 106.70, "Công viên 23/9"));
	}

}
