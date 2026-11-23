package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.ReportableMessages;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

@Service
class ReportableMessagesService implements ReportableMessages {

	private final MessageRepository messages;

	private final ConversationRepository conversations;

	ReportableMessagesService(MessageRepository messages, ConversationRepository conversations) {
		this.messages = messages;
		this.conversations = conversations;
	}

	@Override
	@Transactional(readOnly = true)
	public MessageReportContext contextOf(long messageId, UUID reporterId) {
		StoredMessage message = messages.findById(messageId).orElseThrow(ReportableMessagesService::notFound);
		Conversation conversation = conversations.findById(message.conversationId())
			.filter(candidate -> candidate.isMember(reporterId))
			.orElseThrow(ReportableMessagesService::notFound);
		if (message.senderId() == null) {
			throw InvalidRequestException.invalidField("messageId", "A system message cannot be reported.");
		}
		if (message.senderId().equals(reporterId)) {
			throw new UnprocessableRequestException(ErrorCodes.SELF_ACTION_NOT_ALLOWED,
					"You cannot report your own message.");
		}
		List<ReportedMessage> previous = messages.before(conversation.id(), messageId, CONTEXT_MESSAGES)
			.stream()
			.map(ReportableMessagesService::reportedOf)
			.toList();
		return new MessageReportContext(reportedOf(message), conversation.listingId(), message.senderId(), previous);
	}

	private static ReportedMessage reportedOf(StoredMessage message) {
		return new ReportedMessage(message.id(), message.conversationId(), message.senderId(), message.type().name(),
				summaryOf(message), message.createdAt());
	}

	private static String summaryOf(StoredMessage message) {
		return switch (message.type()) {
			case TEXT, SYSTEM -> message.body();
			case IMAGE -> MessageContent.IMAGE_PREVIEW;
			case LOCATION -> MessageContent.LOCATION_PREVIEW;
		};
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found.");
	}

}
