package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.chat.MessageSent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository.Inserted;

/**
 * Saves one message and everything that follows from it in the caller's transaction: the
 * conversation's last-message fields, the other member's unread count, the
 * {@link MessageSent} event (outbox) and the after-commit delivery (FR-CH-02).
 */
@Component
class MessageWriter {

	private final MessageRepository messages;

	private final ConversationRepository conversations;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	MessageWriter(MessageRepository messages, ConversationRepository conversations, ApplicationEventPublisher events,
			Clock clock) {
		this.messages = messages;
		this.conversations = conversations;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * What happened inside the transaction, delivered to the members once it commits.
	 *
	 * @param viaSocket whether the sender used STOMP, so the sender gets an
	 * acknowledgement on {@code /user/queue/acks}
	 */
	record MessageStored(Conversation conversation, StoredMessage message, boolean viaSocket) {
	}

	/**
	 * @param senderId null for a SYSTEM message
	 * @return the stored message; {@code created} is false when the (sender, client
	 * message id) pair was stored before and nothing else happened
	 */
	Inserted write(Conversation conversation, @Nullable UUID senderId, MessageContent content,
			@Nullable UUID clientMsgId, boolean viaSocket) {
		Instant now = clock.instant();
		Inserted inserted = messages.insert(conversation.id(), senderId, content, clientMsgId, now);
		if (!inserted.created()) {
			return inserted;
		}
		StoredMessage message = inserted.message();
		conversations.recordLastMessage(conversation.id(), message.id(), now, content.preview());
		conversations.addUnreadForOthers(conversation.id(), senderId);
		if (senderId != null) {
			events.publishEvent(new MessageSent(message.id(), conversation.id(), conversation.listingId(), senderId,
					conversation.counterpartOf(senderId), message.type().name(), content.preview(), now));
		}
		events.publishEvent(new MessageStored(conversation, message, viaSocket));
		return inserted;
	}

}
