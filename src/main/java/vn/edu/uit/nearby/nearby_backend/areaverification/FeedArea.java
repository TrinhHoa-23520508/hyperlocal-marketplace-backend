package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.Optional;
import java.util.UUID;

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

}
