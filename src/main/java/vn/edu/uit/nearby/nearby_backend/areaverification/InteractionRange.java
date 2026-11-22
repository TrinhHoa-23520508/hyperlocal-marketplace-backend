package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * The interaction range rule (FR-AR-09, BR-08) for Chat and Transaction: a buyer may open
 * a chat or make an offer only on a listing whose public location lies within the range
 * level of one of the buyer's valid (VERIFIED or EXPIRING) areas.
 */
public interface InteractionRange {

	boolean isWithinRange(UUID buyerId, GeoPoint listingPublicLocation);

	/**
	 * @throws OperationNotAllowedException {@code OUT_OF_RANGE} when
	 * {@link #isWithinRange} is false
	 */
	default void requireWithinRange(UUID buyerId, GeoPoint listingPublicLocation) {
		if (!isWithinRange(buyerId, listingPublicLocation)) {
			throw new OperationNotAllowedException(ErrorCodes.OUT_OF_RANGE, "This listing is outside your areas.");
		}
	}

	/**
	 * Whether the seller's area a listing belongs to still takes new chats and offers:
	 * false once it is EXPIRED or REMOVED (FR-AR-03). Callers answer with their own code,
	 * such as {@code LISTING_AREA_EXPIRED} (FR-TX-01).
	 */
	boolean allowsNewContacts(UUID listingAreaId);

}
