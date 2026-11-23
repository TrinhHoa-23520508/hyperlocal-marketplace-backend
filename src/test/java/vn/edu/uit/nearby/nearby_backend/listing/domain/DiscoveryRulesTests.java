package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.BLUR_GRID;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.PIN;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

class DiscoveryRulesTests {

	@Nested
	class Blur {

		@Test
		@DisplayName("FR-LS-03: the same pin always gets the same public point")
		void isDeterministic() {
			assertThat(ListingLocation.pinnedAt(PIN, BLUR_GRID)).isEqualTo(ListingLocation.pinnedAt(PIN, BLUR_GRID));
		}

		@ParameterizedTest
		@CsvSource({ "10.8700, 106.8031", "10.7769, 106.7009", "21.0285, 105.8542", "10.87135, 106.80412" })
		@DisplayName("appendix D: the public point differs from the pin by at most 212 m")
		void movesThePinByAtMostHalfACellDiagonal(double latitude, double longitude) {
			GeoPoint pin = new GeoPoint(latitude, longitude);

			ListingLocation location = ListingLocation.pinnedAt(pin, BLUR_GRID);

			assertThat(location.publicPoint()).isNotEqualTo(pin);
			assertThat(location.publicPoint().distanceMetersTo(pin)).isLessThanOrEqualTo(300 * Math.sqrt(2) / 2);
		}

		@Test
		void pinsInTheSameCellShareThePublicPoint() {
			GeoPoint center = BLUR_GRID.snap(PIN).center();
			GeoPoint nearby = new GeoPoint(center.latitude() + 0.0001, center.longitude() + 0.0001);

			assertThat(ListingLocation.pinnedAt(nearby, BLUR_GRID).publicPoint()).isEqualTo(center);
		}

	}

	@Nested
	class Distance {

		@ParameterizedTest(name = "{0} m -> {1} m")
		@CsvSource({ "0, 0", "49, 0", "50, 100", "949, 900", "950, 1000", "999, 1000", "1049, 1000", "1050, 1100",
				"12345, 12300" })
		@DisplayName("FR-DC-05: 100 m steps below 1 km, 0.1 km steps from 1 km")
		void roundsToOneHundredMeters(double meters, long expected) {
			assertThat(DisplayDistance.of(meters).meters()).isEqualTo(expected);
		}

