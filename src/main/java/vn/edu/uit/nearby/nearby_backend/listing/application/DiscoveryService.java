package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.FeedArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.FeedCenter;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Category;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DisplayDistance;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SearchCursor;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SearchRadius;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SortOrder;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.CategoryRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.ListingFilter;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.ListingRow;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.MapCluster;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.MapPoint;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.SearchArea;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.Viewport;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * Discovery (FR-DC-01..07), read only and served from the read path (FR-DC-12): feed,
 * filtered search, map, categories and a seller's public listings. Every point returned
 * is a public (blurred) location (BR-05).
 */
@Service
@Transactional(readOnly = true)
public class DiscoveryService {

	static final int MAX_MAP_POINTS = 200;

	private static final int METERS_PER_KM = 1000;

	private static final Set<ListingStatus> FEED_STATUSES = EnumSet.of(ListingStatus.ACTIVE, ListingStatus.RESERVED);

	private final DiscoveryQueries queries;

	private final ListingRepository listings;

	private final CategoryRepository categories;

	private final FeedArea feedArea;

	private final UserBlocks userBlocks;

	private final ListingImageUrls imageUrls;

	private final ListingParameters parameters;

	private final Clock clock;

	DiscoveryService(DiscoveryQueries queries, ListingRepository listings, CategoryRepository categories,
			FeedArea feedArea, UserBlocks userBlocks, ListingImageUrls imageUrls, ListingParameters parameters,
			Clock clock) {
		this.queries = queries;
		this.listings = listings;
		this.categories = categories;
		this.feedArea = feedArea;
		this.userBlocks = userBlocks;
		this.imageUrls = imageUrls;
		this.parameters = parameters;
		this.clock = clock;
	}

	/**
	 * FR-DC-01: ACTIVE and RESERVED listings within the range, newest or most recently
	 * bumped first, {@code search.page_size} per page, without blocked sellers.
	 */
	public ResultPage feed(SearchOrigin origin, @Nullable String cursorToken) {
		ListingFilter filter = new ListingFilter(FEED_STATUSES, null, null, null, null, Set.of(), false,
				excludedSellersFor(origin.viewerId()));
		return page(resolve(origin), filter, SortOrder.NEWEST, cursorToken, false);
	}

	/**
	 * FR-DC-02, FR-DC-03, FR-DC-06 (UC-DC-02).
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for contradicting filters
	 * or an unknown category (E1)
	 */
	public ResultPage search(SearchQuery query, @Nullable String cursorToken) {
		requireConsistent(query);
		SearchArea origin = resolve(query.origin());
		SearchRadius radius = query.radiusKm() == null ? new SearchRadius(origin.radiusMeters(), false)
				: SearchRadius.clamp(query.radiusKm(), parameters.maxSearchRadiusKm());
		Set<ListingStatus> statuses = EnumSet.copyOf(FEED_STATUSES);
		if (query.includeSold()) {
			statuses.add(ListingStatus.SOLD);
		}
		ListingFilter filter = new ListingFilter(statuses, keywordOf(query.keyword()), query.categoryId(),
				query.minPriceVnd(), query.maxPriceVnd(), query.conditions(), query.freeOnly(),
				excludedSellersFor(query.origin().viewerId()));
		return page(new SearchArea(origin.center(), radius.meters()), filter, query.sort(), cursorToken,
				radius.adjusted());
	}

	/**
	 * FR-DC-07: at most 200 markers per response; a crowded viewport gets clusters only.
	 */
	public MapResult map(MapQuery query) {
		ListingFilter filter = new ListingFilter(FEED_STATUSES, null, query.categoryId(), query.minPriceVnd(),
				query.maxPriceVnd(), Set.of(), query.freeOnly(), excludedSellersFor(query.viewerId()));
		if (queries.countInViewport(filter, query.viewport()) <= MAX_MAP_POINTS) {
			return new MapResult(queries.pointsInViewport(filter, query.viewport(), MAX_MAP_POINTS), List.of());
		}
		return new MapResult(List.of(),
				queries.clustersInViewport(filter, query.viewport(), query.zoom(), MAX_MAP_POINTS));
	}

