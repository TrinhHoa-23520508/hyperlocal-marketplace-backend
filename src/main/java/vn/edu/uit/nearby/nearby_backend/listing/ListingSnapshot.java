package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * What other modules may know about a listing (register LS-W12): never the exact pin.
 *
 * @param status the {@code listings.status} value, such as {@code ACTIVE}
 * @param areaId the seller's area the listing belongs to, for {@code InteractionRange}
 * @param publicPoint the blurred location (FR-LS-03)
 * @param reservedTransactionId the ACCEPTED transaction holding a RESERVED listing
 */
public record ListingSnapshot(UUID id, UUID sellerId, UUID areaId, String title, String status, long priceVnd,
		boolean acceptOffers, GeoPoint publicPoint, @Nullable UUID reservedTransactionId) {

	public static final String ACTIVE = "ACTIVE";

	public static final String RESERVED = "RESERVED";

	public boolean isFree() {
		return priceVnd == 0;
	}

	/**
	 * FR-CH-01: a new conversation needs an ACTIVE or RESERVED listing.
	 */
	public boolean takesNewChats() {
		return ACTIVE.equals(status) || RESERVED.equals(status);
	}

	/**
	 * FR-TX-01: a new offer needs an ACTIVE listing.
	 */
	public boolean takesNewOffers() {
		return ACTIVE.equals(status);
	}

}
