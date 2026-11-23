package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * Distance shown on result cards and the detail screen (FR-DC-05): rounded to 100 m below
 * 1 km and to 0.1 km from 1 km, which is the same 100 m step; the app picks the unit.
 * Measured from the search center to the public location, never the pin (appendix D).
 */
public record DisplayDistance(long meters) {

	private static final double STEP_METERS = 100;

	public static DisplayDistance of(double distanceMeters) {
		if (distanceMeters < 0) {
			throw new IllegalArgumentException("Distance cannot be negative: " + distanceMeters);
		}
		return new DisplayDistance(Math.round(distanceMeters / STEP_METERS) * (long) STEP_METERS);
	}

}
