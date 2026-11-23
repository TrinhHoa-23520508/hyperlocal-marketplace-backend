package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.FeedArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.identity.application.block.BlockService;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.ListingCard;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.MapQuery;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.MapResult;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.ResultPage;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.SearchOrigin;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.SearchQuery;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingDetailService.ListingDetail;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingDetailService.Viewer;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.DisabledReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.NewListing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingContent;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingDescription;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingLocation;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingTitle;
import vn.edu.uit.nearby.nearby_backend.listing.domain.MeetingSpot;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Price;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SortOrder;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.Viewport;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.FavoriteRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * FR-DC-01..08, FR-LS-14 and FR-ID-13 (seller listings) against PostGIS. Each test works
 * in its own random region of Vietnam so listings of other tests never show up.
 */
@IntegrationTest
class DiscoveryIntegrationTests {

	private static final GridSnapper BLUR = new GridSnapper(300);

	private static final double METERS_PER_DEGREE_LATITUDE = 111_320;

	@Autowired
	DiscoveryService discovery;

	@Autowired
	ListingDetailService details;

	@Autowired
	ListingLifecycleService lifecycle;

	@Autowired
	ListingRepository listings;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BlockService blocks;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	@Autowired
	FavoriteRepository favorites;

	@Autowired
	ListingImageUrls imageUrls;

	@Autowired
	UserBlocks userBlocks;

	@Autowired
	TradingGate tradingGate;

	@Autowired
	InteractionRange interactionRange;

	@Autowired
	FeedArea feedArea;

	private GeoPoint origin;

	private UUID seller;

	@BeforeEach
	void pickARegion() {
		origin = BLUR
			.snap(new GeoPoint(ThreadLocalRandom.current().nextDouble(11.5, 21.5),
					ThreadLocalRandom.current().nextDouble(104.0, 108.5)))
			.center();
		seller = UUID.randomUUID();
	}

	@Nested
	class Feed {

		@Test
		@DisplayName("FR-DC-01: ACTIVE and RESERVED listings within 4 km (MEDIUM), newest first")
		void listsActiveAndReservedListingsInRange() {
			Listing older = listing("Bàn học gỗ", 100_000, 1_000, Duration.ofHours(3), null);
			Listing reserved = listing("Ghế xoay", 50_000, 2_000, Duration.ofHours(2),
					l -> l.reserve(UUID.randomUUID(), Instant.now()));
			Listing newest = listing("Nồi cơm điện", 200_000, 3_000, Duration.ofHours(1), null);
			listing("Tủ lạnh", 1, 5_000, Duration.ofHours(1), null);
			listing("Đèn bàn", 1, 500, Duration.ofHours(1), l -> l.hide(Instant.now()));
			listing("Quạt máy", 1, 500, Duration.ofHours(1), l -> l.markSold(Instant.now()));
			listing("Xe đạp", 1, 500, Duration.ofHours(1), l -> l.delete(Instant.now()));
			listing("Máy giặt", 1, 500, Duration.ofHours(1),
					l -> l.removeByModerator("Hàng cấm", UUID.randomUUID(), Instant.now()));

			ResultPage page = discovery.feed(new SearchOrigin(null, origin, null), null);

			assertThat(page.items()).extracting(ListingCard::id)
				.containsExactly(newest.getId(), reserved.getId(), older.getId());
			assertThat(page.items().get(1).status()).isEqualTo(ListingStatus.RESERVED);
			assertThat(page.radiusMeters()).isEqualTo(4_000);
		}

		@Test
		@DisplayName("FR-DC-05: cards show the distance to the public point rounded to 100 m, never the pin")
		void cardsShowARoundedDistance() {
			Listing listing = listing("Bàn học gỗ", 0, 1_234, Duration.ofHours(1), null);

			ListingCard card = discovery.feed(new SearchOrigin(null, origin, "NEAR"), null).items().getFirst();

			long expected = Math.round(origin.distanceMetersTo(listing.publicPoint()) / 100) * 100;
			assertThat(card.distanceMeters()).isEqualTo(expected);
			assertThat(card.distanceMeters() % 100).isZero();
			assertThat(card.free()).isTrue();
			assertThat(card.localityName()).isEqualTo("Phường Test");
		}

