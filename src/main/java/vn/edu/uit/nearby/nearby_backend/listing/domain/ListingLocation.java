package vn.edu.uit.nearby.nearby_backend.listing.domain;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;

/**
 * Where a listing is: the seller's exact pin, which only the owner ever sees, and the
 * public point every other reader gets (FR-LS-03, BR-05).
 *
 * @param publicPoint center of the {@code listing.blur_m} grid cell that holds the pin
 * (SRS appendix D "Làm mờ vị trí công khai")
 */
public record ListingLocation(GeoPoint pin, GeoPoint publicPoint) {

	public static ListingLocation pinnedAt(GeoPoint pin, GridSnapper blurGrid) {
		return new ListingLocation(pin, blurGrid.snap(pin).center());
	}

}
