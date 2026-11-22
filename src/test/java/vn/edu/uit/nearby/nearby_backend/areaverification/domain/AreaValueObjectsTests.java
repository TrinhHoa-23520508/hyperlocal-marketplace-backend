package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.NOW;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.RENEWAL_RADIUS;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

class AreaValueObjectsTests {

	@Test
	void rangeLevelsReadTheSrsKilometers() {
		RangeLevels levels = RangeLevels.parse("2,4,6");

		assertThat(levels.radiusMetersOf(RangeLevel.NEAR)).isEqualTo(2000);
		assertThat(levels.radiusMetersOf(RangeLevel.MEDIUM)).isEqualTo(4000);
		assertThat(levels.radiusMetersOf(RangeLevel.FAR)).isEqualTo(6000);
	}

	@Test
	void rangeLevelsTolerateSpacesAroundNumbers() {
		assertThat(RangeLevels.parse(" 1, 3 ,5").radiusMetersOf(RangeLevel.MEDIUM)).isEqualTo(3000);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2,4", "2,4,6,8", "a,b,c", "4,2,6", "2,2,6", "0,4,6", "" })
	void rangeLevelsRejectAMalformedParameter(String value) {
		assertThatThrownBy(() -> RangeLevels.parse(value)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void mediumIsTheDefaultRange() {
		assertThat(RangeLevel.DEFAULT).isEqualTo(RangeLevel.MEDIUM);
	}

	@ParameterizedTest
	@ValueSource(strings = { "NEAR", "MEDIUM", "FAR" })
	void rangeLevelParsesTheAllowedValues(String value) {
		assertThat(RangeLevel.parse(value).name()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(strings = { "near", "HUGE", "" })
	void rangeLevelRejectsOtherValuesWithInvalidRange(String value) {
		assertThatThrownBy(() -> RangeLevel.parse(value)).isInstanceOf(InvalidRequestException.class)
			.hasFieldOrPropertyWithValue("code", "INVALID_RANGE");
	}

	@Test
	void labelIsStripped() {
		assertThat(new AreaLabel("  Nhà  ").value()).isEqualTo("Nhà");
	}

	@Test
	void labelAcceptsThirtyCharacters() {
		assertThat(new AreaLabel("a".repeat(30)).value()).hasSize(30);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
	void labelRejectsBlankOrLongerThanThirtyCharacters(String value) {
		assertThatThrownBy(() -> new AreaLabel(value)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { "'';1", "1;2", "2;1" })
	void slotsGiveTheLowestFreeSlot(String occupied, int expected) {
		assertThat(new AreaSlots(2).firstFree(slots(occupied))).hasValue(expected);
	}

	@Test
	void slotsAreFullWithTwoLiveAreas() {
		assertThat(new AreaSlots(2).firstFree(Set.of(1, 2))).isEmpty();
	}

	@Test
	void slotsNeedAPositiveMaximum() {
		assertThatThrownBy(() -> new AreaSlots(0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void renewalAcceptsAFixAtOneThousandFourHundredNinetyNineMeters() {
		assertThat(RENEWAL_RADIUS.check(UIT, northOf(UIT, 1499)).isWithinRadius()).isTrue();
	}

	@Test
	void renewalRefusesAFixAtOneThousandFiveHundredOneMeters() {
		assertThat(RENEWAL_RADIUS.check(UIT, northOf(UIT, 1501)).isWithinRadius()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({ "1501, 1500", "1549, 1500", "1551, 1600", "4120, 4100", "0, 0" })
	void refusedRenewalReportsTheDistanceRoundedToOneHundredMeters(double meters, long expected) {
		assertThat(RENEWAL_RADIUS.check(UIT, northOf(UIT, meters)).roundedDistanceMeters()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(doubles = { 0, -1, Double.NaN })
	void renewalRadiusMustBePositive(double meters) {
		assertThatThrownBy(() -> new RenewalRadius(meters)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void circleKeepsItsCenterAndRadius() {
		GeoCircle circle = new GeoCircle(UIT, 1500);

		assertThat(circle.center()).isEqualTo(UIT);
		assertThat(circle.radiusMeters()).isEqualTo(1500);
	}

	@ParameterizedTest
	@ValueSource(doubles = { 0, -1, Double.NaN })
	void circleRadiusMustBePositive(double radiusMeters) {
		assertThatThrownBy(() -> new GeoCircle(UIT, radiusMeters)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void impliedSpeedIsDistanceOverTimeInKilometersPerHour() {
		GpsFix earlier = new GpsFix(UIT, NOW);
		GpsFix later = new GpsFix(northOf(UIT, 5000), NOW.plus(Duration.ofMinutes(1)));

		assertThat(later.impliedSpeedKmhSince(earlier)).isCloseTo(300, within(0.001));
	}

	@Test
	void impliedSpeedIgnoresTheOrderOfTheFixes() {
		GpsFix earlier = new GpsFix(UIT, NOW);
		GpsFix later = new GpsFix(northOf(UIT, 1000), NOW.plus(Duration.ofHours(1)));

		assertThat(earlier.impliedSpeedKmhSince(later)).isEqualTo(later.impliedSpeedKmhSince(earlier));
	}

	@Test
	void sameTimeAndPlaceImpliesNoSpeed() {
		GpsFix fix = new GpsFix(UIT, NOW);

		assertThat(fix.impliedSpeedKmhSince(fix)).isZero();
	}

	@Test
	void sameTimeInAnotherPlaceImpliesAnInfiniteSpeed() {
		assertThat(new GpsFix(northOf(UIT, 10), NOW).impliedSpeedKmhSince(new GpsFix(UIT, NOW))).isInfinite();
	}

	@ParameterizedTest
	@ValueSource(doubles = { -0.1, Double.NaN, Double.POSITIVE_INFINITY })
	void readingRejectsAnInvalidAccuracy(double accuracyMeters) {
		GpsFix fix = new GpsFix(UIT, NOW);

		assertThatThrownBy(() -> new GpsReading(fix, accuracyMeters, false))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void readingAcceptsZeroAccuracy() {
		assertThat(new GpsReading(new GpsFix(UIT, NOW), 0, false).accuracyMeters()).isZero();
	}

	private static Set<Integer> slots(String occupied) {
		return occupied.isEmpty() ? Set.of() : Set.copyOf(List.of(Integer.valueOf(occupied)));
	}

}
