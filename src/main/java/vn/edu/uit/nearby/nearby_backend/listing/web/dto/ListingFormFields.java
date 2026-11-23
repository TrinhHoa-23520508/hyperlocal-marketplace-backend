package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.listing.application.ListingForm;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * The A12 fields that create and edit share, and their conversion to a
 * {@link ListingForm}.
 */
public interface ListingFormFields {

	@Nullable String title();

	@Nullable String description();

	ItemCondition condition();

	Long priceVnd();

	@Nullable Boolean acceptOffers();

	@Nullable String meetingSpot();

	Short categoryId();

	Double latitude();

	Double longitude();

	List<UUID> imageIds();

	default ListingForm toForm() {
		return new ListingForm(title(), description(), condition(), priceVnd(),
				acceptOffers() == null || acceptOffers(), meetingSpot(), categoryId(),
				new GeoPoint(latitude(), longitude()), imageIds());
	}

}
