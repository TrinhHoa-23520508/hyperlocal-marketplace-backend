package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.UUID;

/**
 * FR-LS-11 counter the chat module keeps up to date (register LS-W6).
 */
public interface ListingCounters {

	/**
	 * A new conversation about the listing was opened (UC-CH-01). Unknown listings are
	 * ignored.
	 */
	void incrementChatCount(UUID listingId);

}
