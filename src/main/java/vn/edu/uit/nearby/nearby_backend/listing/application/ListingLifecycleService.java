package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.OpenTransactionCheck;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.ChangeListing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * The seller's own state changes on A14: hide and unhide (UC-LS-03), delete (UC-LS-04),
 * mark sold outside the app (UC-LS-05 A1) and bump (UC-LS-06).
 */
@Service
@Transactional
public class ListingLifecycleService {

	private final ListingRepository listings;

	private final ListingImageRepository images;

	private final List<OpenTransactionCheck> openTransactionChecks;

	private final ListingParameters parameters;

	private final ListingEvents events;

	private final OwnerListingViews views;

	private final Clock clock;

	ListingLifecycleService(ListingRepository listings, ListingImageRepository images,
			List<OpenTransactionCheck> openTransactionChecks, ListingParameters parameters, ListingEvents events,
			OwnerListingViews views, Clock clock) {
		this.listings = listings;
		this.images = images;
		this.openTransactionChecks = openTransactionChecks;
		this.parameters = parameters;
		this.events = events;
		this.views = views;
		this.clock = clock;
	}

	/**
	 * FR-LS-07: the listing leaves the feed, search and map but stays in "Tin của tôi".
	 */
	public OwnerListingView hide(ChangeListing command) {
		return changeVersioned(command, Listing::hide);
	}

	public OwnerListingView unhide(ChangeListing command) {
		return changeVersioned(command, Listing::unhide);
	}

	/**
	 * FR-LS-09 (b), UC-LS-05 A1: SOLD without a transaction and without reviews. Open
	 * offers are rejected by the transaction module on {@code ListingStatusChanged}
	 * (register LS-W5).
	 */
	public OwnerListingView markSoldOutsideApp(ChangeListing command) {
		return changeVersioned(command, Listing::markSold);
	}

	/**
	 * FR-LS-10.
	 */
	public OwnerListingView bump(UUID sellerId, UUID listingId) {
		Listing listing = listings.getOwned(listingId, sellerId);
		listing.bump(parameters.bumpRules(), clock.instant());
		listings.flush();
		return views.of(listing);
	}

	/**
	 * FR-LS-08: soft delete, refused while a transaction is OFFERED or ACCEPTED. The pin
	 * is dropped and the images go to the orphan cleanup (SRS table
	 * {@code tab:retention}).
	 */
	public void delete(UUID sellerId, UUID listingId) {
		Listing listing = listings.getOwned(listingId, sellerId);
		requireNoOpenTransaction(listing);
		ListingStatus before = listing.getStatus();
		Instant now = clock.instant();
		listing.delete(now);
		images.detachAllFrom(List.of(listing.getId()));
		events.statusChanged(listing, before, now);
	}

	private OwnerListingView changeVersioned(ChangeListing command, BiConsumer<Listing, Instant> change) {
		Listing listing = listings.getOwned(command.listingId(), command.sellerId());
		listing.requireVersion(command.version());
		ListingStatus before = listing.getStatus();
		Instant now = clock.instant();
		change.accept(listing, now);
		events.statusChanged(listing, before, now);
		listings.flush();
		return views.of(listing);
	}

	private void requireNoOpenTransaction(Listing listing) {
		openTransactionChecks.stream()
			.flatMap(check -> check.findOpenTransaction(listing.getId()).stream())
			.findFirst()
			.ifPresent(transactionId -> {
				throw new ConflictException(ErrorCodes.LISTING_HAS_OPEN_TRANSACTIONS,
						"The listing has an open transaction; finish or cancel it first.",
						Map.of("transactionId", transactionId.toString()));
			});
	}

}