		@Test
		@DisplayName("FR-DC-01: pages neither repeat nor skip when a listing is inserted mid-pagination")
		void pagesStayStableWhileListingsArrive() {
			List<UUID> expected = new ArrayList<>();
			for (int index = 0; index < 25; index++) {
				expected.add(0, listing("Đồ cũ số " + index, 1, 800, Duration.ofMinutes(100 - index), null).getId());
			}
			SearchOrigin from = new SearchOrigin(null, origin, null);

			ResultPage first = discovery.feed(from, null);
			listing("Tin mới chen vào", 1, 800, Duration.ZERO, null);
			ResultPage second = discovery.feed(from, first.nextCursor());

			assertThat(first.items()).hasSize(20);
			assertThat(first.hasMore()).isTrue();
			assertThat(second.hasMore()).isFalse();
			List<UUID> seen = new ArrayList<>(first.items().stream().map(ListingCard::id).toList());
			seen.addAll(second.items().stream().map(ListingCard::id).toList());
			assertThat(seen).containsExactlyElementsOf(expected);
		}

		@Test
		@DisplayName("FR-LS-10: a bumped listing comes before listings created before the bump")
		void bumpedListingRisesToTheTop() {
			Listing old = listing("Bàn học gỗ", 1, 800, Duration.ofHours(5), null);
			Listing recent = listing("Ghế xoay", 1, 800, Duration.ofHours(1), null);
			transaction.executeWithoutResult(status -> listings.findById(old.getId())
				.orElseThrow()
				.bump(ListingFixtures.BUMP_RULES, Instant.now()));

			ResultPage page = discovery.feed(new SearchOrigin(null, origin, null), null);

			assertThat(page.items()).extracting(ListingCard::id).containsExactly(old.getId(), recent.getId());
		}

		@Test
		@DisplayName("FR-DC-01: listings of a seller the viewer blocked, or who blocked them, are left out")
		void leavesOutBlockedSellers() {
			AreaTestAccounts accounts = new AreaTestAccounts(users, locationConsents, appParameters);
			UUID viewer = accounts.eligibleUser();
			seller = accounts.eligibleUser();
			listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);
			blocks.block(seller, viewer);

			assertThat(discovery.feed(new SearchOrigin(viewer, origin, null), null).items()).isEmpty();
			assertThat(discovery.feed(new SearchOrigin(null, origin, null), null).items()).hasSize(1);
		}

		@Test
		@DisplayName("FR-DC-04: a guest without a point is asked to pick one")
		void guestsMustPickACenter() {
			assertThatThrownBy(() -> discovery.feed(new SearchOrigin(null, null, null), null))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
		}

