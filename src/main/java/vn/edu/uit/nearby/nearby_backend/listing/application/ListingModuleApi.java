package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingModeration;
import vn.edu.uit.nearby.nearby_backend.listing.ListingReservations;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.listing.SellerScores;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerReputation;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;

/**
 * The listing facades that Transaction, Chat, Reputation and Moderation call (DEC-LS-2).
 * Every method joins the caller's transaction; state changes go through the
 * {@link Listing} transitions and publish {@code ListingStatusChanged}.
 */
@Service
@Transactional
public class ListingModuleApi
		implements ListingLookup, ListingReservations, ListingCounters, SellerScores, ListingModeration {

	private final ListingRepository listings;

	private final ListingEvents events;

	private final Clock clock;

	ListingModuleApi(ListingRepository listings, ListingEvents events, Clock clock) {
		this.listings = listings;
		this.events = events;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ListingSnapshot> find(UUID listingId) {
		return listings.findById(listingId)
			.map(listing -> new ListingSnapshot(listing.getId(), listing.getSellerId(), listing.getAreaId(),
					listing.getTitle(), listing.getStatus().name(), listing.getPrice(), listing.isAcceptOffers(),
					listing.publicPoint(), listing.getReservedTransactionId()));
	}

	@Override
	public void reserve(UUID listingId, UUID transactionId) {
		Listing listing = getExisting(listingId);
		if (listing.getStatus() == ListingStatus.RESERVED && transactionId.equals(listing.getReservedTransactionId())) {
			return;
		}
		change(listing, (reserved, now) -> reserved.reserve(transactionId, now));
	}

	@Override
	public void release(UUID listingId, UUID transactionId) {
		change(getExisting(listingId), (released, now) -> released.releaseReservation(transactionId, now));
	}

	@Override
	public void completeReservedSale(UUID listingId, UUID transactionId) {
		change(getExisting(listingId), (sold, now) -> sold.completeReservedSale(transactionId, now));
	}

	@Override
	public void markSoldToChatPartner(UUID listingId) {
		change(getExisting(listingId), Listing::markSold);
	}

	@Override
	public void revertDeniedSale(UUID listingId) {
		change(getExisting(listingId), Listing::revertDeniedSale);
	}

	@Override
	public void incrementChatCount(UUID listingId) {
		listings.incrementChatCount(listingId);
	}

	@Override
	public void update(UUID sellerId, BigDecimal score, String tier) {
		SellerReputation reputation = SellerReputation.of(score, tier);
		listings.updateSellerReputation(sellerId, reputation.score(), reputation.tier().name());
	}

	@Override
	public void remove(UUID listingId, UUID moderatorId, String reason) {
		change(getExisting(listingId), (removed, now) -> removed.removeByModerator(reason, moderatorId, now));
	}

	private void change(Listing listing, BiConsumer<Listing, Instant> transition) {
		ListingStatus before = listing.getStatus();
		Instant now = clock.instant();
		transition.accept(listing, now);
		events.statusChanged(listing, before, now);
	}

	private Listing getExisting(UUID listingId) {
		return listings.findById(listingId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.LISTING_NOT_FOUND, "Listing not found."));
	}

}
