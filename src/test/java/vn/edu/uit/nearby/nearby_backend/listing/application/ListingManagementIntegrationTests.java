package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;
import static vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.form;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionType;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.listing.ListingStatusChanged;
import vn.edu.uit.nearby.nearby_backend.listing.application.FavoriteService.FavoriteCard;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.ChangeListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.CreateListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.MyListingsService.MyListingsPage;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.Seller;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.FavoriteRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * UC-LS-03..09 and the listing reactions to area, sanction and account events, against
 * PostGIS.
 */
@IntegrationTest
@RecordApplicationEvents
class ListingManagementIntegrationTests {

	@Autowired
	ListingPublishingService publishing;

	@Autowired
	ListingLifecycleService lifecycle;

	@Autowired
	FavoriteService favorites;

	@Autowired
	MyListingsService myListings;

	@Autowired
	FavoriteRepository favoriteRepository;

	@Autowired
	ListingEvents listingEvents;

	@Autowired
	Clock clock;

	@Autowired
	ListingRepository listings;

	@Autowired
	ListingImageRepository images;

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

	private OwnerListingView listing;

	@BeforeEach
	void publishOneListing() {
		data = new ListingTestData(new AreaTestAccounts(users, locationConsents, appParameters), areaVerification,
				images, transaction);
		seller = data.aSeller();
		listing = publish(seller);
	}

	@Nested
	class HideAndUnhide {

		@Test
		@DisplayName("FR-LS-07: hiding sets HIDDEN (OWNER) and publishes ListingStatusChanged")
		void hidesTheListing() {
			OwnerListingView hidden = lifecycle.hide(change(listing));

			assertThat(hidden.status()).isEqualTo(ListingStatus.HIDDEN);
			assertThat(hidden.hiddenReason()).isEqualTo(HiddenReason.OWNER);
			assertThat(hidden.version()).isEqualTo(listing.version() + 1);
			assertThat(statusChangesOf(listing.id())).containsExactly("ACTIVE>HIDDEN");
		}

		@Test
		@DisplayName("FR-LS-07: showing it again makes it ACTIVE")
		void unhidesTheListing() {
			OwnerListingView hidden = lifecycle.hide(change(listing));

			OwnerListingView shown = lifecycle.unhide(change(hidden));

			assertThat(shown.status()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(shown.hiddenReason()).isNull();
		}

		@Test
		void refusesAStaleVersion() {
			lifecycle.hide(change(listing));

			assertCode(() -> lifecycle.unhide(change(listing)), "CONCURRENT_MODIFICATION");
		}

	}

	@Nested
	class Delete {

		@Test
		@DisplayName("FR-LS-08: a deleted listing is DELETED, without its pin, and its images become orphans")
		void deletesTheListing() {
			lifecycle.delete(seller.id(), listing.id());

			Listing deleted = listings.findById(listing.id()).orElseThrow();
			assertThat(deleted.getStatus()).isEqualTo(ListingStatus.DELETED);
			assertThat(deleted.pin()).isEqualTo(deleted.publicPoint());
			assertThat(images.findByListingIdOrderByPosition(listing.id())).isEmpty();
			assertThat(statusChangesOf(listing.id())).containsExactly("ACTIVE>DELETED");
		}

		@Test
		@DisplayName("FR-LS-08: a deleted listing is 404 for its owner and the public")
		void aDeletedListingIsGone() {
			lifecycle.delete(seller.id(), listing.id());

			assertCode(() -> lifecycle.delete(seller.id(), listing.id()), "LISTING_NOT_FOUND");
			assertCode(() -> listings.getVisible(listing.id(), null), "LISTING_NOT_FOUND");
		}

		@Test
		@DisplayName("FR-ID-18: another seller cannot delete the listing")
		void anotherSellerGetsNotFound() {
			assertCode(() -> lifecycle.delete(UUID.randomUUID(), listing.id()), "LISTING_NOT_FOUND");
		}

	}

	@Nested
	class SoldAndBump {

		@Test
		@DisplayName("FR-LS-09 (b): sold outside the app makes the listing SOLD")
		void marksSoldOutsideTheApp() {
			OwnerListingView sold = lifecycle.markSoldOutsideApp(change(listing));

			assertThat(sold.status()).isEqualTo(ListingStatus.SOLD);
			assertThat(sold.soldAt()).isNotNull();
			assertThat(statusChangesOf(listing.id())).containsExactly("ACTIVE>SOLD");
		}

