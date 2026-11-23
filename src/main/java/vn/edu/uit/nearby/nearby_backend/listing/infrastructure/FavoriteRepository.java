package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import vn.edu.uit.nearby.nearby_backend.listing.domain.Favorite;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Favorite.FavoriteId;

/**
 * FR-LS-12. Inserts and deletes report whether a row changed, so the listing counter
 * moves by exactly one per distinct user.
 */
public interface FavoriteRepository extends Repository<Favorite, FavoriteId> {

	@Modifying
	@Query(value = """
			INSERT INTO favorites (user_id, listing_id, created_at) VALUES (:userId, :listingId, :createdAt)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(UUID userId, UUID listingId, Instant createdAt);

	@Modifying
	@Query(value = "DELETE FROM favorites WHERE user_id = :userId AND listing_id = :listingId", nativeQuery = true)
	int deleteIfPresent(UUID userId, UUID listingId);

	boolean existsById(FavoriteId id);

	@Query("""
			select f from Favorite f where f.id.userId = :userId
			order by f.createdAt desc, f.id.listingId desc
			""")
	List<Favorite> findFirstPage(UUID userId, Limit limit);

	@Query("""
			select f from Favorite f where f.id.userId = :userId
			  and (f.createdAt < :createdAt or (f.createdAt = :createdAt and f.id.listingId < :listingId))
			order by f.createdAt desc, f.id.listingId desc
			""")
	List<Favorite> findPageAfter(UUID userId, Instant createdAt, UUID listingId, Limit limit);

	/**
	 * UC-SY-12: the user's saved listings go with the account; each listing loses one
	 * from its counter.
	 */
	@Modifying
	@Query(value = """
			WITH removed AS (DELETE FROM favorites WHERE user_id = :userId RETURNING listing_id)
			UPDATE listings SET favorite_count = greatest(favorite_count - 1, 0)
			WHERE id IN (SELECT listing_id FROM removed)
			""", nativeQuery = true)
	int deleteAllOfUser(UUID userId);

}
