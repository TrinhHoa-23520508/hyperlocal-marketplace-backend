package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Everything within {@code radiusMeters} of {@code center}, such as the 1 500 m around a
 * listing pin that must contain the seller's area center (FR-AR-09).
 */
public record GeoCircle(GeoPoint center, double radiusMeters) {

	public GeoCircle {
		if (!(radiusMeters > 0)) {
			throw new IllegalArgumentException("Radius must be positive: " + radiusMeters);
		}
	}

}
