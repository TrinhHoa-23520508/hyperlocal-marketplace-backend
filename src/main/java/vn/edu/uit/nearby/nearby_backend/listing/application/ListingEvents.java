package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.listing.ListingPriceDropped;
import vn.edu.uit.nearby.nearby_backend.listing.ListingPublished;
import vn.edu.uit.nearby.nearby_backend.listing.ListingStatusChanged;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Price;

/**
 * Publishes the listing events of SRS appendix B inside the caller's transaction
 * (outbox).
 */
@Component
class ListingEvents {

	private final ApplicationEventPublisher events;

	ListingEvents(ApplicationEventPublisher events) {
		this.events = events;
	}

	void published(Listing listing) {
		events.publishEvent(new ListingPublished(listing.getId(), listing.getSellerId(), listing.getCategoryId(),
				listing.getCreatedAt()));
	}

	/**
	 * FR-LS-06: exactly one event per drop of an ACTIVE listing's price. Drops of a
	 * hidden listing stay quiet, since nobody else can see it.
	 */
	void priceChanged(Listing listing, Price before, Instant now) {
		Price after = listing.askingPrice();
		if (listing.getStatus() == ListingStatus.ACTIVE && after.isLowerThan(before)) {
			events.publishEvent(new ListingPriceDropped(listing.getId(), listing.getSellerId(), before.amountVnd(),
					after.amountVnd(), now));
		}
	}

	void statusChanged(Listing listing, ListingStatus before, Instant now) {
		if (listing.getStatus() != before) {
			events.publishEvent(new ListingStatusChanged(listing.getId(), listing.getSellerId(), before.name(),
					listing.getStatus().name(), now));
		}
	}

}
