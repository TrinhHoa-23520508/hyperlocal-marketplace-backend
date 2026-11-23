package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Participant;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;

/**
 * UC-CH-05: opening a conversation or seeing a new message on screen moves the reader's
 * marker (FR-CH-06). A marker older than the stored one changes nothing; one beyond the
 * newest message counts as the newest.
 */
@Service
public class ReadService {

	private final ConversationRepository conversations;

	private final MessageRepository messages;

	private final ApplicationEventPublisher events;

	ReadService(ConversationRepository conversations, MessageRepository messages, ApplicationEventPublisher events) {
		this.conversations = conversations;
		this.messages = messages;
		this.events = events;
	}

	/**
	 * Tells the counterpart (read receipt) and the reader's own devices (badge) once it
	 * commits.
	 */
	record ReadRecorded(Conversation conversation, UUID readerId, long lastReadMessageId) {
	}

	/**
	 * @return the reader's state; unchanged when the marker did not move forward
	 * @throws ResourceNotFoundException {@code CONVERSATION_NOT_FOUND} for a stranger
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for a negative marker
	 */
	@Transactional
	public Participant markRead(UUID readerId, UUID conversationId, long lastReadMessageId) {
		if (lastReadMessageId < 0) {
			throw InvalidRequestException.invalidField("lastReadMessageId", "The marker cannot be negative.");
		}
		Conversation conversation = conversations.findById(conversationId)
			.filter(candidate -> candidate.isMember(readerId))
			.orElseThrow(
					() -> new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found."));
		long marker = Math.min(lastReadMessageId, messages.newestIdOf(conversationId));
		var moved = conversations.markRead(conversationId, readerId, marker);
		moved.ifPresent(participant -> events
			.publishEvent(new ReadRecorded(conversation, readerId, participant.lastReadMessageId())));
		return moved.orElseGet(() -> conversations.findParticipant(conversationId, readerId).orElseThrow());
	}

}
