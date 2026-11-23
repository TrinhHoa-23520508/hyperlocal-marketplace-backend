package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.util.UUID;

/**
 * Seller commands on listings (UC-LS-01..06).
 */
public final class ListingCommands {

	private ListingCommands() {
	}

	/**
	 * UC-LS-01.
	 *
	 * @param areaId the seller's verified area the listing belongs to; it never changes
	 */
	public record CreateListing(UUID sellerId, UUID areaId, ListingForm form) {
	}

	/**
	 * UC-LS-02.
	 *
	 * @param version the version the seller edited (optimistic lock)
	 */
	public record EditListing(UUID sellerId, UUID listingId, long version, ListingForm form) {
	}

	/**
	 * A versioned state change of the seller's own listing: hide, unhide, sold outside
	 * the app.
	 */
	public record ChangeListing(UUID sellerId, UUID listingId, long version) {
	}

}
