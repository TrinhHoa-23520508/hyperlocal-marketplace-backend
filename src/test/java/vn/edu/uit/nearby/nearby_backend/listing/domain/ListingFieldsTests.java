package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * FR-LS-01 field rules (group LST, SRS table {@code tab:validation}).
 */
class ListingFieldsTests {

	@Nested
	class Title {

		@ParameterizedTest
		@ValueSource(ints = { 5, 100 })
		@DisplayName("FR-LS-01: titles of 5 and 100 characters are accepted")
		void acceptsTheBoundaryLengths(int length) {
			assertThat(new ListingTitle("Đ".repeat(length)).value()).hasSize(length);
		}

		@ParameterizedTest
		@ValueSource(ints = { 4, 101 })
		@DisplayName("FR-LS-01: titles of 4 and 101 characters are a 400 VALIDATION_FAILED on title")
		void refusesLengthsOutsideFiveToOneHundred(int length) {
			assertFieldError(() -> new ListingTitle("Đ".repeat(length)), "title");
		}

		@Test
		void stripsSurroundingSpacesAndControlCharacters() {
			assertThat(new ListingTitle("  Áo\tkhoác\u0000 da \n").value()).isEqualTo("Áokhoác da");
		}

		@Test
		void countsAnEmojiAsOneCharacter() {
			assertThat(new ListingTitle("Bánh😀").value()).isEqualTo("Bánh😀");
		}

		@Test
		void refusesATitleThatIsTooShortOnceCleaned() {
			assertFieldError(() -> new ListingTitle("\u0001\u0002 Ghế \u0003"), "title");
		}

		@Test
		void refusesAMissingTitle() {
			assertFieldError(() -> new ListingTitle(null), "title");
		}

	}

	@Nested
	class Description {

		@Test
		@DisplayName("FR-LS-01: a description of 2 000 characters is accepted, 2 001 is refused")
		void acceptsAtMostTwoThousandCharacters() {
			assertThat(new ListingDescription("a".repeat(2000)).value()).hasSize(2000);
			assertFieldError(() -> new ListingDescription("a".repeat(2001)), "description");
		}

		@Test
		void keepsLineBreaks() {
			assertThat(new ListingDescription("Dòng 1\r\nDòng 2\u0007").value()).isEqualTo("Dòng 1\nDòng 2");
		}

		@Test
		void treatsABlankDescriptionAsNone() {
			assertThat(new ListingDescription("  \n ").text()).isEmpty();
			assertThat(new ListingDescription(null).text()).isEmpty();
		}

	}

	@Nested
	class Spot {

		@Test
		@DisplayName("FR-LS-01: a meeting spot of 100 characters is accepted, 101 is refused")
		void acceptsAtMostOneHundredCharacters() {
			assertThat(new MeetingSpot("x".repeat(100)).text()).hasValue("x".repeat(100));
			assertFieldError(() -> new MeetingSpot("x".repeat(101)), "meetingSpot");
		}

		@Test
		void treatsABlankSpotAsNone() {
			assertThat(new MeetingSpot(" ").value()).isNull();
		}

	}

	@Nested
	class Amount {

		@ParameterizedTest
		@ValueSource(longs = { 0, 1, 1_000_000_000 })
		@DisplayName("FR-LS-01: prices 0-1 000 000 000 VND are accepted")
		void acceptsPricesInRange(long amount) {
			assertThat(new Price(amount).amountVnd()).isEqualTo(amount);
		}

		@ParameterizedTest
		@ValueSource(longs = { -1, 1_000_000_001 })
		@DisplayName("FR-LS-01: prices -1 and 1 000 000 001 are a 400 VALIDATION_FAILED on price")
		void refusesPricesOutOfRange(long amount) {
			assertFieldError(() -> new Price(amount), "price");
		}

		@Test
		@DisplayName("BR-09: price 0 means the item is free")
		void zeroIsFree() {
			assertThat(new Price(0).isFree()).isTrue();
			assertThat(new Price(1).isFree()).isFalse();
		}

		@Test
		void comparesPrices() {
			assertThat(new Price(90_000).isLowerThan(new Price(100_000))).isTrue();
			assertThat(new Price(100_000).isLowerThan(new Price(100_000))).isFalse();
		}

	}

	@Nested
	class Images {

		@Test
		@DisplayName("FR-LS-01: the first image is the cover, positions follow the order")
		void keepsTheChosenOrder() {
			UUID first = UUID.randomUUID();
			UUID second = UUID.randomUUID();

			ImageOrder order = ImageOrder.of(List.of(first, second), 10);

			assertThat(order.cover()).isEqualTo(first);
			assertThat(order.positionOf(first)).isEqualTo(1);
			assertThat(order.positionOf(second)).isEqualTo(2);
			assertThat(order.contains(second)).isTrue();
		}

		@Test
		@DisplayName("FR-LS-01: 1 and 10 images are accepted")
		void acceptsOneToTenImages() {
			assertThat(ImageOrder.of(images(1), 10).imageIds()).hasSize(1);
			assertThat(ImageOrder.of(images(10), 10).imageIds()).hasSize(10);
		}

		@Test
		@DisplayName("FR-LS-01: 0 and 11 images are refused")
		void refusesZeroOrElevenImages() {
			assertFieldError(() -> ImageOrder.of(images(0), 10), "imageIds");
			assertFieldError(() -> ImageOrder.of(images(11), 10), "imageIds");
		}

		@Test
		void refusesTheSameImageTwice() {
			UUID image = UUID.randomUUID();

			assertFieldError(() -> ImageOrder.of(List.of(image, image), 10), "imageIds");
		}

		@Test
		void hasNoPositionForAnotherImage() {
			ImageOrder order = ImageOrder.of(images(2), 10);

			assertThatThrownBy(() -> order.positionOf(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
		}

		private static List<UUID> images(int count) {
			return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID()).toList();
		}

	}

	private static void assertFieldError(Runnable creation, String field) {
		assertThatThrownBy(creation::run).isInstanceOf(InvalidRequestException.class)
			.hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED")
			.extracting(ex -> ((InvalidRequestException) ex).getProperties().get("errors"))
			.asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
			.singleElement()
			.extracting("field")
			.isEqualTo(field);
	}

}
