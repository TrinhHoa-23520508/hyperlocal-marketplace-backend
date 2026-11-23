package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

	Optional<Listing> findByIdAndSellerIdAndStatusNot(UUID id, UUID sellerId, ListingStatus excludedStatus);

	/**
	 * @throws ResourceNotFoundException {@code LISTING_NOT_FOUND} when the listing does
	 * not exist, is DELETED or belongs to someone else (FR-ID-18)
	 */
	default Listing getOwned(UUID listingId, UUID sellerId) {
		return findByIdAndSellerIdAndStatusNot(listingId, sellerId, ListingStatus.DELETED)
			.orElseThrow(ListingRepository::notFound);
	}

	/**
	 * @throws ResourceNotFoundException {@code LISTING_NOT_FOUND} when the listing does
	 * not exist or the viewer may not see it (FR-LS-08, FR-ID-18)
	 */
	default Listing getVisible(UUID listingId, @Nullable UUID viewerId) {
		return findById(listingId).filter(listing -> listing.isVisibleTo(viewerId))
			.orElseThrow(ListingRepository::notFound);
	}

	/**
	 * Serializes the postings of one seller until the transaction ends, so two parallel
	 * requests cannot both pass the posting quota (FR-LS-04).
	 */
	@Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:sellerId AS text), 0))",
			nativeQuery = true)
	int lockPostingOf(UUID sellerId);

	long countBySellerIdAndStatusIn(UUID sellerId, Collection<ListingStatus> statuses);

	/**
	 * Creation times of the seller's listings since {@code since}, oldest first, whatever
	 * their state now: deleting a listing does not give quota back.
	 */
	@Query("select l.createdAt from Listing l where l.sellerId = :sellerId and l.createdAt > :since order by l.createdAt")
	List<Instant> findCreationTimesSince(UUID sellerId, Instant since);

	List<Listing> findByAreaIdAndStatusIn(UUID areaId, Collection<ListingStatus> statuses);

	List<Listing> findBySellerIdAndStatus(UUID sellerId, ListingStatus status);

	List<Listing> findBySellerIdAndStatusAndHiddenReason(UUID sellerId, ListingStatus status, HiddenReason reason);

	List<Listing> findBySellerIdAndStatusNotIn(UUID sellerId, Collection<ListingStatus> statuses);

	@Query("""
			select l.status as status, count(l) as count from Listing l
			where l.sellerId = :sellerId and l.status in :statuses
			group by l.status
			""")
	List<StatusCount> countBySellerGroupedByStatus(UUID sellerId, Collection<ListingStatus> statuses);

	@Query("""
			select l from Listing l
			where l.sellerId = :sellerId and l.status in :statuses
			order by l.createdAt desc, l.id desc
			""")
	List<Listing> findOwnFirstPage(UUID sellerId, Collection<ListingStatus> statuses, Limit limit);

	@Query("""
			select l from Listing l
			where l.sellerId = :sellerId and l.status in :statuses
			  and (l.createdAt < :createdAt or (l.createdAt = :createdAt and l.id < :id))
			order by l.createdAt desc, l.id desc
			""")
	List<Listing> findOwnPageAfter(UUID sellerId, Collection<ListingStatus> statuses, Instant createdAt, UUID id,
			Limit limit);

	/**
	 * FR-LS-12: changes the stored counter by exactly {@code delta}. Counters are not
	 * versioned, so this never conflicts with an edit.
	 */
	@Modifying
	@Query(value = "UPDATE listings SET favorite_count = greatest(favorite_count + :delta, 0) WHERE id = :listingId",
			nativeQuery = true)
	int addToFavoriteCount(UUID listingId, int delta);

	@Modifying
	@Query(value = "UPDATE listings SET view_count = view_count + 1 WHERE id = :listingId", nativeQuery = true)
	int incrementViewCount(UUID listingId);

	@Modifying
	@Query(value = "UPDATE listings SET chat_count = chat_count + 1 WHERE id = :listingId", nativeQuery = true)
	int incrementChatCount(UUID listingId);

	/**
	 * Copies a pushed reputation onto every listing of the seller (FR-DC-03, FR-DC-05).
	 */
	@Modifying
	@Query(value = "UPDATE listings SET seller_score = :score, seller_tier = :tier WHERE seller_id = :sellerId",
			nativeQuery = true)
	int updateSellerReputation(UUID sellerId, BigDecimal score, String tier);

	/**
	 * Any listing of the seller that already carries the pushed reputation; every one
	 * carries the same value.
	 */
	Optional<Listing> findFirstBySellerIdAndSellerScoreIsNotNull(UUID sellerId);

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException(ErrorCodes.LISTING_NOT_FOUND, "Listing not found.");
	}

	interface StatusCount {

		ListingStatus getStatus();

		long getCount();

	}

}
