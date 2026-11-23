package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A message as the app receives it, over REST and over the {@code /user/queue/messages}
 * queue. Exactly one of {@code body} (TEXT, SYSTEM), {@code images} (IMAGE) and
 * {@code location} (LOCATION) is set, according to {@code type}.
 */
public record MessageView(
		@Schema(description = "Increases with every message of every conversation: the sync checkpoint (FR-CH-07)",
				example = "1042") long id,
		UUID conversationId, @Schema(description = "Null for SYSTEM messages") @Nullable UUID senderId,
		@Schema(allowableValues = {
				"TEXT", "IMAGE", "LOCATION", "SYSTEM" }) String type,
		@Nullable String body,
		@Schema(description = "IMAGE: short-lived download links, usable only by the two members") @Nullable List<ImageView> images,
		@Schema(description = "LOCATION: the meeting point the two members agreed on") @Nullable LocationView location,
		@Schema(description = "SYSTEM: what happened, for the app to style the note") @Nullable SystemView system,
		@Schema(description = "The id the sender generated; absent on SYSTEM messages") @Nullable UUID clientMsgId,
		Instant createdAt){

	public record ImageView(String url, String thumbnailUrl, int width, int height) {
	}

	public record LocationView(double latitude, double longitude, String placeName) {
	}

	public record SystemView(String kind, Map<String, Object> facts) {
	}

}
