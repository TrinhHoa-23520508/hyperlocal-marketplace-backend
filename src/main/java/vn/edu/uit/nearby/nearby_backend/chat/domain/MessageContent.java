package vn.edu.uit.nearby.nearby_backend.chat.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.VietnamBounds;

/**
 * What a message carries, validated by the field rules of group CHT
 * ({@code tab:fieldrules}): text of 1 to {@code chat.max_message_chars} characters, 1 to
 * 5 images, a meeting point inside Vietnam, or a system note (FR-CH-03..05, FR-CH-08).
 */
public sealed interface MessageContent {

	/**
	 * Characters shown in the conversation list
	 * ({@code conversations.last_message_preview}).
	 */
	int PREVIEW_CHARS = 100;

	/**
	 * FR-CH-04.
	 */
	int MAX_IMAGES = 5;

	/**
	 * Same limit as the listing's meeting spot text (FR-LS-01).
	 */
	int MAX_PLACE_NAME_CHARS = 100;

	String IMAGE_PREVIEW = "[Hình ảnh]";

	String LOCATION_PREVIEW = "[Vị trí]";

	MessageType type();

	/**
	 * @return {@code messages.body}; null when the type has none
	 */
	@Nullable String body();

	/**
	 * @return {@code messages.payload} (a list for IMAGE, a map otherwise); null for TEXT
	 */
	@Nullable Object payload();

	String preview();

	/**
	 * FR-CH-03.
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for empty text
	 * @throws UnprocessableRequestException {@code MESSAGE_TOO_LONG} over
	 * {@code maxChars}
	 */
	static Text text(@Nullable String raw, int maxChars) {
		String cleaned = ChatText.multiLine(raw);
		if (cleaned.isEmpty()) {
			throw InvalidRequestException.invalidField("body", "The message cannot be empty.");
		}
		if (ChatText.lengthOf(cleaned) > maxChars) {
			throw new UnprocessableRequestException(ErrorCodes.MESSAGE_TOO_LONG,
					"A message can have at most " + maxChars + " characters.");
		}
		return new Text(cleaned);
	}

	/**
	 * FR-CH-04.
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for no image or more than
	 * {@value #MAX_IMAGES}
	 */
	static Images images(List<ChatImage> images) {
		if (images.isEmpty() || images.size() > MAX_IMAGES) {
			throw InvalidRequestException.invalidField("images", "A message carries 1 to " + MAX_IMAGES + " images.");
		}
		return new Images(List.copyOf(images));
	}

	/**
	 * FR-CH-05.
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for a point outside
	 * Vietnam or a missing place name
	 */
	static MeetingPoint meetingPoint(double latitude, double longitude, @Nullable String placeName) {
		if (!VietnamBounds.contains(latitude, longitude)) {
			throw InvalidRequestException.invalidField("point", "The meeting point must be inside Vietnam.");
		}
		String cleaned = ChatText.singleLine(placeName);
		if (cleaned.isEmpty() || ChatText.lengthOf(cleaned) > MAX_PLACE_NAME_CHARS) {
			throw InvalidRequestException.invalidField("placeName",
					"The place name needs 1 to " + MAX_PLACE_NAME_CHARS + " characters.");
		}
		return new MeetingPoint(latitude, longitude, cleaned);
	}

	/**
	 * FR-CH-08: written by the system, never by a user, so a violation is a bug.
	 * @param kind what happened, such as {@code OFFER_CREATED}
	 * @param text what members read
	 * @throws IllegalArgumentException when the kind or text is blank or too long
	 */
	static SystemNote systemNote(String kind, String text, Map<String, Object> facts) {
		if (kind.isBlank() || kind.length() > SystemNote.MAX_KIND_CHARS || text.isBlank()
				|| ChatText.lengthOf(text) > SystemNote.MAX_TEXT_CHARS) {
			throw new IllegalArgumentException("A system note needs a kind and a text within the column sizes.");
		}
		return new SystemNote(kind, text, Map.copyOf(facts));
	}

	record Text(String text) implements MessageContent {

		@Override
		public MessageType type() {
			return MessageType.TEXT;
		}

		@Override
		public String body() {
			return text;
		}

		@Override
		public @Nullable Object payload() {
			return null;
		}

		@Override
		public String preview() {
			return ChatText.cut(text.replace('\n', ' '), PREVIEW_CHARS);
		}

	}

	record Images(List<ChatImage> images) implements MessageContent {

		@Override
		public MessageType type() {
			return MessageType.IMAGE;
		}

		@Override
		public @Nullable String body() {
			return null;
		}

		@Override
		public Object payload() {
			return images.stream().map(image -> {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("key", image.key());
				entry.put("thumbKey", image.thumbKey());
				entry.put("w", image.width());
				entry.put("h", image.height());
				return entry;
			}).toList();
		}

		@Override
		public String preview() {
			return IMAGE_PREVIEW;
		}

	}

	record MeetingPoint(double latitude, double longitude, String placeName) implements MessageContent {

		@Override
		public MessageType type() {
			return MessageType.LOCATION;
		}

		@Override
		public @Nullable String body() {
			return null;
		}

		@Override
		public Object payload() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("lat", latitude);
			payload.put("lng", longitude);
			payload.put("placeName", placeName);
			return payload;
		}

		@Override
		public String preview() {
			return LOCATION_PREVIEW;
		}

	}

	record SystemNote(String kind, String text, Map<String, Object> facts) implements MessageContent {

		/**
		 * {@code messages.body} is varchar(1000).
		 */
		public static final int MAX_TEXT_CHARS = 1_000;

		public static final int MAX_KIND_CHARS = 40;

		@Override
		public MessageType type() {
			return MessageType.SYSTEM;
		}

		@Override
		public String body() {
			return text;
		}

		@Override
		public Object payload() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("kind", kind);
			payload.put("facts", facts);
			return payload;
		}

		@Override
		public String preview() {
			return ChatText.cut(text.replace('\n', ' '), PREVIEW_CHARS);
		}

	}

}
