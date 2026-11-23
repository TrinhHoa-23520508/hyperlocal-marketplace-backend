package vn.edu.uit.nearby.nearby_backend.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VietnamBoundsTests {

	@ParameterizedTest(name = "{0}, {1} -> {2}")
	@DisplayName("FR-CH-05: the box includes its edges and rejects one step outside")
	@CsvSource({ "10.7769, 106.7009, true", "21.0285, 105.8542, true", "8.0, 102.0, true", "23.5, 117.5, true",
			"7.9999, 106.0, false", "23.5001, 106.0, false", "15.0, 101.9999, false", "15.0, 117.5001, false",
			"48.8566, 2.3522, false", "0.0, 0.0, false" })
	void decidesByTheBoundingBox(double latitude, double longitude, boolean inside) {
		assertThat(VietnamBounds.contains(latitude, longitude)).isEqualTo(inside);
		assertThat(VietnamBounds.contains(new GeoPoint(latitude, longitude))).isEqualTo(inside);
	}

}