		@Test
		void refusesANegativeDistance() {
			assertThatThrownBy(() -> DisplayDistance.of(-1)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Radius {

		@Test
		@DisplayName("FR-DC-06: 25 km is searched as 20 km and flagged")
		void clampsAboveTheMaximum() {
			assertThat(SearchRadius.clamp(25, 20)).isEqualTo(new SearchRadius(20_000, true));
		}

		@Test
		@DisplayName("FR-DC-06: 0.5 km is searched as 1 km and flagged")
		void clampsBelowTheMinimum() {
			assertThat(SearchRadius.clamp(0.5, 20)).isEqualTo(new SearchRadius(1_000, true));
		}

		@ParameterizedTest
		@CsvSource({ "1, 1000", "4, 4000", "20, 20000", "2.5, 2500" })
		void keepsARadiusInRange(double km, int meters) {
			assertThat(SearchRadius.clamp(km, 20)).isEqualTo(new SearchRadius(meters, false));
		}

	}

	@Nested
	class Relevance {

		private final RelevanceScore relevance = new RelevanceScore(RankWeights.parse("0.5,0.3,0.2"));

		@Test
		@DisplayName("appendix D: score = 0.5 (1 - d/D) + 0.3 R/100 + 0.2 exp(-age / 7 days)")
		void followsTheSrsFormula() {
			double score = relevance.score(1_000, 4_000, new BigDecimal("80.00"), Duration.ofDays(7));

			assertThat(score).isCloseTo(0.5 * 0.75 + 0.3 * 0.8 + 0.2 * Math.exp(-1), within(1e-9));
		}

		@Test
		@DisplayName("FR-DC-03: a seller without reviews counts as 50")
		void usesFiftyForASellerWithoutReviews() {
			assertThat(relevance.score(0, 4_000, null, Duration.ZERO)).isCloseTo(0.5 + 0.15 + 0.2, within(1e-9));
		}

		@Test
		void caresNothingAboutListingsBeyondTheRadiusOrFromTheFuture() {
			assertThat(relevance.score(5_000, 4_000, null, Duration.ofDays(-1))).isCloseTo(0.15 + 0.2, within(1e-9));
		}

		@Test
		@DisplayName("FR-DC-03: changing search.rank_weights changes the order")
		void weightsDecideTheOrder() {
			RelevanceScore distanceFirst = new RelevanceScore(RankWeights.parse("1,0,0"));
			RelevanceScore freshnessFirst = new RelevanceScore(RankWeights.parse("0, 0, 1"));
			double nearButOld = 500;
			double farButNew = 3_500;

			assertThat(distanceFirst.score(nearButOld, 4_000, null, Duration.ofDays(30)))
				.isGreaterThan(distanceFirst.score(farButNew, 4_000, null, Duration.ZERO));
			assertThat(freshnessFirst.score(nearButOld, 4_000, null, Duration.ofDays(30)))
				.isLessThan(freshnessFirst.score(farButNew, 4_000, null, Duration.ZERO));
		}

		@ParameterizedTest
		@ValueSource(strings = { "0.5,0.3", "0.5,0.3,0.2,0.1", "a,b,c", "-0.5,0.3,0.2" })
		void refusesMalformedWeights(String weights) {
			assertThatThrownBy(() -> RankWeights.parse(weights)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Cursor {

		private static final Instant FEED_AT = Instant.parse("2026-11-22T03:00:00.123Z");

		@Test
		@DisplayName("FR-DC-01: the feed cursor round-trips (feed_at, id)")
		void roundTripsTheNewestCursor() {
			SearchCursor cursor = SearchCursor.newest(FEED_AT, UUID.randomUUID());

			assertThat(SearchCursor.decode(cursor.encode(), SortOrder.NEWEST)).hasValue(cursor);
		}

		@Test
		void roundTripsARankedCursor() {
			SearchCursor cursor = new SearchCursor(SortOrder.RELEVANT, FEED_AT.plusSeconds(60), 0.734_512_345_678_9,
					FEED_AT, UUID.randomUUID());

			assertThat(SearchCursor.decode(cursor.encode(), SortOrder.RELEVANT)).hasValue(cursor);
		}

		@Test
		void hasNoCursorOnTheFirstPage() {
			assertThat(SearchCursor.decode(null, SortOrder.NEWEST)).isEmpty();
			assertThat(SearchCursor.decode(" ", SortOrder.NEAREST)).isEmpty();
		}

		@ParameterizedTest
		@ValueSource(strings = { "garbage", "%%%", "djF8TkVXRVNUfDE=" })
		@DisplayName("FR-DC-01: an undecodable cursor is a 400 INVALID_CURSOR")
		void refusesAnUndecodableCursor(String token) {
			assertInvalidCursor(token, SortOrder.NEWEST);
		}

		@Test
		void refusesACursorOfAnotherSortOrder() {
			String token = SearchCursor.newest(FEED_AT, UUID.randomUUID()).encode();

			assertInvalidCursor(token, SortOrder.NEAREST);
		}

		@Test
		void refusesACursorOfAnotherVersion() {
			String raw = "v0|NEWEST|1|0.0|1|" + UUID.randomUUID();
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());

			assertInvalidCursor(token, SortOrder.NEWEST);
		}

		private static void assertInvalidCursor(String token, SortOrder sort) {
			assertThatThrownBy(() -> SearchCursor.decode(token, sort)).isInstanceOf(InvalidRequestException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_CURSOR");
		}

	}

}
