package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access for Chat and Transaction (register LS-W12). DELETED and REMOVED listings
 * are returned too, so a conversation about a deleted listing stays readable (FR-LS-08,
 * FR-CH-11).
 */
public interface ListingLookup {

	Optional<ListingSnapshot> find(UUID listingId);

	/**
	 * One query for many listings, such as the rows of the conversation list (A17).
	 * @return the snapshots by listing id; unknown ids are absent
	 */
	Map<UUID, ListingSnapshot> findAll(Collection<UUID> listingIds);

}
