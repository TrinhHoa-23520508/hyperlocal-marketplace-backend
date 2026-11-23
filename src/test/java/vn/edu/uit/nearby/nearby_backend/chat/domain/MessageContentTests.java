package vn.edu.uit.nearby.nearby_backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent.Images;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent.MeetingPoint;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent.Text;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

class MessageContentTests {

	private static final int MAX_CHARS = 1_000;

	@Nested
	@DisplayName("FR-CH-03: text of 1 to 1 000 characters")
	class TextRules {

		@Test
		void oneAndAThousandCharactersAreAccepted() {
			assertThat(MessageContent.text("a", MAX_CHARS).body()).isEqualTo("a");
			assertThat(MessageContent.text("a".repeat(1_000), MAX_CHARS).body()).hasSize(1_000);
		}

		@Test
		@DisplayName("1 001 characters: 422 MESSAGE_TOO_LONG")
		void oneOverTheLimitIsTooLong() {
			assertThatThrownBy(() -> MessageContent.text("a".repeat(1_001), MAX_CHARS))
				.isInstanceOf(UnprocessableRequestException.class)
				.extracting(error -> ((BusinessException) error).getCode())
				.isEqualTo(ErrorCodes.MESSAGE_TOO_LONG);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   ", "\n\t ", "\u0000\u0007" })
		@DisplayName("empty or blank text: 400 VALIDATION_FAILED")
		void emptyTextIsInvalid(String raw) {
			assertThatThrownBy(() -> MessageContent.text(raw, MAX_CHARS)).isInstanceOf(InvalidRequestException.class)
				.extracting(error -> ((BusinessException) error).getCode())
				.isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		@DisplayName("characters are code points, so an emoji counts once")
		void countsCodePoints() {
			String emoji = "😀";

			assertThat(MessageContent.text(emoji.repeat(1_000), MAX_CHARS).body()).hasSize(2_000);
			assertThatThrownBy(() -> MessageContent.text(emoji.repeat(1_001), MAX_CHARS))
				.isInstanceOf(UnprocessableRequestException.class);
		}

		@Test
		void controlCharactersAreRemovedAndLineBreaksKept() {
			Text text = MessageContent.text("  xin\u0000 chào\r\nbạn  ", MAX_CHARS);

			assertThat(text.body()).isEqualTo("xin chào\nbạn");
		}

		@Test
		void aTextMessageHasABodyAndNoPayload() {
			Text text = MessageContent.text("hi", MAX_CHARS);

			assertThat(text.type()).isEqualTo(MessageType.TEXT);
			assertThat(text.payload()).isNull();
		}

	}

	@Nested
	@DisplayName("conversations.last_message_preview")
	class Previews {

		@Test
		void aLongTextIsCutAtOneHundredCharacters() {
			assertThat(MessageContent.text("a".repeat(100), MAX_CHARS).preview()).hasSize(100);
			assertThat(MessageContent.text("a".repeat(101), MAX_CHARS).preview()).isEqualTo("a".repeat(100));
		}

		@Test
		void aCutNeverSplitsASurrogatePair() {
			String emoji = "😀";

			String preview = MessageContent.text(emoji.repeat(150), MAX_CHARS).preview();

			assertThat(preview.codePointCount(0, preview.length())).isEqualTo(100);
			assertThat(preview).isEqualTo(emoji.repeat(100));
		}

		@Test
		void lineBreaksBecomeSpaces() {
			assertThat(MessageContent.text("một\nhai", MAX_CHARS).preview()).isEqualTo("một hai");
		}

		@Test
		void imagesAndPointsHaveFixedLabels() {
			assertThat(MessageContent.images(List.of(image())).preview()).isEqualTo("[Hình ảnh]");
			assertThat(MessageContent.meetingPoint(10.77, 106.70, "Cafe").preview()).isEqualTo("[Vị trí]");
		}

		@Test
		void aSystemNoteShowsItsText() {
			assertThat(MessageContent.systemNote("OFFER_CREATED", "Đã gửi đề nghị 100.000đ", Map.of()).preview())
				.isEqualTo("Đã gửi đề nghị 100.000đ");
		}

	}

	@Nested
	@DisplayName("FR-CH-04: 1 to 5 images")
	class ImageRules {

		@ParameterizedTest(name = "{0} image(s) are accepted")
		@ValueSource(ints = { 1, 5 })
		void oneToFiveImagesAreAccepted(int count) {
			Images images = MessageContent.images(java.util.Collections.nCopies(count, image()));

			assertThat(images.images()).hasSize(count);
			assertThat(images.type()).isEqualTo(MessageType.IMAGE);
			assertThat(images.body()).isNull();
		}

		@ParameterizedTest(name = "{0} image(s) are refused")
		@ValueSource(ints = { 0, 6 })
		void noneOrSixAreInvalid(int count) {
			List<ChatImage> images = java.util.Collections.nCopies(count, image());

			assertThatThrownBy(() -> MessageContent.images(images)).isInstanceOf(InvalidRequestException.class)
				.extracting(error -> ((BusinessException) error).getCode())
				.isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		@DisplayName("the payload is the dictionary's list of {key, thumbKey, w, h}")
		void payloadFollowsTheDictionary() {
			Images images = MessageContent
				.images(List.of(new ChatImage("chat-images/a.webp", "chat-images/a-t.webp", 1080, 720)));

			assertThat(images.payload()).isEqualTo(List
				.of(Map.of("key", "chat-images/a.webp", "thumbKey", "chat-images/a-t.webp", "w", 1080, "h", 720)));
		}

		@Test
		void anImageNeedsKeysAndDimensions() {
			assertThatThrownBy(() -> new ChatImage(" ", "t", 1, 1)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ChatImage("k", "t", 0, 1)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	@DisplayName("FR-CH-05: a meeting point inside Vietnam")
	class MeetingPointRules {

		@ParameterizedTest(name = "{0}, {1} is inside")
		@CsvSource({ "10.7769, 106.7009", "8.0, 102.0", "23.5, 117.5" })
		void pointsInsideAndOnTheEdgeAreAccepted(double latitude, double longitude) {
			MeetingPoint point = MessageContent.meetingPoint(latitude, longitude, "Công viên");

			assertThat(point.type()).isEqualTo(MessageType.LOCATION);
			assertThat(point.payload()).isEqualTo(Map.of("lat", latitude, "lng", longitude, "placeName", "Công viên"));
		}

		@ParameterizedTest(name = "{0}, {1} is outside")
		@CsvSource({ "7.99, 106.0", "23.51, 106.0", "15.0, 101.99", "15.0, 117.51", "48.85, 2.35", "NaN, 106.0" })
		@DisplayName("outside Vietnam: 400 VALIDATION_FAILED")
		void pointsOutsideAreInvalid(double latitude, double longitude) {
			assertThatThrownBy(() -> MessageContent.meetingPoint(latitude, longitude, "Nơi nào đó"))
				.isInstanceOf(InvalidRequestException.class);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "  " })
		void thePlaceNameIsRequired(String placeName) {
			assertThatThrownBy(() -> MessageContent.meetingPoint(10.77, 106.70, placeName))
				.isInstanceOf(InvalidRequestException.class);
		}

		@Test
		void thePlaceNameHasAtMostOneHundredCharacters() {
			assertThat(MessageContent.meetingPoint(10.77, 106.70, "a".repeat(100)).placeName()).hasSize(100);
			assertThatThrownBy(() -> MessageContent.meetingPoint(10.77, 106.70, "a".repeat(101)))
				.isInstanceOf(InvalidRequestException.class);
		}

	}

	@Nested
	@DisplayName("FR-CH-08: system notes")
	class SystemNoteRules {

		@Test
		void aSystemNoteHasATextAndAKindInThePayload() {
			MessageContent.SystemNote note = MessageContent.systemNote("OFFER_CREATED", "Đã gửi đề nghị",
					Map.of("transactionId", "t-1"));

			assertThat(note.type()).isEqualTo(MessageType.SYSTEM);
			assertThat(note.body()).isEqualTo("Đã gửi đề nghị");
			assertThat(note.payload())
				.isEqualTo(Map.of("kind", "OFFER_CREATED", "facts", Map.of("transactionId", "t-1")));
		}

		@Test
		void aBlankKindOrTextIsABug() {
			assertThatThrownBy(() -> MessageContent.systemNote(" ", "text", Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> MessageContent.systemNote("KIND", " ", Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> MessageContent.systemNote("K".repeat(41), "text", Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	private static ChatImage image() {
		return new ChatImage("chat-images/a.webp", "chat-images/a-t.webp", 1080, 720);
	}

}