		@Test
		@DisplayName("FR-DC-01: an undecodable cursor is a 400 INVALID_CURSOR")
		void refusesABadCursor() {
			assertThatThrownBy(() -> discovery.feed(new SearchOrigin(null, origin, null), "garbage"))
				.hasFieldOrPropertyWithValue("code", "INVALID_CURSOR");
		}

	}

	@Nested
	class Search {

		@Test
		@DisplayName("FR-DC-02: \"ao khoac\" finds \"Áo khoác\"")
		void matchesWithoutAccents() {
			Listing jacket = listing("Áo khoác da nam", 300_000, 800, Duration.ofHours(1), null);
			listing("Bàn học gỗ", 300_000, 800, Duration.ofHours(1), null);

			ResultPage page = discovery.search(query("ao khoac", SortOrder.NEWEST), null);

			assertThat(page.items()).extracting(ListingCard::id).containsExactly(jacket.getId());
		}

		@Test
		@DisplayName("FR-DC-02: SOLD appears only with includeSold; HIDDEN never")
		void includesSoldOnlyOnRequest() {
			Listing sold = listing("Áo khoác cũ", 1, 800, Duration.ofHours(1), l -> l.markSold(Instant.now()));
			listing("Áo khoác ẩn", 1, 800, Duration.ofHours(1), l -> l.hide(Instant.now()));

			assertThat(discovery.search(query("ao khoac", SortOrder.NEWEST), null).items()).isEmpty();
			assertThat(discovery.search(withSold(query("ao khoac", SortOrder.NEWEST)), null).items())
				.extracting(ListingCard::id)
				.containsExactly(sold.getId());
		}

		@Test
		@DisplayName("FR-DC-02: category, price range, condition and free-only filters combine")
		void combinesFilters() {
			Listing free = listing("Sách giáo khoa", 0, 800, Duration.ofHours(1), null);
			Listing cheap = listing("Sách tiểu thuyết", 50_000, 800, Duration.ofHours(2), null);
			listing("Sách hiếm", 900_000, 800, Duration.ofHours(3), null);

			SearchQuery priced = new SearchQuery(new SearchOrigin(null, origin, null), "sach", (short) 1, 0L, 100_000L,
					Set.of(ItemCondition.LIKE_NEW), false, null, false, SortOrder.NEWEST);
			SearchQuery freeOnly = new SearchQuery(new SearchOrigin(null, origin, null), null, null, null, null,
					Set.of(), true, null, false, SortOrder.NEWEST);

			assertThat(discovery.search(priced, null).items()).extracting(ListingCard::id)
				.containsExactly(free.getId(), cheap.getId());
			assertThat(discovery.search(freeOnly, null).items()).extracting(ListingCard::id)
				.containsExactly(free.getId());
		}

		@Test
		@DisplayName("FR-DC-02 E1: a minimum price above the maximum is a 400 VALIDATION_FAILED")
		void refusesContradictingPrices() {
			SearchQuery contradicting = new SearchQuery(new SearchOrigin(null, origin, null), null, null, 200L, 100L,
					Set.of(), false, null, false, SortOrder.NEWEST);

			assertThatThrownBy(() -> discovery.search(contradicting, null)).hasFieldOrPropertyWithValue("code",
					"VALIDATION_FAILED");
		}

		@Test
		@DisplayName("FR-DC-02 E1: an unknown category is a 400 VALIDATION_FAILED")
		void refusesAnUnknownCategory() {
			SearchQuery unknown = new SearchQuery(new SearchOrigin(null, origin, null), null, Short.MAX_VALUE, null,
					null, Set.of(), false, null, false, SortOrder.NEWEST);

			assertThatThrownBy(() -> discovery.search(unknown, null)).hasFieldOrPropertyWithValue("code",
					"VALIDATION_FAILED");
		}

		@Test
		@DisplayName("FR-DC-06: 25 km is searched as 20 km and flagged; an empty result suggests a wider radius")
		void clampsTheRadiusAndSuggestsWidening() {
			SearchQuery far = new SearchQuery(new SearchOrigin(null, origin, null), "khong co gi", null, null, null,
					Set.of(), false, 25.0, false, SortOrder.NEWEST);
			SearchQuery near = new SearchQuery(new SearchOrigin(null, origin, null), "khong co gi", null, null, null,
					Set.of(), false, 2.0, false, SortOrder.NEWEST);

			ResultPage clamped = discovery.search(far, null);
			ResultPage empty = discovery.search(near, null);

			assertThat(clamped.radiusMeters()).isEqualTo(20_000);
			assertThat(clamped.radiusAdjusted()).isTrue();
			assertThat(clamped.suggestWiderRadius()).isFalse();
			assertThat(empty.suggestWiderRadius()).isTrue();
		}

		@Test
		@DisplayName("FR-DC-03: with \"nearest\" the distance never decreases, across pages too")
		void sortsByDistance() {
			for (int index = 0; index < 25; index++) {
				listing("Đồ cũ " + index, 1, 150 * (25 - index), Duration.ofMinutes(index), null);
			}
			SearchQuery nearest = query(null, SortOrder.NEAREST);

			ResultPage first = discovery.search(nearest, null);
			ResultPage second = discovery.search(nearest, first.nextCursor());

			List<Long> distances = new ArrayList<>(first.items().stream().map(ListingCard::distanceMeters).toList());
			distances.addAll(second.items().stream().map(ListingCard::distanceMeters).toList());
			assertThat(distances).hasSize(25).isSorted();
		}

		@Test
		@DisplayName("FR-DC-03: changing search.rank_weights changes the \"relevant\" order without a redeploy")
		void relevanceFollowsTheWeights() {
			Listing nearButOld = listing("Ghế gỗ cũ", 1, 300, Duration.ofDays(30), null);
			Listing farButNew = listing("Ghế gỗ mới", 1, 3_500, Duration.ZERO, null);
			String seeded = appParameters.get("search.rank_weights");
			try {
				setRankWeights("1,0,0");
				assertThat(relevantIds()).containsExactly(nearButOld.getId(), farButNew.getId());

				setRankWeights("0,0,1");
				assertThat(relevantIds()).containsExactly(farButNew.getId(), nearButOld.getId());
			}
			finally {
				setRankWeights(seeded);
			}
		}

		private List<UUID> relevantIds() {
			return discovery.search(query("ghe go", SortOrder.RELEVANT), null)
				.items()
				.stream()
				.map(ListingCard::id)
				.toList();
		}

		private void setRankWeights(String weights) {
			jdbc.update("UPDATE app_parameters SET value = ? WHERE key = 'search.rank_weights'", weights);
		}

	}

	@Nested
	class MapView {

		@Test
		@DisplayName("FR-DC-07: up to 200 listings come back as markers on their public points")
		void returnsMarkersForAQuietViewport() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);

			MapResult result = discovery.map(mapAround(origin, 13));

			assertThat(result.clusters()).isEmpty();
			assertThat(result.points()).singleElement().satisfies(point -> {
				assertThat(point.listingId()).isEqualTo(listing.getId());
				assertThat(point.publicPoint()).isEqualTo(listing.publicPoint()).isNotEqualTo(listing.pin());
			});
		}

		@Test
		@DisplayName("FR-DC-07: a crowded viewport gets at most 200 clusters that count every listing")
		void clustersACrowdedViewport() {
			int count = 230;
			transaction.executeWithoutResult(status -> {
				for (int index = 0; index < count; index++) {
					GeoPoint pin = eastOf(northOf(origin, (index / 15) * 400.0), (index % 15) * 400.0);
					listings.save(Listing.publish(newListing("Đồ cũ " + index, 1, pin), Instant.now()));
				}
			});

			MapResult result = discovery.map(mapAround(eastOf(northOf(origin, 3_000), 3_000), 10));

			assertThat(result.points()).isEmpty();
			assertThat(result.clusters()).isNotEmpty().hasSizeLessThanOrEqualTo(200);
			assertThat(result.clusters().stream().mapToLong(cluster -> cluster.listings()).sum()).isEqualTo(count);
		}

	}

	@Nested
	class Detail {

		@Test
		@DisplayName("FR-DC-08: a guest sees the listing without the pin and every action asks to log in")
		void guestSeesNoPin() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);

			ListingDetail detail = details.detail(listing.getId(), new Viewer(null, "guest-1"), origin);

			assertThat(detail.pin()).isNull();
			assertThat(detail.publicPoint()).isEqualTo(listing.publicPoint());
			assertThat(detail.distanceMeters()).isNotNull();
			assertThat(detail.actions().chat().reason()).isEqualTo(DisabledReason.LOGIN_REQUIRED);
		}

		@Test
		@DisplayName("FR-LS-03: only the owner sees the exact pin, with edit actions")
		void ownerSeesThePin() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);

			ListingDetail detail = details.detail(listing.getId(), new Viewer(seller, ""), null);

			assertThat(detail.pin()).isEqualTo(listing.pin());
			assertThat(detail.actions().ownListing()).isTrue();
		}

		@Test
		@DisplayName("FR-DC-08: a user without an area sees Chat disabled with the gate conditions")
		void showsWhyChatIsDisabled() {
			UUID buyer = new AreaTestAccounts(users, locationConsents, appParameters).eligibleUser();
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);

			ListingDetail detail = details.detail(listing.getId(), new Viewer(buyer, ""), origin);

			assertThat(detail.actions().chat().reason()).isEqualTo(DisabledReason.TRADING_GATE_FAILED);
			assertThat(detail.actions().chat().gateFailures()).contains("NO_VALID_AREA");
			assertThat(detail.actions().save().enabled()).isTrue();
		}

		@Test
		@DisplayName("FR-LS-08: a DELETED listing is 404 for everyone")
		void deletedListingIsNotFound() {
			Listing deleted = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), l -> l.delete(Instant.now()));

			assertThatThrownBy(() -> details.detail(deleted.getId(), new Viewer(seller, ""), null))
				.hasFieldOrPropertyWithValue("code", "LISTING_NOT_FOUND");
		}

		@Test
		@DisplayName("FR-ID-18: someone else's HIDDEN listing is 404")
		void hiddenListingIsNotFoundForOthers() {
			Listing hidden = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), l -> l.hide(Instant.now()));

			assertThatThrownBy(() -> details.detail(hidden.getId(), new Viewer(null, "g"), null))
				.hasFieldOrPropertyWithValue("code", "LISTING_NOT_FOUND");
		}

		@Test
		@DisplayName("FR-LS-14: ten views by one viewer in a day count once; the owner's views do not count")
		void countsOneViewPerViewerAndDay() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);
			Viewer guest = new Viewer(null, "fingerprint-" + UUID.randomUUID());

			for (int view = 0; view < 10; view++) {
				details.detail(listing.getId(), guest, null);
				details.detail(listing.getId(), new Viewer(seller, ""), null);
			}
			details.detail(listing.getId(), new Viewer(null, "another-" + UUID.randomUUID()), null);

			assertThat(listings.findById(listing.getId()).orElseThrow().getViewCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("FR-LS-14: a failing view count never blocks the detail")
		void showsTheDetailWhenCountingFails() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);
			ListingDetailService withBrokenCounter = new ListingDetailService(listings, favorites, imageUrls,
					userBlocks, tradingGate, interactionRange, feedArea, new FailingViewCounter());

			ListingDetail detail = withBrokenCounter.detail(listing.getId(), new Viewer(null, "guest-1"), null);

			assertThat(detail.id()).isEqualTo(listing.getId());
			assertThat(listings.findById(listing.getId()).orElseThrow().getViewCount()).isZero();
		}

		@Test
		@DisplayName("FR-LS-15: a listing keeps a category that was deactivated and stays visible")
		void keepsADeactivatedCategory() {
			Listing listing = listing("Bàn học gỗ", 1, 800, Duration.ofHours(1), null);
			jdbc.update("UPDATE categories SET active = false WHERE id = ?", listing.getCategoryId());
			try {
				ListingDetail detail = details.detail(listing.getId(), new Viewer(null, "guest-1"), null);

				assertThat(detail.categoryId()).isEqualTo(listing.getCategoryId());
				assertThat(discovery.feed(new SearchOrigin(null, origin, null), null).items())
					.extracting(ListingCard::id)
					.contains(listing.getId());
			}
			finally {
				jdbc.update("UPDATE categories SET active = true WHERE id = ?", listing.getCategoryId());
			}
		}

	}

	private static final class FailingViewCounter extends ListingViewCounter {

		FailingViewCounter() {
			super(null, null, null);
		}

		@Override
		public boolean countView(UUID listingId, String viewerKey) {
			throw new IllegalStateException("Redis is down");
		}

	}

	@Test
	@DisplayName("FR-ID-13: a seller's public listings are their ACTIVE and RESERVED ones")
	void listsASellersPublicListings() {
		Listing active = listing("Bàn học gỗ", 1, 800, Duration.ofHours(2), null);
		Listing reserved = listing("Ghế xoay", 1, 800, Duration.ofHours(1),
				l -> l.reserve(UUID.randomUUID(), Instant.now()));
		listing("Tủ sách", 1, 800, Duration.ofHours(1), l -> l.hide(Instant.now()));

		assertThat(discovery.sellerListings(seller, null, null, null).items()).extracting(ListingCard::id)
			.containsExactly(reserved.getId(), active.getId());
	}

	@Test
	@DisplayName("A08/A12: the nine seeded categories are active")
	void listsActiveCategories() {
		assertThat(discovery.activeCategories()).hasSizeGreaterThanOrEqualTo(9);
	}

	private Listing listing(String title, long price, double metersEast, Duration age, Consumer<Listing> change) {
		return transaction.execute(status -> {
			Listing listing = Listing.publish(newListing(title, price, eastOf(origin, metersEast)),
					Instant.now().minus(age));
			if (change != null) {
				change.accept(listing);
			}
			return listings.save(listing);
		});
	}

	private NewListing newListing(String title, long price, GeoPoint pin) {
		ListingContent content = new ListingContent(new ListingTitle(title), new ListingDescription(null),
				ItemCondition.LIKE_NEW, new Price(price), true, new MeetingSpot(null));
		return new NewListing(seller, UUID.randomUUID(), (short) 1, content, ListingLocation.pinnedAt(pin, BLUR),
				"Phường Test");
	}

	private SearchQuery query(String keyword, SortOrder sort) {
		return new SearchQuery(new SearchOrigin(null, origin, null), keyword, null, null, null, Set.of(), false, null,
				false, sort);
	}

	private static SearchQuery withSold(SearchQuery query) {
		return new SearchQuery(query.origin(), query.keyword(), query.categoryId(), query.minPriceVnd(),
				query.maxPriceVnd(), query.conditions(), query.freeOnly(), query.radiusKm(), true, query.sort());
	}

	private static MapQuery mapAround(GeoPoint center, int zoom) {
		double span = 0.05;
		return new MapQuery(null, new Viewport(center.latitude() - span, center.longitude() - span,
				center.latitude() + span, center.longitude() + span), zoom, null, null, null, false);
	}

	private static GeoPoint eastOf(GeoPoint from, double meters) {
		double metersPerDegree = METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(from.latitude()));
		return new GeoPoint(from.latitude(), from.longitude() + meters / metersPerDegree);
	}

	private static GeoPoint northOf(GeoPoint from, double meters) {
		return new GeoPoint(from.latitude() + meters / METERS_PER_DEGREE_LATITUDE, from.longitude());
	}

}
