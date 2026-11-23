package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The default feed and search center (FR-DC-01, FR-DC-04): the user's active area and the
 * radius of its range level (FR-AR-08).
 */
public interface FeedArea {

	/**
	 * @return empty when the user has no active area; the client then uses GPS or a point
	 * picked on the map (FR-DC-04)
	 */
	Optional<FeedCenter> activeFeedArea(UUID userId);

	/**
	 * The feed radius of a range level when the center is a GPS position or a point
	 * picked on the map (FR-DC-04), for guests and users browsing elsewhere.
	 * @param rangeLevel NEAR, MEDIUM or FAR; null means MEDIUM, the default level
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException
	 * {@code INVALID_RANGE} for another value
	 */
	int radiusMetersOf(@Nullable String rangeLevel);

}
