package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * The fields a seller fills in on A12, shared by create (FR-LS-01) and edit (FR-LS-06).
 *
 * @param acceptOffers false: offers must match the asking price (FR-TX-01)
 */
public record ListingContent(ListingTitle title, ListingDescription description, ItemCondition condition, Price price,
		boolean acceptOffers, MeetingSpot meetingSpot) {

}
