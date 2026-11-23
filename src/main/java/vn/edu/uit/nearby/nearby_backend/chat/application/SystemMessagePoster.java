package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository.Inserted;

/**
 * FR-CH-08: posts the system message of a transaction event into the conversation of its
 * (listing, buyer), creating the conversation first when an offer is the first contact
 * (UC-TX-01 step 7, register row CH-W5).
 *
 * <p>
 * Internal to the chat module on purpose: the listeners of Transaction and Payment events
 * (register row CH-W1) will live in this module and call it, so no other module learns
 * about chat internals. A system message is never refused: it ignores the send lock and
 * the rate limit, and the caller's transaction must not fail because of it (FR-CH-08
 * "không chặn chuyển trạng thái giao dịch").
 */
@Service
public class SystemMessagePoster {

	private final ConversationService conversations;

	private final ConversationRepository conversationRows;

	private final MessageWriter writer;

	SystemMessagePoster(ConversationService conversations, ConversationRepository conversationRows,
			MessageWriter writer) {
		this.conversations = conversations;
		this.conversationRows = conversationRows;
		this.writer = writer;
	}

	/**
	 * One system message to post.
	 *
	 * @param kind what happened, such as {@code OFFER_CREATED}
	 * @param text what the members read
	 * @param sourceEventId the id of the domain event; the same id posts at most once, so
	 * a replay from the outbox adds nothing
	 */
	public record SystemMessage(UUID listingId, UUID buyerId, UUID sellerId, String kind, String text,
			Map<String, Object> facts, UUID sourceEventId) {
	}

	/**
	 * @return false when this event had already been posted
	 */
	@Transactional
	public boolean post(SystemMessage message) {
		Conversation conversation = conversations.openForTrade(message.listingId(), message.buyerId(),
				message.sellerId());
		return write(conversation, message);
	}

	/**
	 * Posts into a conversation that must already exist, such as the one a sale marked to
	 * a chat partner belongs to; the seller of the message is not used.
	 * @return false when the conversation does not exist or the event had been posted
	 */
	@Transactional
	public boolean postIfConversationExists(SystemMessage message) {
		return conversationRows.findByListingAndBuyer(message.listingId(), message.buyerId())
			.map(conversation -> write(conversation, message))
			.orElse(false);
	}

	private boolean write(Conversation conversation, SystemMessage message) {
		MessageContent content = MessageContent.systemNote(message.kind(), message.text(), message.facts());
		return writer.write(conversation, null, content, message.sourceEventId(), false).created();
	}

}
