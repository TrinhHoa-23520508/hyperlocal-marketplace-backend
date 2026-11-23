package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess.SendLock;
import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * Applies the send lock of FR-CH-11 to a conversation: the listing was removed, one
 * member blocked the other, or a member is suspended, banned or deleted. The answer never
 * says which, so a block stays hidden.
 */
@Component
class SendGuard {

	static final String CLOSED_MESSAGE = "This conversation is closed.";

	private final ListingLookup listings;

	private final UserBlocks blocks;

	private final AccountStanding standing;

	SendGuard(ListingLookup listings, UserBlocks blocks, AccountStanding standing) {
		this.listings = listings;
		this.blocks = blocks;
		this.standing = standing;
	}

	Optional<SendLock> lockOf(Conversation conversation) {
		String listingStatus = listings.find(conversation.listingId()).map(ListingSnapshot::status).orElse("DELETED");
		boolean blocked = blocks.isBlockedEitherWay(conversation.buyerId(), conversation.sellerId());
		boolean bothActive = standing.isActiveWithVerifiedPhone(conversation.buyerId())
				&& standing.isActiveWithVerifiedPhone(conversation.sellerId());
		return ConversationAccess.checkSend(listingStatus, blocked, bothActive);
	}

	/**
	 * @throws OperationNotAllowedException {@code CONVERSATION_CLOSED}
	 */
	void requireOpen(Conversation conversation) {
		if (lockOf(conversation).isPresent()) {
			throw new OperationNotAllowedException(ErrorCodes.CONVERSATION_CLOSED, CLOSED_MESSAGE);
		}
	}

}
