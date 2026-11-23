package vn.edu.uit.nearby.nearby_backend.chat.domain;

import java.util.UUID;

/**
 * One member's read state (FR-CH-06): the highest message id they have read and the
 * number of messages after it that someone else sent.
 */
public record Participant(UUID conversationId, UUID userId, ParticipantRole role, long lastReadMessageId,
		int unreadCount) {

	public Participant {
		if (lastReadMessageId < 0 || unreadCount < 0) {
			throw new IllegalArgumentException("The read marker and the unread count cannot be negative.");
		}
	}

	public static Participant joined(UUID conversationId, UUID userId, ParticipantRole role) {
		return new Participant(conversationId, userId, role, 0, 0);
	}

	/**
	 * The counterpart sent a message: one more is unread.
	 */
	public Participant receivedMessage() {
		return new Participant(conversationId, userId, role, lastReadMessageId, unreadCount + 1);
	}

	/**
	 * @param messageId the highest message the reader has seen
	 * @param unreadAfterMarker messages after {@code messageId} that the reader did not
	 * send, counted from the stored messages
	 * @return the same state when the marker is not newer than the stored one (a marker
	 * that goes backwards is ignored, FR-CH-06), otherwise the new marker and count
	 */
	public Participant readUpTo(long messageId, int unreadAfterMarker) {
		if (messageId <= lastReadMessageId) {
			return this;
		}
		return new Participant(conversationId, userId, role, messageId, unreadAfterMarker);
	}

	public boolean hasUnread() {
		return unreadCount > 0;
	}

}
