package vn.edu.uit.nearby.nearby_backend.shared.geo;

/**
 * "Inside Vietnam" for a meeting point (FR-CH-05). The SRS gives no polygon, so this is a
 * bounding box around the mainland and the islands (plan DEC-CH-10): enough to reject a
 * point that is obviously elsewhere, and exact enough to test at its edges.
 */
public final class VietnamBounds {

	public static final double MIN_LATITUDE = 8.0;

	public static final double MAX_LATITUDE = 23.5;

	public static final double MIN_LONGITUDE = 102.0;

	public static final double MAX_LONGITUDE = 117.5;

	private VietnamBounds() {
	}

	/**
	 * @return true when the point is inside the box, edges included
	 */
	public static boolean contains(double latitude, double longitude) {
		return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE && longitude >= MIN_LONGITUDE
				&& longitude <= MAX_LONGITUDE;
	}

	public static boolean contains(GeoPoint point) {
		return contains(point.latitude(), point.longitude());
	}

}
