package vn.edu.uit.nearby.nearby_backend.listing;

import java.time.Instant;
import java.util.UUID;

/**
 * The seller lowered the asking price (FR-LS-06); published once per drop. Consumed by
 * notification to tell favouriters (R2, UC-SY-08).
 */
public record ListingPriceDropped(UUID listingId, UUID sellerId, long oldPriceVnd, long newPriceVnd,
		Instant droppedAt) {

}
