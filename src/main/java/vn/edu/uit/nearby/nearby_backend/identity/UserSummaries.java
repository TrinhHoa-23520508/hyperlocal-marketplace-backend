package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Display names and avatars of many users in one query, for the conversation list (A17)
 * where one call per row would be slow (plan DEC-CH-9).
 */
public interface UserSummaries {

	/**
	 * @return the summaries by user id; unknown ids are absent
	 */
	Map<UUID, UserSummary> summariesOf(Collection<UUID> userIds);

}
