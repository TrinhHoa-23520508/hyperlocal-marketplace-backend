package vn.edu.uit.nearby.nearby_backend.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * A member wrote a message (SRS appendix B, FR-CH-02). Notification pushes it to the
 * recipient when they are offline (UC-SY-16); Moderation screens it for scam patterns
 * from R2 (FR-CH-10). SYSTEM messages do not publish it.
 *
 * @param messageId the sync checkpoint of the message
 * @param preview the first 100 characters, or "[Hình ảnh]" / "[Vị trí]"
 */
public record MessageSent(long messageId, UUID conversationId, UUID listingId, UUID senderId, UUID recipientId,
		String type, String preview, Instant sentAt) {

}
