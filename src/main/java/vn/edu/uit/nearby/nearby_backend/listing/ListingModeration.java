package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.UUID;

/**
 * The moderator removal of SRS table {@code tab:sm-listing} ("Mọi trạng thái chưa kết
 * thúc → REMOVED", FR-MD-02; register LS-W9). Runs in the caller's transaction so the
 * audit log entry and the removal commit together. Publishes
 * {@code ListingStatusChanged}.
 */
public interface ListingModeration {

	/**
	 * @param reason required, at most 200 characters
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException
	 * {@code LISTING_NOT_FOUND} for an unknown listing
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException
	 * {@code INVALID_LISTING_STATE} for a DELETED or REMOVED listing
	 */
	void remove(UUID listingId, UUID moderatorId, String reason);

}
