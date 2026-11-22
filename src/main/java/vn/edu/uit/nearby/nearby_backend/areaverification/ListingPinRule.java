package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Where a seller may pin a listing (FR-AR-09, FR-LS-01): inside one of the seller's own
 * valid (VERIFIED or EXPIRING) areas, within {@code listing.pin_radius_m} of its center.
 */
public interface ListingPinRule {

	/**
	 * @return false when the area is not the seller's, is not valid, or the pin is too
	 * far from its center
	 */
	boolean isPinInsideArea(UUID sellerId, UUID areaId, GeoPoint pin);

	/**
	 * @throws UnprocessableRequestException {@code PIN_OUTSIDE_AREA}, the single code
	 * FR-LS-01 uses for an invalid area and for a pin outside it
	 */
	default void requirePinInsideArea(UUID sellerId, UUID areaId, GeoPoint pin) {
		if (!isPinInsideArea(sellerId, areaId, pin)) {
			throw new UnprocessableRequestException(ErrorCodes.PIN_OUTSIDE_AREA,
					"The pin must be inside one of your valid areas.");
		}
	}

}
