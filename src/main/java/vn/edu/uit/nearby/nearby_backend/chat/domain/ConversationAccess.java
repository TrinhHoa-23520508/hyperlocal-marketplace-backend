package vn.edu.uit.nearby.nearby_backend.chat.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may open a conversation and who may still write in one (FR-CH-01, FR-CH-11). Pure
 * decisions over facts the application layer has fetched.
 */
public final class ConversationAccess {

	/**
	 * Why a new conversation cannot be opened, after the trading gate and the range rule
	 * have passed. The listed order is the order of the checks.
	 */
	public enum OpenRefusal {

		/**
		 * Blocked either way (FR-ID-14): answered {@code CONTACT_NOT_ALLOWED} without
		 * saying who blocked whom.
		 */
		CONTACT_BLOCKED,

		/**
		 * The buyer owns the listing (FR-CH-01: "không phải tin của chính mình").
		 */
		OWN_LISTING,

		/**
		 * The listing is not ACTIVE or RESERVED.
		 */
		LISTING_NOT_AVAILABLE

	}

	/**
	 * Why sending is locked. Every reason is answered with the same neutral
	 * {@code CONVERSATION_CLOSED} (FR-CH-11); the reason is only for logs and tests.
	 */
	public enum SendLock {

		/**
		 * The listing was removed by a moderator. SOLD and DELETED listings stay open
		 * (FR-CH-11 acceptance, plan reading R1).
		 */
		LISTING_REMOVED,

		BLOCKED,

		/**
		 * A member is suspended, banned or deleted (plan reading R2).
		 */
		ACCOUNT_RESTRICTED

	}

	private static final String REMOVED = "REMOVED";

	private ConversationAccess() {
	}

	public static Optional<OpenRefusal> checkOpen(UUID buyerId, UUID listingSellerId, boolean listingTakesNewChats,
			boolean blockedEitherWay) {
		if (blockedEitherWay) {
			return Optional.of(OpenRefusal.CONTACT_BLOCKED);
		}
		if (buyerId.equals(listingSellerId)) {
			return Optional.of(OpenRefusal.OWN_LISTING);
		}
		return listingTakesNewChats ? Optional.empty() : Optional.of(OpenRefusal.LISTING_NOT_AVAILABLE);
	}

	/**
	 * @param listingStatus the {@code listings.status} value, such as {@code SOLD}
	 * @param bothMembersActive false when either member is suspended, banned or deleted
	 */
	public static Optional<SendLock> checkSend(String listingStatus, boolean blockedEitherWay,
			boolean bothMembersActive) {
		if (REMOVED.equals(listingStatus)) {
			return Optional.of(SendLock.LISTING_REMOVED);
		}
		if (blockedEitherWay) {
			return Optional.of(SendLock.BLOCKED);
		}
		return bothMembersActive ? Optional.empty() : Optional.of(SendLock.ACCOUNT_RESTRICTED);
	}

}
