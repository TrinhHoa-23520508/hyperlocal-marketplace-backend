package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.Optional;
import java.util.UUID;

/**
 * Read access for Chat and Transaction (register LS-W12). DELETED and REMOVED listings
 * are returned too, so a conversation about a deleted listing stays readable (FR-LS-08,
 * FR-CH-11).
 */
public interface ListingLookup {

	Optional<ListingSnapshot> find(UUID listingId);

}
