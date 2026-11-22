package vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GeoCircle;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevels;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

public interface UserAreaRepository extends JpaRepository<UserArea, UUID> {

	List<UserArea> findByUserIdAndStatusNotOrderBySlot(UUID userId, AreaStatus excludedStatus);

	default List<UserArea> findLiveAreas(UUID userId) {
		return findByUserIdAndStatusNotOrderBySlot(userId, AreaStatus.REMOVED);
	}

	Optional<UserArea> findByIdAndUserId(UUID id, UUID userId);

	List<UserArea> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAt(AreaStatus status, Instant limit, Limit batch);

	/**
	 * VERIFIED areas expiring at or before {@code reminderLimit} (UC-SY-05 reminder).
	 */
	default List<UserArea> findDueForRenewalReminder(Instant reminderLimit, Limit batch) {
		return findByStatusAndExpiresAtLessThanEqualOrderByExpiresAt(AreaStatus.VERIFIED, reminderLimit, batch);
	}

	List<UserArea> findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAt(Collection<AreaStatus> statuses, Instant now,
			Limit batch);

	/**
	 * VERIFIED or EXPIRING areas whose expiry has been reached (UC-SY-05 hourly scan).
	 */
	default List<UserArea> findReachedExpiry(Instant now, Limit batch) {
		return findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAt(
				List.of(AreaStatus.VERIFIED, AreaStatus.EXPIRING), now, batch);
	}

	/**
	 * FR-AR-09: whether the point lies within the range of any VERIFIED or EXPIRING area
	 * of the user, each area using the radius of its own range level. Distances are on
	 * the mean-Earth sphere ({@code use_spheroid = false}), the same model as the renewal
	 * radius, so every 1 500 m rule of the module agrees.
	 */
	@Query(value = """
			SELECT EXISTS (
			  SELECT 1 FROM user_areas a
			  WHERE a.user_id = :userId AND a.status IN ('VERIFIED', 'EXPIRING')
			    AND ST_DWithin(a.center,
			                   ST_SetSRID(ST_MakePoint(:#{#point.longitude()}, :#{#point.latitude()}), 4326)::geography,
			                   CASE a.range_level WHEN 'NEAR' THEN :#{#levels.radiusMetersOf('NEAR')}
			                                      WHEN 'MEDIUM' THEN :#{#levels.radiusMetersOf('MEDIUM')}
			                                      ELSE :#{#levels.radiusMetersOf('FAR')} END,
			                   false))""", nativeQuery = true)
	boolean existsValidAreaInRange(UUID userId, GeoPoint point, RangeLevels levels);

	/**
	 * FR-AR-09 / FR-LS-01: whether the center of the given VERIFIED or EXPIRING area of
	 * the user lies inside the circle.
	 */
	@Query(value = """
			SELECT EXISTS (
			  SELECT 1 FROM user_areas a
			  WHERE a.id = :areaId AND a.user_id = :userId AND a.status IN ('VERIFIED', 'EXPIRING')
			    AND ST_DWithin(a.center,
			                   ST_SetSRID(ST_MakePoint(:#{#circle.center().longitude()},
			                                           :#{#circle.center().latitude()}), 4326)::geography,
			                   :#{#circle.radiusMeters()}, false))""", nativeQuery = true)
	boolean isValidAreaCenterInside(UUID userId, UUID areaId, GeoCircle circle);

	Optional<UserArea> findFirstByUserIdAndActiveTrueAndStatusNot(UUID userId, AreaStatus excludedStatus);

	@Modifying
	@Query("delete from UserArea a where a.userId = :userId")
	int deleteAllOfUser(UUID userId);

	/**
	 * @throws ResourceNotFoundException {@code AREA_NOT_FOUND} when the area does not
	 * exist or belongs to someone else, so other users' areas stay invisible (FR-ID-18)
	 */
	default UserArea getOwned(UUID areaId, UUID userId) {
		return findByIdAndUserId(areaId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.AREA_NOT_FOUND, "Area not found."));
	}

}
