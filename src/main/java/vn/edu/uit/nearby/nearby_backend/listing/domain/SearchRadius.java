package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * Search radius clamped to 1 km - {@code search.max_radius_km} (FR-DC-06); a clamped
 * value is flagged so the app can tell the user.
 */
public record SearchRadius(int meters, boolean adjusted) {

	public static final int MIN_KM = 1;

	private static final int METERS_PER_KM = 1000;

	public static SearchRadius clamp(double requestedKm, int maxKm) {
		double clampedKm = Math.clamp(requestedKm, MIN_KM, maxKm);
		return new SearchRadius((int) Math.round(clampedKm * METERS_PER_KM), clampedKm != requestedKm);
	}

}
