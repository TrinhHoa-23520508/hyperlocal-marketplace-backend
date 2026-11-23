package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingContent;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingDescription;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingTitle;
import vn.edu.uit.nearby.nearby_backend.listing.domain.MeetingSpot;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Price;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * What the seller fills in on A12, for a new listing (UC-LS-01) or an edit (UC-LS-02).
 *
 * @param imageIds uploaded images in display order; the first is the cover
 */
public record ListingForm(@Nullable String title, @Nullable String description, ItemCondition condition, long priceVnd,
		boolean acceptOffers, @Nullable String meetingSpot, short categoryId, GeoPoint pin, List<UUID> imageIds) {

	public ListingForm {
		imageIds = List.copyOf(imageIds);
	}

	/**
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException
	 * {@code VALIDATION_FAILED} on the first field that breaks the LST rules
	 */
	ListingContent content() {
		return new ListingContent(new ListingTitle(title), new ListingDescription(description), condition,
				new Price(priceVnd), acceptOffers, new MeetingSpot(meetingSpot));
	}

}
