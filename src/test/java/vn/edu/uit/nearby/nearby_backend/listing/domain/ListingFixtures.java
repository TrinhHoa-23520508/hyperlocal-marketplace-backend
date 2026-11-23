package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.ListingChanges;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.NewListing;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;

/**
 * Test data built from the SRS parameter values ({@code tab:params}).
 */
public final class ListingFixtures {

	public static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");

	public static final GeoPoint PIN = new GeoPoint(10.8700, 106.8031);

	public static final GridSnapper BLUR_GRID = new GridSnapper(300);

	public static final BumpRules BUMP_RULES = new BumpRules(Duration.ofHours(48), 10);

	public static final short CATEGORY_ID = 1;

	private ListingFixtures() {
	}

	public static ListingContent content(long priceVnd) {
		return new ListingContent(new ListingTitle("Áo khoác da nam"), new ListingDescription("Còn mới 90%"),
				ItemCondition.LIKE_NEW, new Price(priceVnd), true, new MeetingSpot("Cổng KTX khu A"));
	}

	public static ListingChanges changes(long priceVnd) {
		return new ListingChanges(CATEGORY_ID, content(priceVnd), ListingLocation.pinnedAt(PIN, BLUR_GRID));
	}

	public static Listing anActiveListing(UUID sellerId) {
		return Listing.publish(new NewListing(sellerId, UUID.randomUUID(), CATEGORY_ID, content(150_000),
				ListingLocation.pinnedAt(PIN, BLUR_GRID), "Linh Trung"), NOW);
	}

	public static Listing anActiveListing() {
		return anActiveListing(UUID.randomUUID());
	}

	/**
	 * A listing brought to {@code status} through allowed transitions only.
	 */
	public static Listing aListing(ListingStatus status) {
		Listing listing = anActiveListing();
		switch (status) {
			case ACTIVE -> {
			}
			case HIDDEN -> listing.hide(NOW);
			case RESERVED -> listing.reserve(UUID.randomUUID(), NOW);
			case SOLD -> listing.markSold(NOW);
			case DELETED -> listing.delete(NOW);
			case REMOVED -> listing.removeByModerator("Hàng cấm", UUID.randomUUID(), NOW);
			case PENDING_REVIEW -> throw new IllegalArgumentException("PENDING_REVIEW is reached only in R2");
		}
		return listing;
	}

}
