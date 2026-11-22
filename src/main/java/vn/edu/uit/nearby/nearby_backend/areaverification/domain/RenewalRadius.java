package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * How close to an area's center a fix must be to renew it, {@code area.renew_radius_m}
 * (FR-AR-04). The same rule makes a new verification near an existing area renew that
 * area instead (UC-AR-01 A4).
 */
public record RenewalRadius(double meters) {

	public RenewalRadius {
		if (!(meters > 0)) {
			throw new IllegalArgumentException("Renewal radius must be positive: " + meters);
		}
	}

	public RenewalCheck check(GeoPoint areaCenter, GeoPoint fixLocation) {
		double distanceMeters = areaCenter.distanceMetersTo(fixLocation);
		return new RenewalCheck(distanceMeters, distanceMeters <= meters);
	}

	public record RenewalCheck(double distanceMeters, boolean isWithinRadius) {

		private static final long ROUNDING_STEP_METERS = 100;

		/**
		 * The distance shown to the user when renewal is refused, rounded to 100 m
		 * (FR-AR-04).
		 */
		public long roundedDistanceMeters() {
			return Math.round(distanceMeters / ROUNDING_STEP_METERS) * ROUNDING_STEP_METERS;
		}

	}

}
