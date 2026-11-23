package vn.edu.uit.nearby.nearby_backend.listing;

import java.time.Instant;
import java.util.UUID;

/**
 * A listing moved between two states of SRS table {@code tab:sm-listing}. Consumed by
 * trade (reject open offers on SOLD or REMOVED, register LS-W5) and notification (R2).
 *
 * @param fromStatus the {@code listings.status} value before, such as {@code ACTIVE}
 * @param toStatus the value after, such as {@code SOLD}
 */
public record ListingStatusChanged(UUID listingId, UUID sellerId, String fromStatus, String toStatus,
		Instant changedAt) {

}