	/**
	 * A08 and A12: active categories in display order.
	 */
	public List<Category> activeCategories() {
		return categories.findByActiveTrueOrderBySortOrder();
	}

	/**
	 * FR-ID-13 (DEC-LS-6): the seller's ACTIVE and RESERVED listings for A11, newest
	 * first. Nothing is shown between users who blocked each other.
	 */
	public CursorPage<ListingCard> sellerListings(UUID sellerId, @Nullable UUID viewerId, @Nullable String cursorToken,
			@Nullable Integer requestedLimit) {
		int limit = CursorPage.clampLimit(requestedLimit);
		if (viewerId != null && userBlocks.isBlockedEitherWay(viewerId, sellerId)) {
			return new CursorPage<>(List.of(), null, false);
		}
		Limit fetchLimit = Limit.of(limit + 1);
		List<Listing> fetched = Cursor.decode(cursorToken)
			.map(cursor -> listings.findOwnPageAfter(sellerId, FEED_STATUSES, cursor.createdAt(), cursor.id(),
					fetchLimit))
			.orElseGet(() -> listings.findOwnFirstPage(sellerId, FEED_STATUSES, fetchLimit));
		CursorPage<Listing> page = CursorPage.of(fetched, limit,
				listing -> new Cursor(listing.getCreatedAt(), listing.getId()));
		Map<UUID, String> covers = imageUrls.coverThumbnails(page.items().stream().map(Listing::getId).toList());
		return page.map(listing -> new ListingCard(listing.getId(), listing.getTitle(), listing.getPrice(),
				listing.askingPrice().isFree(), listing.getStatus(), covers.get(listing.getId()), null,
				listing.getLocalityName(), listing.getCreatedAt(),
				listing.getSellerTier() == null ? SellerTier.NEW : listing.getSellerTier()));
	}

	private ResultPage page(SearchArea area, ListingFilter filter, SortOrder sort, @Nullable String cursorToken,
			boolean radiusAdjusted) {
		SearchCursor after = SearchCursor.decode(cursorToken, sort).orElse(null);
		Instant rankedAt = after == null ? clock.instant() : after.rankedAt();
		int pageSize = parameters.pageSize();
		List<ListingRow> fetched = queries.search(area, filter, sort, rankedAt, after, pageSize + 1,
				parameters.relevanceScore());
		boolean hasMore = fetched.size() > pageSize;
		List<ListingRow> rows = hasMore ? fetched.subList(0, pageSize) : fetched;
		String nextCursor = hasMore ? cursorOf(rows.getLast(), sort, rankedAt).encode() : null;
		boolean suggestWiderRadius = rows.isEmpty() && after == null
				&& area.radiusMeters() < parameters.maxSearchRadiusKm() * METERS_PER_KM;
		return new ResultPage(toCards(rows), nextCursor, hasMore, area.radiusMeters(), radiusAdjusted,
				suggestWiderRadius);
	}

	private List<ListingCard> toCards(List<ListingRow> rows) {
		Map<UUID, String> covers = imageUrls.coverThumbnails(rows.stream().map(ListingRow::id).toList());
		return rows.stream()
			.map(row -> new ListingCard(row.id(), row.title(), row.priceVnd(), row.priceVnd() == 0, row.status(),
					covers.get(row.id()), DisplayDistance.of(row.distanceMeters()).meters(), row.localityName(),
					row.feedAt(), row.sellerTier()))
			.toList();
	}

	private static SearchCursor cursorOf(ListingRow last, SortOrder sort, Instant rankedAt) {
		return sort == SortOrder.NEWEST ? SearchCursor.newest(last.feedAt(), last.id())
				: new SearchCursor(sort, rankedAt, last.rank(), last.feedAt(), last.id());
	}

