package vn.edu.uit.nearby.nearby_backend.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What Moderation needs to open a report about a chat message (FR-CH-12, UC-SP-01,
 * register row CH-W2): the message and the 20 messages before it as context, shown to the
 * moderator on W03. Moderation owns the {@code reports} table and calls this when the
 * reporter long-presses a message and chooses "Báo cáo".
 */
public interface ReportableMessages {

	/**
	 * FR-CH-12: "Đính kèm 20 tin trước đó làm ngữ cảnh".
	 */
	int CONTEXT_MESSAGES = 20;

	/**
	 * @param reporterId must be a member of the message's conversation
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException
	 * {@code CONVERSATION_NOT_FOUND} for an unknown message or one the reporter cannot
	 * see, so a conversation's existence stays private (FR-ID-18)
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException
	 * {@code SELF_ACTION_NOT_ALLOWED} for the reporter's own message (FR-CH-12: no report
	 * item on it)
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException
	 * {@code VALIDATION_FAILED} for a SYSTEM message, which no user wrote
	 */
	MessageReportContext contextOf(long messageId, UUID reporterId);

	/**
	 * @param reportedUserId the author of the message, the user the report is about
	 * @param previous up to {@value #CONTEXT_MESSAGES} messages before it, oldest first
	 */
	record MessageReportContext(ReportedMessage message, UUID listingId, UUID reportedUserId,
			List<ReportedMessage> previous) {
	}

	/**
	 * @param summary the text of a TEXT or SYSTEM message, otherwise "[Hình ảnh]" or "[Vị
	 * trí]"
	 */
	record ReportedMessage(long id, UUID conversationId, @Nullable UUID senderId, String type, String summary,
			Instant createdAt) {
	}

}
