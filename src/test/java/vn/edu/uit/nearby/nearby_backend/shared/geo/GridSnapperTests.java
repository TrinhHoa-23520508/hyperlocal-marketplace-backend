package vn.edu.uit.nearby.nearby_backend.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GridSnapperTests {

	private static final int CELL_SIZE_METERS = 300;

	private static final double HALF_DIAGONAL_METERS = CELL_SIZE_METERS * Math.sqrt(2) / 2;

	private final GridSnapper grid = new GridSnapper(CELL_SIZE_METERS);

	@Test
	void snapsTheSamePointToTheSameCellEveryTime() {
		GeoPoint uit = new GeoPoint(10.8700, 106.8031);

		assertThat(grid.snap(uit)).isEqualTo(grid.snap(uit));
	}

	@ParameterizedTest
	@CsvSource({ "10.8700,106.8031", "10.7769,106.7009", "21.0285,105.8542", "-33.8688,151.2093", "0,0" })
	void keepsTheCenterWithinHalfACellDiagonalOfThePoint(double latitude, double longitude) {
		GeoPoint point = new GeoPoint(latitude, longitude);

		GridCell cell = grid.snap(point);

		assertThat(cell.center().distanceMetersTo(point)).isLessThanOrEqualTo(HALF_DIAGONAL_METERS);
	}

	@Test
	void movesThePointToTheCellCenter() {
		GeoPoint point = new GeoPoint(10.8700, 106.8031);

		assertThat(grid.snap(point).center()).isNotEqualTo(point);
	}

	@Test
	void snapsACellCenterToItself() {
		GridCell cell = grid.snap(new GeoPoint(10.8700, 106.8031));

		assertThat(grid.snap(cell.center())).isEqualTo(cell);
	}

	@Test
	void putsNearbyPointsOfOneCellTogether() {
		GridCell cell = grid.snap(new GeoPoint(10.8700, 106.8031));
		GeoPoint slightlyMoved = new GeoPoint(cell.center().latitude() + 0.0001, cell.center().longitude() + 0.0001);

		assertThat(grid.snap(slightlyMoved)).isEqualTo(cell);
	}

	@Test
	void separatesPointsInNeighbouringCells() {
		GeoPoint point = new GeoPoint(10.8700, 106.8031);
		GeoPoint oneCellEast = new GeoPoint(10.8700, 106.8031 + 0.003);

		assertThat(grid.snap(oneCellEast).code()).isNotEqualTo(grid.snap(point).code());
	}

	@Test
	void namesTheCellBySizeColumnAndRow() {
		assertThat(grid.snap(new GeoPoint(0.0001, 0.0001)).code()).isEqualTo("300:0:0");
		assertThat(grid.snap(new GeoPoint(-0.0001, -0.0001)).code()).isEqualTo("300:-1:-1");
	}

	@ParameterizedTest
	@CsvSource({ "90,180", "-90,-180", "85.06,179.9999" })
	void handlesTheEdgesOfTheMapWithoutLeavingValidCoordinates(double latitude, double longitude) {
		GridCell cell = grid.snap(new GeoPoint(latitude, longitude));

		assertThat(cell.code()).hasSizeLessThanOrEqualTo(20);
		assertThat(cell.center().longitude()).isBetween(-180.0, 180.0);
	}

	@Test
	void keepsCodesWithinTwentyCharactersAtTheSmallestCellSize() {
		GridCell cell = new GridSnapper(10).snap(new GeoPoint(-85, -179.9999));

		assertThat(cell.code()).hasSizeLessThanOrEqualTo(20);
	}

	@ParameterizedTest
	@CsvSource({ "9", "0", "-300" })
	void rejectsCellsSmallerThanTenMeters(int cellSizeMeters) {
		assertThatThrownBy(() -> new GridSnapper(cellSizeMeters)).isInstanceOf(IllegalArgumentException.class);
	}

}
