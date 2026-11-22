package vn.edu.uit.nearby.nearby_backend.shared.geo;

import vn.edu.uit.nearby.nearby_backend.shared.constant.GeoConstants;

/**
 * Snaps a point to the center of the square grid cell that contains it, so a precise
 * location is never stored or shown where a coarse one is enough (SRS appendix D: area
 * centers, FR-AR-01, and public listing locations, FR-LS-03). Squares are laid out on the
 * Web Mercator plane (EPSG:3857); the same point always lands in the same cell.
 *
 * @param cellSizeMeters side of a square in Web Mercator meters, at least 10 so a cell
 * code fits in 20 characters
 */
public record GridSnapper(int cellSizeMeters) {

	private static final int MIN_CELL_SIZE_METERS = 10;

	private static final double MAX_LONGITUDE = 180;

	public GridSnapper {
		if (cellSizeMeters < MIN_CELL_SIZE_METERS) {
			throw new IllegalArgumentException(
					"Grid cell size must be at least " + MIN_CELL_SIZE_METERS + " meters: " + cellSizeMeters);
		}
	}

	public GridCell snap(GeoPoint point) {
		long column = (long) Math.floor(projectX(point.longitude()) / cellSizeMeters);
		long row = (long) Math.floor(projectY(point.latitude()) / cellSizeMeters);
		GeoPoint center = new GeoPoint(unprojectY((row + 0.5) * cellSizeMeters),
				unprojectX((column + 0.5) * cellSizeMeters));
		return new GridCell(cellSizeMeters + ":" + column + ":" + row, center);
	}

	private static double projectX(double longitude) {
		return GeoConstants.WEB_MERCATOR_RADIUS_METERS * Math.toRadians(longitude);
	}

	private static double projectY(double latitude) {
		double clamped = Math.clamp(latitude, -GeoConstants.WEB_MERCATOR_MAX_LATITUDE,
				GeoConstants.WEB_MERCATOR_MAX_LATITUDE);
		return GeoConstants.WEB_MERCATOR_RADIUS_METERS * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(clamped) / 2));
	}

	private static double unprojectX(double x) {
		return Math.clamp(Math.toDegrees(x / GeoConstants.WEB_MERCATOR_RADIUS_METERS), -MAX_LONGITUDE, MAX_LONGITUDE);
	}

	private static double unprojectY(double y) {
		return Math.toDegrees(2 * Math.atan(Math.exp(y / GeoConstants.WEB_MERCATOR_RADIUS_METERS)) - Math.PI / 2);
	}

}
