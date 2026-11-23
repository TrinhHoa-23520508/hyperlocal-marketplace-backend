package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;
import static vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.form;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateFailure;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.listing.ListingPriceDropped;
import vn.edu.uit.nearby.nearby_backend.listing.ListingPublished;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.CreateListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.EditListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.Seller;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingContent;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingDescription;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingLocation;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingTitle;
import vn.edu.uit.nearby.nearby_backend.listing.domain.MeetingSpot;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Price;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * UC-LS-01 and UC-LS-02 against PostGIS, the real trading gate and the real pin rule.
 */
@IntegrationTest
@RecordApplicationEvents
class ListingPublishingIntegrationTests {

	@Autowired
	ListingPublishingService publishing;

	@Autowired
	ListingRepository listings;

	@Autowired
	ListingImageRepository images;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEvents events;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	@Autowired
	AreaVerificationService areaVerification;

	private ListingTestData data;

	private Seller seller;

	@BeforeEach
	void createSeller() {
		data = new ListingTestData(new AreaTestAccounts(users, locationConsents, appParameters), areaVerification,
				images, transaction);
		seller = data.aSeller();
	}

	@Test
	@DisplayName("UC-LS-01 step 1: an eligible seller sees an open gate and 3 posts left today")
	void showsTheGateAndTheRemainingQuota() {
		PostingEligibility eligibility = publishing.postingEligibility(seller.id());

		assertThat(eligibility.canPost()).isTrue();
		assertThat(eligibility.allowance().remainingTodayIfLimited()).hasValue(3);
		assertThat(eligibility.allowance().remainingOpen()).isEqualTo(30);
	}

	@Test
	@DisplayName("UC-LS-01 step 1: a user without an area sees why the gate is closed")
	void showsWhyTheGateIsClosed() {
		PostingEligibility eligibility = publishing.postingEligibility(data.aBuyerWithoutArea());

		assertThat(eligibility.canPost()).isFalse();
		assertThat(eligibility.tradingGate().failures()).contains(TradingGateFailure.NO_VALID_AREA);
	}

