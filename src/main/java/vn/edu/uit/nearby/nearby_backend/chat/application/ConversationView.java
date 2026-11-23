package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A conversation as the app shows it, in the list (A17) and in the header of the chat
 * screen (A18).
 *
 * @param listing null only if the listing row is gone
 * @param counterpart the other member; a deleted account shows "Người dùng đã xoá"
 * @param lastMessagePreview the first 100 characters, "[Hình ảnh]" or "[Vị trí]"
 * @param sendOpen false when sending is locked (FR-CH-11); reading stays possible
 * @param closedNotice the neutral text to show when {@code sendOpen} is false; it never
 * says whether someone blocked the other
 */
public record ConversationView(UUID id, @Nullable ListingBrief listing, @Nullable CounterpartBrief counterpart,
		@Nullable String lastMessagePreview, @Nullable Instant lastMessageAt,
		@Schema(description = "Messages after the reader's marker that the counterpart sent") int unreadCount,
		boolean sendOpen, @Nullable String closedNotice, Instant sortAt) {

	public record ListingBrief(UUID id, String title, @Schema(example = "ACTIVE") String status, long priceVnd,
			@Nullable String coverThumbnailUrl) {
	}

	public record CounterpartBrief(UUID id, String displayName, @Nullable String avatarUrl, boolean deleted) {
	}

}
