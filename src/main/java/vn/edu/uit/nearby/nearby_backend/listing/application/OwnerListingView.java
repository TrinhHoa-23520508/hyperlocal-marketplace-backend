package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * A listing as its owner sees it after a change (A12, A14), including the exact pin that
 * only the owner may see (FR-LS-03).
 *
 * @param nextBumpAllowedAt null while the listing has never been bumped
 */
public record OwnerListingView(UUID id, ListingStatus status, @Nullable HiddenReason hiddenReason, short categoryId,
		UUID areaId, String title, @Nullable String description, ItemCondition condition, long priceVnd, boolean free,
		boolean acceptOffers, @Nullable String meetingSpot, GeoPoint pin, GeoPoint publicPoint,
		@Nullable String localityName, List<ListingImageView> images, Counters counters, int bumpCount,
		@Nullable Instant bumpedAt, @Nullable Instant nextBumpAllowedAt, @Nullable Instant publishedAt,
		@Nullable Instant soldAt, Instant createdAt, Instant updatedAt, long version) {

	/**
	 * FR-LS-11 counters as stored on the listing.
	 */
	public record Counters(int views, int favorites, int chats) {
	}

}