		@Test
		@DisplayName("FR-LS-09: a SOLD listing cannot be sold again: 409 INVALID_LISTING_STATE")
		void refusesToSellTwice() {
			OwnerListingView sold = lifecycle.markSoldOutsideApp(change(listing));

			assertCode(() -> lifecycle.markSoldOutsideApp(change(sold)), "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("FR-LS-10: a bump sets bumped_at; another within 48 h is a 429 BUMP_TOO_SOON")
		void bumpsOncePerCooldown() {
			OwnerListingView bumped = lifecycle.bump(seller.id(), listing.id());

			assertThat(bumped.bumpCount()).isOne();
			assertThat(bumped.nextBumpAllowedAt()).isEqualTo(bumped.bumpedAt().plusSeconds(48 * 3600));
			assertCode(() -> lifecycle.bump(seller.id(), listing.id()), "BUMP_TOO_SOON");
		}

	}

	@Nested
	class Favorites {

		@Test
		@DisplayName("FR-LS-12: saving twice keeps one row; the counter moves by one per user")
		void savingIsIdempotent() {
			UUID buyer = UUID.randomUUID();

			favorites.save(buyer, listing.id());
			favorites.save(buyer, listing.id());
			favorites.save(UUID.randomUUID(), listing.id());

			assertThat(favoriteCount()).isEqualTo(2);
			assertThat(favorites.savedListings(buyer, null, null).items()).hasSize(1);
		}

		@Test
		@DisplayName("FR-LS-12: unsaving twice moves the counter once")
		void unsavingIsIdempotent() {
			UUID buyer = UUID.randomUUID();
			favorites.save(buyer, listing.id());

			favorites.unsave(buyer, listing.id());
			favorites.unsave(buyer, listing.id());

			assertThat(favoriteCount()).isZero();
			assertThat(favorites.savedListings(buyer, null, null).items()).isEmpty();
		}

		@Test
		@DisplayName("FR-ID-18: another seller's hidden listing cannot be saved")
		void cannotSaveAHiddenListing() {
			lifecycle.hide(change(listing));

			assertCode(() -> favorites.save(UUID.randomUUID(), listing.id()), "LISTING_NOT_FOUND");
		}

		@Test
		@DisplayName("FR-LS-12: a saved listing that was deleted is listed as no longer available")
		void flagsADeletedListingAsUnavailable() {
			UUID buyer = UUID.randomUUID();
			favorites.save(buyer, listing.id());

			lifecycle.delete(seller.id(), listing.id());

			assertThat(favorites.savedListings(buyer, null, null).items()).singleElement().satisfies(card -> {
				assertThat(card.status()).isEqualTo(ListingStatus.DELETED);
				assertThat(card.available()).isFalse();
			});
		}

		@Test
		@DisplayName("UC-LS-09: saved listings page newest first without repeats")
		void pagesSavedListings() {
			UUID buyer = UUID.randomUUID();
			OwnerListingView second = publish(seller);
			OwnerListingView third = publish(seller);
			favorites.save(buyer, listing.id());
			favorites.save(buyer, second.id());
			favorites.save(buyer, third.id());

			CursorPage<FavoriteCard> first = favorites.savedListings(buyer, null, 2);
			CursorPage<FavoriteCard> rest = favorites.savedListings(buyer, first.nextCursor(), 2);

			assertThat(first.hasMore()).isTrue();
			assertThat(first.items().getFirst().coverThumbnailUrl()).endsWith("_320.webp");
			assertThat(rest.hasMore()).isFalse();
			assertThat(List.of(first.items().get(0), first.items().get(1), rest.items().get(0)))
				.extracting(FavoriteCard::listingId)
				.containsExactlyInAnyOrder(listing.id(), second.id(), third.id());
		}

	}

	@Nested
	class MyListings {

		@Test
		@DisplayName("FR-LS-11: listings grouped by state with stored counters; DELETED is not shown")
		void groupsListingsByState() {
			OwnerListingView hidden = publish(seller);
			lifecycle.hide(change(hidden));
			OwnerListingView deleted = publish(seller);
			lifecycle.delete(seller.id(), deleted.id());
			favorites.save(UUID.randomUUID(), listing.id());

			MyListingsPage page = myListings.myListings(seller.id(), Set.of(ListingStatus.ACTIVE), null, null);

			assertThat(page.countsByStatus()).containsEntry(ListingStatus.ACTIVE, 1L)
				.containsEntry(ListingStatus.HIDDEN, 1L)
				.containsEntry(ListingStatus.SOLD, 0L)
				.doesNotContainKey(ListingStatus.DELETED);
			assertThat(page.listings().items()).singleElement().satisfies(card -> {
				assertThat(card.id()).isEqualTo(listing.id());
				assertThat(card.favorites()).isOne();
				assertThat(card.openOffers()).isZero();
				assertThat(card.coverThumbnailUrl()).endsWith("_320.webp");
			});
		}

		@Test
		@DisplayName("FR-LS-07: a hidden listing stays in \"Tin của tôi\"")
		void listsEveryStateButDeletedByDefault() {
			lifecycle.hide(change(listing));

			MyListingsPage page = myListings.myListings(seller.id(), Set.of(), null, null);

			assertThat(page.listings().items()).extracting(MyListingsService.OwnerListingCard::status)
				.containsExactly(ListingStatus.HIDDEN);
		}

	}

	@Nested
	class Reactions {

		@Test
		@DisplayName("FR-AR-07: removing the area hides its listings (AREA_REMOVED), once")
		void hidesTheListingsOfARemovedArea() {
			AreaRemoved removed = new AreaRemoved(seller.id(), seller.areaId(), Instant.now());

			react(listeners -> listeners.onAreaRemoved(removed));
			react(listeners -> listeners.onAreaRemoved(removed));

			Listing stored = listings.findById(listing.id()).orElseThrow();
			assertThat(stored.getStatus()).isEqualTo(ListingStatus.HIDDEN);
			assertThat(stored.getHiddenReason()).isEqualTo(HiddenReason.AREA_REMOVED);
			assertThat(statusChangesOf(listing.id())).containsExactly("ACTIVE>HIDDEN");
		}

		@Test
		void ignoresAnotherUsersArea() {
			react(listeners -> listeners
				.onAreaRemoved(new AreaRemoved(UUID.randomUUID(), seller.areaId(), Instant.now())));

			assertThat(listings.findById(listing.id()).orElseThrow().getStatus()).isEqualTo(ListingStatus.ACTIVE);
		}

		@Test
		@DisplayName("FR-MD-05: a sanction hides the seller's ACTIVE listings; lifting restores only those")
		void sanctionHidesAndLiftRestores() {
			OwnerListingView ownerHidden = publish(seller);
			lifecycle.hide(change(ownerHidden));
			UserSanctioned sanctioned = new UserSanctioned(seller.id(), SanctionType.SUSPENSION, null, Instant.now());

			react(listeners -> listeners.onUserSanctioned(sanctioned));
			react(listeners -> listeners.onUserSanctioned(sanctioned));
			assertThat(listings.findById(listing.id()).orElseThrow().getHiddenReason())
				.isEqualTo(HiddenReason.SANCTION);

			SanctionLifted lifted = new SanctionLifted(seller.id(), Instant.now());
			react(listeners -> listeners.onSanctionLifted(lifted));
			react(listeners -> listeners.onSanctionLifted(lifted));

			assertThat(listings.findById(listing.id()).orElseThrow().getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listings.findById(ownerHidden.id()).orElseThrow().getHiddenReason())
				.isEqualTo(HiddenReason.OWNER);
		}

		@Test
		@DisplayName("UC-SY-12: an anonymised account loses its favourites and its listings become DELETED, once")
		void anonymisesTheSellersListingsAndFavourites() {
			Seller other = data.aSeller();
			OwnerListingView othersListing = publish(other);
			favorites.save(seller.id(), othersListing.id());
			AccountDeleted deleted = new AccountDeleted(seller.id(), Instant.now());

			react(listeners -> listeners.onAccountDeleted(deleted));
			react(listeners -> listeners.onAccountDeleted(deleted));

			Listing anonymised = listings.findById(listing.id()).orElseThrow();
			assertThat(anonymised.getStatus()).isEqualTo(ListingStatus.DELETED);
			assertThat(anonymised.pin()).isEqualTo(anonymised.publicPoint());
			assertThat(images.findByListingIdOrderByPosition(listing.id())).isEmpty();
			assertThat(favorites.savedListings(seller.id(), null, null).items()).isEmpty();
			assertThat(listings.findById(othersListing.id()).orElseThrow().getFavoriteCount()).isZero();
		}

	}

	private OwnerListingView publish(Seller owner) {
		return publishing.create(new CreateListing(owner.id(), owner.areaId(),
				form(northOf(owner.areaCenter(), 50), data.readyImages(owner.id(), 1), 100_000)));
	}

	/**
	 * Calls the listener the way the outbox does: in its own transaction, synchronously.
	 */
	private void react(Consumer<ListingSafetyListeners> handling) {
		ListingSafetyListeners listeners = new ListingSafetyListeners(listings, images, favoriteRepository,
				listingEvents, clock);
		transaction.executeWithoutResult(status -> handling.accept(listeners));
	}

	private ChangeListing change(OwnerListingView view) {
		return new ChangeListing(seller.id(), view.id(), view.version());
	}

	private int favoriteCount() {
		return listings.findById(listing.id()).orElseThrow().getFavoriteCount();
	}

	private List<String> statusChangesOf(UUID listingId) {
		return events.stream(ListingStatusChanged.class)
			.filter(event -> event.listingId().equals(listingId))
			.map(event -> event.fromStatus() + ">" + event.toStatus())
			.toList();
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", code);
	}

}
