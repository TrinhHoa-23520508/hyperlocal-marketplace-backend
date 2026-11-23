package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.UUID;

/**
 * The rows of SRS table {@code tab:sm-listing} that transactions drive (registers LS-W3,
 * LS-W4). The transaction module calls these inside its own database transaction, so a
 * failure rolls both back (DEC-LS-2; BR-04 "one ACCEPTED per listing" stays atomic). Each
 * call publishes {@code ListingStatusChanged}.
 *
 * <p>
 * Every method throws {@code ResourceNotFoundException} {@code LISTING_NOT_FOUND} for an
 * unknown listing and {@code ConflictException} {@code INVALID_LISTING_STATE} for a move
 * the table does not allow.
 */
public interface ListingReservations {

	/**
	 * ACTIVE to RESERVED when a transaction is ACCEPTED. Calling it again for the same
	 * transaction changes nothing; another transaction is refused (BR-04).
	 */
	void reserve(UUID listingId, UUID transactionId);

	/**
	 * RESERVED to ACTIVE when the holding transaction is CANCELLED or EXPIRED.
	 */
	void release(UUID listingId, UUID transactionId);

	/**
	 * RESERVED to SOLD when the holding transaction is COMPLETED, or when the seller
	 * marks it sold to that buyer (FR-LS-09).
	 */
	void completeReservedSale(UUID listingId, UUID transactionId);

	/**
	 * FR-LS-09 (a): ACTIVE to SOLD when the seller marks it sold to a chat partner
	 * ({@code SaleMarked}).
	 */
	void markSoldToChatPartner(UUID listingId);

	/**
	 * The buyer denied a marked sale within the deny window ({@code SaleDenied}): SOLD
	 * back to ACTIVE.
	 */
	void revertDeniedSale(UUID listingId);

}
