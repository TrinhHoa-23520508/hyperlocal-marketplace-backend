package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository.Inserted;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

/**
 * UC-CH-02..04: a member sends a text, an image message or a meeting point. REST and
 * STOMP both end here, so both follow the same rules and answer the same codes.
 */
@Service
public class MessageService {

	private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

	private final ConversationRepository conversations;

	private final MessageRepository messages;

	private final MessageWriter writer;

	private final SendGuard guard;

	private final RateLimiter rateLimiter;

	private final ChatParameters parameters;

	private final MessageViews views;

	private final ChatImageService chatImages;

	MessageService(ConversationRepository conversations, MessageRepository messages, MessageWriter writer,
			SendGuard guard, RateLimiter rateLimiter, ChatParameters parameters, MessageViews views,
			ChatImageService chatImages) {
		this.conversations = conversations;
		this.messages = messages;
		this.writer = writer;
		this.guard = guard;
		this.rateLimiter = rateLimiter;
		this.parameters = parameters;
		this.views = views;
		this.chatImages = chatImages;
	}

	/**
	 * @param viaSocket whether the sender used STOMP, so the sender gets an
	 * acknowledgement on {@code /user/queue/acks}
	 */
	public record Request(UUID senderId, UUID conversationId, UUID clientMsgId, boolean viaSocket) {
	}

	/**
	 * @param created false when the same client message id was stored before: the earlier
	 * message is returned and nothing is sent again (UC-CH-02 A3)
	 */
	public record Sent(MessageView message, boolean created) {
	}

	/**
	 * @throws ResourceNotFoundException {@code CONVERSATION_NOT_FOUND} when the sender is
	 * not a member
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException
	 * {@code CONVERSATION_CLOSED}
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException
	 * {@code MESSAGE_TOO_LONG}
	 * @throws RateLimitExceededException {@code CHAT_RATE_LIMITED}
	 */
	@Transactional
	public Sent sendText(Request request, @Nullable String body) {
		return send(request, () -> MessageContent.text(body, parameters.maxMessageChars()));
	}

	/**
	 * FR-CH-04: one IMAGE message with 1 to 5 images the sender uploaded and the pipeline
	 * accepted.
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException
	 * {@code IMAGE_INVALID} when an image is not the sender's READY, unused image of this
	 * conversation
	 */
	@Transactional
	public Sent sendImages(Request request, List<UUID> imageIds) {
		Sent sent = send(request, () -> MessageContent
			.images(chatImages.readyImagesOf(request.senderId(), request.conversationId(), imageIds)));
		if (sent.created()) {
			chatImages.attach(imageIds, sent.message().id());
		}
		return sent;
	}

	@Transactional
	public Sent sendMeetingPoint(Request request, double latitude, double longitude, @Nullable String placeName) {
		return send(request, () -> MessageContent.meetingPoint(latitude, longitude, placeName));
	}

	/**
	 * The order of UC-CH-02 step 3: membership, a repeat of an earlier send, the send
	 * lock, the content rules, then the rate limit. A repeat is answered before the lock
	 * and the limit, so a retry after a timeout always gets the stored message and costs
	 * nothing.
	 */
	Sent send(Request request, Supplier<MessageContent> content) {
		Conversation conversation = conversations.findById(request.conversationId())
			.filter(candidate -> candidate.isMember(request.senderId()))
			.orElseThrow(
					() -> new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found."));
		var earlier = messages.findBySenderAndClientMsgId(request.senderId(), request.clientMsgId());
		if (earlier.isPresent()) {
			return repeatOf(conversation, earlier.get());
		}
		guard.requireOpen(conversation);
		MessageContent validContent = content.get();
		acquireRate(request.senderId());
		Inserted stored = writer.write(conversation, request.senderId(), validContent, request.clientMsgId(),
				request.viaSocket());
		return new Sent(views.of(stored.message()), stored.created());
	}

	private Sent repeatOf(Conversation conversation, StoredMessage earlier) {
		if (!earlier.conversationId().equals(conversation.id())) {
			throw InvalidRequestException.invalidField("clientMsgId", "This id was used for another message.");
		}
		return new Sent(views.of(earlier), false);
	}

	private void acquireRate(UUID senderId) {
		String key = RedisKeyPrefixes.CHAT_MESSAGE_RATE + senderId;
		if (!rateLimiter.tryAcquire(key, parameters.messagesPerMinute(), RATE_WINDOW)) {
			throw new RateLimitExceededException(ErrorCodes.CHAT_RATE_LIMITED,
					"You are sending messages too fast. Please wait a moment.", rateLimiter.remainingWindow(key));
		}
	}

}
