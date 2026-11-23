package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.listing.application.OwnerListingView.Counters;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;

@Component
class OwnerListingViews {

	private final ListingImageUrls imageUrls;

	private final ListingParameters parameters;

	OwnerListingViews(ListingImageUrls imageUrls, ListingParameters parameters) {
		this.imageUrls = imageUrls;
		this.parameters = parameters;
	}

	OwnerListingView of(Listing listing) {
		return new OwnerListingView(listing.getId(), listing.getStatus(), listing.getHiddenReason(),
				listing.getCategoryId(), listing.getAreaId(), listing.getTitle(), listing.getDescription(),
				listing.getCondition(), listing.getPrice(), listing.askingPrice().isFree(), listing.isAcceptOffers(),
				listing.getMeetingSpotText(), listing.pin(), listing.publicPoint(), listing.getLocalityName(),
				imageUrls.imagesOf(listing.getId()),
				new Counters(listing.getViewCount(), listing.getFavoriteCount(), listing.getChatCount()),
				listing.getBumpCount(), listing.getBumpedAt(), nextBumpAllowedAt(listing), listing.getPublishedAt(),
				listing.getSoldAt(), listing.getCreatedAt(), listing.getUpdatedAt(), listing.getVersion());
	}

	private @Nullable Instant nextBumpAllowedAt(Listing listing) {
		Instant bumpedAt = listing.getBumpedAt();
		return bumpedAt == null ? null : bumpedAt.plus(parameters.bumpRules().cooldown());
	}

}
