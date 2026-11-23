package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess.OpenRefusal;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.Opened;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;

/**
 * UC-CH-01: the buyer taps "Chat" on a listing (A10). Opening a second time returns the
 * same conversation (FR-CH-01); a conversation that already exists is always returned,
 * even after the listing is sold or the buyer left the area, because reading it is always
 * allowed (FR-CH-11).
 */
@Service
public class ConversationService {

	private static final String LISTING_NOT_FOUND = "Listing not found.";

	private static final String LISTING_UNAVAILABLE = "This listing is no longer available.";

	private final ConversationRepository conversations;

	private final ListingLookup listings;

	private final ListingCounters listingCounters;

	private final TradingGate tradingGate;

	private final InteractionRange interactionRange;

	private final UserBlocks blocks;

	private final Clock clock;

	ConversationService(ConversationRepository conversations, ListingLookup listings, ListingCounters listingCounters,
			TradingGate tradingGate, InteractionRange interactionRange, UserBlocks blocks, Clock clock) {
		this.conversations = conversations;
		this.listings = listings;
		this.listingCounters = listingCounters;
		this.tradingGate = tradingGate;
		this.interactionRange = interactionRange;
		this.blocks = blocks;
		this.clock = clock;
	}

	/**
	 * @return the conversation of (listing, buyer) and whether this call created it
	 * @throws OperationNotAllowedException {@code TRADING_GATE_FAILED} with reasons,
	 * {@code OUT_OF_RANGE}, {@code CONTACT_NOT_ALLOWED}
	 * @throws ConflictException {@code INVALID_LISTING_STATE} for your own listing or one
	 * that takes no new chats
	 * @throws ResourceNotFoundException {@code LISTING_NOT_FOUND} for an unknown, deleted
	 * or removed listing without an earlier conversation
	 */
	@Transactional
	public Opened open(UUID buyerId, UUID listingId) {
		var existing = conversations.findByListingAndBuyer(listingId, buyerId);
		if (existing.isPresent()) {
			return new Opened(existing.get(), false);
		}
		ListingSnapshot listing = listings.find(listingId)
			.filter(snapshot -> !isGone(snapshot))
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.LISTING_NOT_FOUND, LISTING_NOT_FOUND));
		tradingGate.requireOpen(buyerId);
		interactionRange.requireWithinRange(buyerId, listing.publicPoint());
		requireAllowed(buyerId, listing);
		Opened opened = conversations.insertIfAbsent(listingId, buyerId, listing.sellerId(), clock.instant());
		if (opened.created()) {
			listingCounters.incrementChatCount(listingId);
		}
		return opened;
	}

	/**
	 * UC-TX-01 step 7 (register row CH-W5): an offer is the first contact, so the
	 * conversation of (listing, buyer) is created if it does not exist. Transaction
	 * already checked the gate, the range and the blocks for the offer, so nothing is
	 * checked again here; the chat counter moves only when a row was really created.
	 */
	@Transactional
	public Conversation openForTrade(UUID listingId, UUID buyerId, UUID sellerId) {
		Opened opened = conversations.insertIfAbsent(listingId, buyerId, sellerId, clock.instant());
		if (opened.created()) {
			listingCounters.incrementChatCount(listingId);
		}
		return opened.conversation();
	}

	/**
	 * Whether the buyer chatted with the seller about the listing: the "is a chat
	 * partner" fact for marking a listing sold to someone (register row CH-W4, FR-LS-09
	 * (a)). Transaction defines its own SPI and implements it with this.
	 */
	@Transactional(readOnly = true)
	public boolean isChatPartner(UUID listingId, UUID buyerId) {
		return conversations.existsByListingAndBuyer(listingId, buyerId);
	}

	private void requireAllowed(UUID buyerId, ListingSnapshot listing) {
		boolean takesChats = listing.takesNewChats() && interactionRange.allowsNewContacts(listing.areaId());
		boolean blocked = blocks.isBlockedEitherWay(buyerId, listing.sellerId());
		ConversationAccess.checkOpen(buyerId, listing.sellerId(), takesChats, blocked).ifPresent(refusal -> {
			if (refusal == OpenRefusal.CONTACT_BLOCKED) {
				throw new OperationNotAllowedException(ErrorCodes.CONTACT_NOT_ALLOWED, "You cannot contact this user.");
			}
			throw new ConflictException(ErrorCodes.INVALID_LISTING_STATE, LISTING_UNAVAILABLE);
		});
	}

	private static boolean isGone(ListingSnapshot listing) {
		return "DELETED".equals(listing.status()) || "REMOVED".equals(listing.status());
	}

}