	@Test
	@DisplayName("FR-LS-01: a valid submission creates exactly one ACTIVE listing with its images in order")
	void createsAnActiveListing() {
		List<UUID> imageIds = data.readyImages(seller.id(), 3);

		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 150_000));

		assertThat(created.status()).isEqualTo(ListingStatus.ACTIVE);
		assertThat(created.areaId()).isEqualTo(seller.areaId());
		assertThat(created.images()).extracting(ListingImageView::id).containsExactlyElementsOf(imageIds);
		assertThat(created.images()).extracting(ListingImageView::position).containsExactly(1, 2, 3);
		assertThat(created.images().getFirst().thumbnailUrl()).endsWith("_320.webp");
		assertThat(created.version()).isZero();
		assertThat(listings.countBySellerIdAndStatusIn(seller.id(), ListingStatus.openStatuses())).isOne();
		assertThat(events.stream(ListingPublished.class).filter(event -> event.listingId().equals(created.id())))
			.hasSize(1);
	}

	@Test
	@DisplayName("FR-LS-03: the stored public location is the 300 m cell center, the pin stays private")
	void storesTheBlurredLocation() {
		GeoPoint pin = northOf(seller.areaCenter(), 120);

		OwnerListingView created = create(seller, form(pin, data.readyImages(seller.id(), 1), 0));

		assertThat(created.pin()).isEqualTo(pin);
		assertThat(created.publicPoint()).isEqualTo(new GridSnapper(300).snap(pin).center());
		assertThat(created.free()).isTrue();
	}

	@Test
	@DisplayName("FR-LS-01: a seller who fails the trading gate gets 403 TRADING_GATE_FAILED")
	void refusesASellerWhoFailsTheGate() {
		UUID noArea = data.aBuyerWithoutArea();

		assertCode(() -> publishing.create(
				new CreateListing(noArea, seller.areaId(), form(seller.areaCenter(), data.readyImages(noArea, 1), 1))),
				"TRADING_GATE_FAILED");
	}

	@Test
	@DisplayName("FR-LS-01/FR-AR-09: a pin 1 501 m from the area center is a 422 PIN_OUTSIDE_AREA")
	void refusesAPinOutsideTheArea() {
		GeoPoint farPin = northOf(seller.areaCenter(), 1501);

		assertCode(() -> create(seller, form(farPin, data.readyImages(seller.id(), 1), 1)), "PIN_OUTSIDE_AREA");
	}

	@Test
	@DisplayName("FR-LS-01: another seller's area is a 422 PIN_OUTSIDE_AREA")
	void refusesAnotherSellersArea() {
		Seller other = data.aSeller();

		assertCode(() -> publishing.create(new CreateListing(seller.id(), other.areaId(),
				form(other.areaCenter(), data.readyImages(seller.id(), 1), 1))), "PIN_OUTSIDE_AREA");
	}

	@Test
	@DisplayName("BR-07: the 4th listing in a day before a completed transaction is a 429 LISTING_DAILY_LIMIT")
	void refusesTheFourthListingOfTheDay() {
		for (int posted = 0; posted < 3; posted++) {
			create(seller, form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1));
		}

		assertThatThrownBy(() -> create(seller, form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1)))
			.isInstanceOf(RateLimitExceededException.class)
			.hasFieldOrPropertyWithValue("code", "LISTING_DAILY_LIMIT")
			.satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfter()).hasValueSatisfying(
					retryAfter -> assertThat(retryAfter).isBetween(Duration.ofHours(23), Duration.ofHours(24))));
		assertThat(publishing.postingEligibility(seller.id()).allowance().remainingTodayIfLimited()).hasValue(0);
	}

	@Test
	@DisplayName("FR-LS-04: the 31st open listing is a 409 LISTING_OPEN_LIMIT")
	void refusesTheThirtyFirstOpenListing() {
		insertOldOpenListings(seller, 30);

		assertCode(() -> create(seller, form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1)),
				"LISTING_OPEN_LIMIT");
	}

	@Test
	@DisplayName("FR-LS-01: another user's image is a 422 IMAGE_INVALID")
	void refusesAnotherUsersImage() {
		UUID foreignImage = data.readyImage(UUID.randomUUID());

		assertCode(() -> create(seller, form(seller.areaCenter(), List.of(foreignImage), 1)), "IMAGE_INVALID");
		assertThat(listings.countBySellerIdAndStatusIn(seller.id(), ListingStatus.openStatuses())).isZero();
	}

	@Test
	void refusesAnUnknownImage() {
		assertCode(() -> create(seller, form(seller.areaCenter(), List.of(UUID.randomUUID()), 1)), "IMAGE_INVALID");
	}

	@Test
	@DisplayName("FR-LS-01: an image already on another listing is a 422 IMAGE_INVALID")
	void refusesAnImageOfAnotherListing() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		create(seller, form(seller.areaCenter(), imageIds, 1));

		assertCode(() -> create(seller, form(seller.areaCenter(), imageIds, 1)), "IMAGE_INVALID");
	}

	@Test
	@DisplayName("FR-LS-01: 11 images are a 400 VALIDATION_FAILED")
	void refusesElevenImages() {
		assertCode(() -> create(seller, form(seller.areaCenter(), data.readyImages(seller.id(), 11), 1)),
				"VALIDATION_FAILED");
	}

	@Test
	@DisplayName("FR-LS-15: an inactive category is a 422 CATEGORY_INACTIVE")
	void refusesAnInactiveCategory() {
		short inactive = jdbc.queryForObject(
				"INSERT INTO categories (code, name, active) VALUES (?, 'Ngừng', false) RETURNING id", Short.class,
				"RETIRED_" + UUID.randomUUID().toString().substring(0, 8));
		ListingForm retired = withCategory(form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1), inactive);

		assertCode(() -> create(seller, retired), "CATEGORY_INACTIVE");
	}

	@Test
	@DisplayName("FR-DC-02: an unknown category is a 400 VALIDATION_FAILED")
	void refusesAnUnknownCategory() {
		ListingForm unknown = withCategory(form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1),
				Short.MAX_VALUE);

		assertCode(() -> create(seller, unknown), "VALIDATION_FAILED");
	}

	@Test
	@DisplayName("FR-LS-06: lowering the price of an ACTIVE listing publishes exactly one ListingPriceDropped")
	void editLowersThePriceOnce() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 150_000));

		OwnerListingView edited = publishing.edit(new EditListing(seller.id(), created.id(), created.version(),
				form(seller.areaCenter(), imageIds, 120_000)));

		assertThat(edited.priceVnd()).isEqualTo(120_000);
		assertThat(edited.version()).isEqualTo(created.version() + 1);
		assertThat(events.stream(ListingPriceDropped.class).filter(event -> event.listingId().equals(created.id())))
			.singleElement()
			.satisfies(event -> {
				assertThat(event.oldPriceVnd()).isEqualTo(150_000);
				assertThat(event.newPriceVnd()).isEqualTo(120_000);
			});
	}

	@Test
	void editRaisingThePricePublishesNothing() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 150_000));

		publishing.edit(new EditListing(seller.id(), created.id(), created.version(),
				form(seller.areaCenter(), imageIds, 180_000)));

		assertThat(events.stream(ListingPriceDropped.class)).isEmpty();
	}

	@Test
	@DisplayName("FR-LS-06: images can be reordered, added and dropped; dropped ones become orphans")
	void editReordersImages() {
		List<UUID> first = data.readyImages(seller.id(), 2);
		OwnerListingView created = create(seller, form(seller.areaCenter(), first, 1));
		UUID added = data.readyImage(seller.id());

		OwnerListingView edited = publishing.edit(new EditListing(seller.id(), created.id(), created.version(),
				form(seller.areaCenter(), List.of(added, first.get(1)), 1)));

		assertThat(edited.images()).extracting(ListingImageView::id).containsExactly(added, first.get(1));
		assertThat(images.findById(first.get(0)).orElseThrow().isAttached()).isFalse();
	}

	@Test
	@DisplayName("FR-LS-06: a stale version is a 409 CONCURRENT_MODIFICATION")
	void refusesAStaleVersion() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 1));
		publishing.edit(
				new EditListing(seller.id(), created.id(), created.version(), form(seller.areaCenter(), imageIds, 2)));

		assertCode(() -> publishing.edit(
				new EditListing(seller.id(), created.id(), created.version(), form(seller.areaCenter(), imageIds, 3))),
				"CONCURRENT_MODIFICATION");
	}

	@Test
	@DisplayName("FR-LS-06: a moved pin must stay inside the listing's area")
	void refusesToMoveThePinOutsideTheArea() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 1));

		assertCode(() -> publishing.edit(new EditListing(seller.id(), created.id(), created.version(),
				form(northOf(seller.areaCenter(), 1501), imageIds, 1))), "PIN_OUTSIDE_AREA");
	}

	@Test
	@DisplayName("FR-ID-18: another seller's listing does not exist for the editor")
	void anotherSellersListingIsNotFound() {
		List<UUID> imageIds = data.readyImages(seller.id(), 1);
		OwnerListingView created = create(seller, form(seller.areaCenter(), imageIds, 1));
		UUID intruder = data.aSeller().id();

		assertCode(() -> publishing
			.edit(new EditListing(intruder, created.id(), created.version(), form(seller.areaCenter(), imageIds, 1))),
				"LISTING_NOT_FOUND");
	}

	private OwnerListingView create(Seller owner, ListingForm form) {
		return publishing.create(new CreateListing(owner.id(), owner.areaId(), form));
	}

	private void insertOldOpenListings(Seller owner, int count) {
		Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
		ListingContent content = new ListingContent(new ListingTitle("Bàn học gỗ"), new ListingDescription(null),
				ItemCondition.USED, new Price(1), true, new MeetingSpot(null));
		transaction.executeWithoutResult(status -> {
			for (int index = 0; index < count; index++) {
				listings.save(Listing.publish(new Listing.NewListing(owner.id(), owner.areaId(), (short) 1, content,
						ListingLocation.pinnedAt(owner.areaCenter(), new GridSnapper(300)), null), twoDaysAgo));
			}
		});
	}

	private static ListingForm withCategory(ListingForm form, short categoryId) {
		return new ListingForm(form.title(), form.description(), form.condition(), form.priceVnd(), form.acceptOffers(),
				form.meetingSpot(), categoryId, form.pin(), form.imageIds());
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", code);
	}

}
