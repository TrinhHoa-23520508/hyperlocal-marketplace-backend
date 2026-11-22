package vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AreaVerificationAttempt;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AttemptResult;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.VerificationKind;

public interface AreaVerificationAttemptRepository extends JpaRepository<AreaVerificationAttempt, UUID> {

	/**
	 * The user's latest accepted attempt that still holds its precise fix, the reference
	 * for the implied-speed rule (FR-AR-05).
	 */
	default Optional<AreaVerificationAttempt> findLatestAcceptedWithFix(UUID userId) {
		return findFirstByUserIdAndResultAndReportedLocationIsNotNullOrderByCreatedAtDesc(userId,
				AttemptResult.ACCEPTED);
	}

	Optional<AreaVerificationAttempt> findFirstByUserIdAndResultAndReportedLocationIsNotNullOrderByCreatedAtDesc(
			UUID userId, AttemptResult result);

	/**
	 * Accepted additions and replacements since {@code since}, oldest first (FR-AR-06
	 * {@code area.max_changes_per_30d}).
	 */
	default List<AreaVerificationAttempt> findAreaChangesSince(UUID userId, Instant since) {
		return findByUserIdAndResultAndKindInAndCreatedAtAfterOrderByCreatedAt(userId, AttemptResult.ACCEPTED,
				List.of(VerificationKind.INITIAL, VerificationKind.REPLACEMENT), since);
	}

	List<AreaVerificationAttempt> findByUserIdAndResultAndKindInAndCreatedAtAfterOrderByCreatedAt(UUID userId,
			AttemptResult result, Collection<VerificationKind> kinds, Instant since);

	List<AreaVerificationAttempt> findByUserIdOrderByCreatedAt(UUID userId);

	/**
	 * Clears precise locations older than the retention cutoff, keeping the result and
	 * grid cell (FR-AR-10, UC-SY-11). Safe to run again: purged rows no longer match.
	 */
	@Modifying
	@Query(value = """
			UPDATE area_verifications SET reported_location = NULL, location_purged_at = :now
			WHERE reported_location IS NOT NULL AND created_at < :cutoff""", nativeQuery = true)
	int purgeLocationsCreatedBefore(Instant cutoff, Instant now);

	@Modifying
	@Query(value = """
			UPDATE area_verifications SET reported_location = NULL, location_purged_at = :now
			WHERE reported_location IS NOT NULL AND user_id = :userId""", nativeQuery = true)
	int purgeLocationsOfUser(UUID userId, Instant now);

	/**
	 * Deletes attempts older than {@code area.attempt_retention_months}; their device
	 * signals go with them (ON DELETE CASCADE).
	 */
	@Modifying
	@Query(value = "DELETE FROM area_verifications WHERE created_at < :cutoff", nativeQuery = true)
	int deleteCreatedBefore(Instant cutoff);

}