	/**
	 * FR-DC-04: a point the user chose (GPS or map) wins; otherwise the active area.
	 */
	private SearchArea resolve(SearchOrigin origin) {
		if (origin.point() != null) {
			return new SearchArea(origin.point(), feedArea.radiusMetersOf(origin.rangeLevel()));
		}
		UUID viewerId = origin.viewerId();
		FeedCenter active = viewerId == null ? null : feedArea.activeFeedArea(viewerId).orElse(null);
		if (active == null) {
			throw InvalidRequestException.invalidField("center",
					"Choose a point on the map or use your current location.");
		}
		return new SearchArea(active.center(), active.radiusMeters());
	}

	private Set<UUID> excludedSellersFor(@Nullable UUID viewerId) {
		return viewerId == null ? Set.of() : userBlocks.blockedEitherWay(viewerId);
	}

	private void requireConsistent(SearchQuery query) {
		if (query.minPriceVnd() != null && query.maxPriceVnd() != null && query.minPriceVnd() > query.maxPriceVnd()) {
			throw InvalidRequestException.invalidField("minPrice", "The minimum price is above the maximum price.");
		}
		if (query.categoryId() != null) {
			categories.getExisting(query.categoryId());
		}
	}

	private static @Nullable String keywordOf(@Nullable String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.strip();
	}

	/**
	 * Where to look from (FR-DC-04).
	 *
	 * @param point a GPS position or a point picked on the map; null means the viewer's
	 * active area
	 * @param rangeLevel NEAR, MEDIUM or FAR for a chosen point; null means MEDIUM
	 */
	public record SearchOrigin(@Nullable UUID viewerId, @Nullable GeoPoint point, @Nullable String rangeLevel) {
	}

	/**
	 * UC-DC-02 inputs from A07/A08.
	 *
	 * @param radiusKm null keeps the origin's range; otherwise clamped to 1-20 km
	 */
	public record SearchQuery(SearchOrigin origin, @Nullable String keyword, @Nullable Short categoryId,
			@Nullable Long minPriceVnd, @Nullable Long maxPriceVnd, Set<ItemCondition> conditions, boolean freeOnly,
			@Nullable Double radiusKm, boolean includeSold, SortOrder sort) {

		public SearchQuery {
			conditions = conditions.isEmpty() ? Set.of() : EnumSet.copyOf(conditions);
		}

	}

	public record MapQuery(@Nullable UUID viewerId, Viewport viewport, int zoom, @Nullable Short categoryId,
			@Nullable Long minPriceVnd, @Nullable Long maxPriceVnd, boolean freeOnly) {
	}

	/**
	 * FR-DC-05 result card. Never a pin.
	 *
	 * @param distanceMeters rounded to 100 m from the search center to the public point;
	 * null where there is no center
	 * @param coverThumbnailUrl null when the cover is missing: the app shows a
	 * placeholder
	 * @param postedAt when the listing was posted or last bumped
	 */
	public record ListingCard(UUID id, String title, long priceVnd, boolean free, ListingStatus status,
			@Nullable String coverThumbnailUrl, @Nullable Long distanceMeters, @Nullable String localityName,
			Instant postedAt, SellerTier sellerTier) {
	}

	/**
	 * @param radiusMeters the radius actually searched
	 * @param radiusAdjusted the requested radius was outside 1-20 km and was clamped
	 * (FR-DC-06)
	 * @param suggestWiderRadius nothing found on the first page and a wider radius is
	 * possible (FR-DC-06, UC-DC-02 A1)
	 */
	public record ResultPage(List<ListingCard> items, @Nullable String nextCursor, boolean hasMore, int radiusMeters,
			boolean radiusAdjusted, boolean suggestWiderRadius) {
	}

	/**
	 * Either single markers or clusters, never both (FR-DC-07).
	 */
	public record MapResult(List<MapPoint> points, List<MapCluster> clusters) {
	}

}
