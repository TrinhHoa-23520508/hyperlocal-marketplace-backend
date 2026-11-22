package vn.edu.uit.nearby.nearby_backend.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Point;

import vn.edu.uit.nearby.nearby_backend.shared.constant.GeoConstants;

class GeoPointTests {

	@Test
	void convertsToJtsPointWithLongitudeAsXAndWgs84Srid() {
		Point point = new GeoPoint(10.8700, 106.8031).toPoint();

		assertThat(point.getX()).isEqualTo(106.8031);
		assertThat(point.getY()).isEqualTo(10.8700);
		assertThat(point.getSRID()).isEqualTo(GeoConstants.WGS84_SRID);
	}

	@Test
	void roundTripsThroughJtsPoint() {
		GeoPoint uit = new GeoPoint(10.8700, 106.8031);

		assertThat(GeoPoint.from(uit.toPoint())).isEqualTo(uit);
	}

	@ParameterizedTest
	@CsvSource({ "90,180", "-90,-180", "0,0" })
	void acceptsCoordinatesOnTheValidBoundary(double latitude, double longitude) {
		assertThat(new GeoPoint(latitude, longitude).latitude()).isEqualTo(latitude);
	}

	@ParameterizedTest
	@CsvSource({ "90.0001,0", "-90.0001,0", "0,180.0001", "0,-180.0001" })
	void rejectsCoordinatesOutsideTheValidRange(double latitude, double longitude) {
		assertThatThrownBy(() -> new GeoPoint(latitude, longitude)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void distanceToItselfIsZero() {
		GeoPoint uit = new GeoPoint(10.8700, 106.8031);

		assertThat(uit.distanceMetersTo(uit)).isZero();
	}

	@Test
	void measuresOneDegreeOfLatitudeOnTheMeanEarthSphere() {
		assertThat(new GeoPoint(0, 0).distanceMetersTo(new GeoPoint(1, 0))).isCloseTo(111_195.08, within(0.01));
	}

	@Test
	void measuresTheDistanceBetweenHoChiMinhCityAndHanoi() {
		GeoPoint hoChiMinhCity = new GeoPoint(10.7769, 106.7009);
		GeoPoint hanoi = new GeoPoint(21.0285, 105.8542);

		assertThat(hoChiMinhCity.distanceMetersTo(hanoi)).isCloseTo(1_143_505.99, within(0.01));
	}

	@Test
	void distanceIsSymmetric() {
		GeoPoint a = new GeoPoint(10.8700, 106.8031);
		GeoPoint b = new GeoPoint(10.7769, 106.7009);

		assertThat(a.distanceMetersTo(b)).isEqualTo(b.distanceMetersTo(a));
	}

	@Test
	void antipodalPointsAreHalfTheCircumferenceApart() {
		assertThat(new GeoPoint(0, 0).distanceMetersTo(new GeoPoint(0, 180))).isCloseTo(Math.PI * 6_371_008.8,
				within(0.01));
	}

}
