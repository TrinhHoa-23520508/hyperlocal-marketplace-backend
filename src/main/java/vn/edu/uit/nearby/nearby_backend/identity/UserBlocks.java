package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Blocks between users (FR-ID-14). Chat, Transaction and Listing check them before a
 * contact and answer {@code CONTACT_NOT_ALLOWED} without revealing the block.
 */
public interface UserBlocks {

	boolean isBlockedEitherWay(UUID userId, UUID otherUserId);

	/**
	 * @return every user that blocked {@code userId} or that {@code userId} blocked
	 */
	Set<UUID> blockedEitherWay(UUID userId);

}
