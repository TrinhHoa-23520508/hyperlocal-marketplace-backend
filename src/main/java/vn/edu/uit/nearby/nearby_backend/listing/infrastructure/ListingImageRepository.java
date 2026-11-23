package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {

	Optional<ListingImage> findByIdAndOwnerId(UUID id, UUID ownerId);

	List<ListingImage> findByListingIdOrderByPosition(UUID listingId);

	/**
	 * Cover images (position 1) of the given listings, for cards.
	 */
	@Query("select i from ListingImage i where i.listingId in :listingIds and i.position = 1")
	List<ListingImage> findCovers(Collection<UUID> listingIds);

	/**
	 * UC-SY-15: images not attached to a listing whose upload started at or before
	 * {@code createdBefore}, oldest first.
	 */
	@Query("select i from ListingImage i where i.listingId is null and i.createdAt <= :createdBefore order by i.createdAt")
	List<ListingImage> findOrphans(Instant createdBefore, Limit batch);

	/**
	 * Deleted listings release their images; the orphan cleanup then deletes them (SRS
	 * table {@code tab:retention}: derivatives go with the listing).
	 */
	@Modifying
	@Query("update ListingImage i set i.listingId = null, i.position = null where i.listingId in :listingIds")
	int detachAllFrom(Collection<UUID> listingIds);

}
