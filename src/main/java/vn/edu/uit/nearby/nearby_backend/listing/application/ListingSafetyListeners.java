package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.FavoriteRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;

/**
 * Listing rows of SRS table {@code tab:sm-listing} driven by other modules' events, and
 * the listing part of account anonymisation (UC-SY-12). Each handler picks only the
 * listings still in the source state, so a repeated event changes nothing.
 */
@Component
class ListingSafetyListeners {

	private final ListingRepository listings;

	private final ListingImageRepository images;

	private final FavoriteRepository favorites;

	private final ListingEvents events;

	private final Clock clock;

	ListingSafetyListeners(ListingRepository listings, ListingImageRepository images, FavoriteRepository favorites,
			ListingEvents events, Clock clock) {
		this.listings = listings;
		this.images = images;
		this.favorites = favorites;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * FR-AR-07: ACTIVE or HIDDEN listings of the removed area become HIDDEN with
	 * {@code AREA_REMOVED}.
	 */
	@ApplicationModuleListener
	void onAreaRemoved(AreaRemoved event) {
		List<Listing> affected = listings
			.findByAreaIdAndStatusIn(event.areaId(), EnumSet.of(ListingStatus.ACTIVE, ListingStatus.HIDDEN))
			.stream()
			.filter(listing -> listing.isOwnedBy(event.userId()))
			.filter(listing -> listing.getHiddenReason() != HiddenReason.AREA_REMOVED)
			.toList();
		apply(affected, Listing::hideForRemovedArea);
	}

	/**
	 * FR-MD-05: a suspended or banned seller's ACTIVE listings become HIDDEN with
	 * {@code SANCTION}.
	 */
	@ApplicationModuleListener
	void onUserSanctioned(UserSanctioned event) {
		apply(listings.findBySellerIdAndStatus(event.userId(), ListingStatus.ACTIVE), Listing::hideForSanction);
	}

	/**
	 * Sanction lifted or suspension ended: only the listings the sanction hid come back.
	 */
	@ApplicationModuleListener
	void onSanctionLifted(SanctionLifted event) {
		apply(listings.findBySellerIdAndStatusAndHiddenReason(event.userId(), ListingStatus.HIDDEN,
				HiddenReason.SANCTION), Listing::restoreAfterSanction);
	}

	/**
	 * UC-SY-12 (SRS table {@code tab:retention}): the user's favourites are deleted,
	 * their listings become DELETED without their pins, and the images go to the orphan
	 * cleanup.
	 */
	@ApplicationModuleListener
	void onAccountDeleted(AccountDeleted event) {
		favorites.deleteAllOfUser(event.userId());
		List<Listing> owned = listings.findBySellerIdAndStatusNotIn(event.userId(), EnumSet.of(ListingStatus.DELETED));
		apply(owned, Listing::anonymiseForDeletedAccount);
		if (!owned.isEmpty()) {
			images.detachAllFrom(owned.stream().map(Listing::getId).toList());
		}
	}

	private void apply(List<Listing> affected, BiConsumer<Listing, Instant> change) {
		Instant now = clock.instant();
		for (Listing listing : affected) {
			ListingStatus before = listing.getStatus();
			change.accept(listing, now);
			events.statusChanged(listing, before, now);
		}
	}

}
