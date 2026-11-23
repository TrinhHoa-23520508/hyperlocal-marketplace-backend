package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.form;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingModeration;
import vn.edu.uit.nearby.nearby_backend.listing.ListingReservations;
import vn.edu.uit.nearby.nearby_backend.listing.ListingStatusChanged;
import vn.edu.uit.nearby.nearby_backend.listing.SellerScores;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.CreateListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.Seller;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The facades other modules call (DEC-LS-2; registers LS-W3, W4, W6, W7, W9, W12).
 */
@IntegrationTest
@RecordApplicationEvents
class ListingModuleApiIntegrationTests {

	@Autowired
	ListingLookup lookup;

	@Autowired
	ListingReservations reservations;

	@Autowired
	ListingCounters counters;

	@Autowired
	SellerScores sellerScores;

	@Autowired
	ListingModeration moderation;

	@Autowired
	ListingRepository listings;

	@Autowired
	ApplicationEvents events;

	private Listing listing;

	@BeforeEach
	void saveAnActiveListing() {
		listing = listings.save(ListingFixtures.anActiveListing());
	}

	@Nested
	class Lookup {

		@Test
		@DisplayName("LS-W12: other modules get the public point, never the pin")
		void describesTheListing() {
			assertThat(lookup.find(listing.getId())).hasValueSatisfying(snapshot -> {
				assertThat(snapshot.sellerId()).isEqualTo(listing.getSellerId());
				assertThat(snapshot.areaId()).isEqualTo(listing.getAreaId());
				assertThat(snapshot.status()).isEqualTo("ACTIVE");
				assertThat(snapshot.priceVnd()).isEqualTo(150_000);
				assertThat(snapshot.publicPoint()).isEqualTo(listing.publicPoint()).isNotEqualTo(listing.pin());
				assertThat(snapshot.takesNewChats()).isTrue();
				assertThat(snapshot.takesNewOffers()).isTrue();
			});
		}

		@Test
		@DisplayName("FR-CH-11: a DELETED listing is still described to other modules")
		void describesADeletedListing() {
			Listing deleted = listings.save(ListingFixtures.aListing(ListingStatus.DELETED));

			assertThat(lookup.find(deleted.getId())).hasValueSatisfying(snapshot -> {
				assertThat(snapshot.status()).isEqualTo("DELETED");
				assertThat(snapshot.takesNewChats()).isFalse();
			});
		}

		@Test
		void knowsNothingOfAnUnknownListing() {
			assertThat(lookup.find(UUID.randomUUID())).isEmpty();
		}

	}

	@Nested
	class Reservations {

		@Test
		@DisplayName("tab:sm-listing: an ACCEPTED transaction reserves the listing, once")
		void reservesForATransaction() {
			UUID transactionId = UUID.randomUUID();

			reservations.reserve(listing.getId(), transactionId);
			reservations.reserve(listing.getId(), transactionId);

			Listing reserved = reload();
			assertThat(reserved.getStatus()).isEqualTo(ListingStatus.RESERVED);
			assertThat(reserved.getReservedTransactionId()).isEqualTo(transactionId);
			assertThat(statusChanges()).containsExactly("ACTIVE>RESERVED");
		}

		@Test
		@DisplayName("BR-04: a second transaction cannot reserve a RESERVED listing")
		void refusesASecondTransaction() {
			reservations.reserve(listing.getId(), UUID.randomUUID());

			assertCode(() -> reservations.reserve(listing.getId(), UUID.randomUUID()), "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("tab:sm-listing: a CANCELLED or EXPIRED transaction makes the listing ACTIVE again")
		void releasesTheReservation() {
			UUID transactionId = UUID.randomUUID();
			reservations.reserve(listing.getId(), transactionId);

			reservations.release(listing.getId(), transactionId);

			assertThat(reload().getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(statusChanges()).containsExactly("ACTIVE>RESERVED", "RESERVED>ACTIVE");
		}

		@Test
		void refusesToReleaseForAnotherTransaction() {
			reservations.reserve(listing.getId(), UUID.randomUUID());

			assertCode(() -> reservations.release(listing.getId(), UUID.randomUUID()), "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("tab:sm-listing: a COMPLETED transaction makes the listing SOLD")
		void completesTheReservedSale() {
			UUID transactionId = UUID.randomUUID();
			reservations.reserve(listing.getId(), transactionId);

			reservations.completeReservedSale(listing.getId(), transactionId);

			Listing sold = reload();
			assertThat(sold.getStatus()).isEqualTo(ListingStatus.SOLD);
			assertThat(sold.getSoldAt()).isNotNull();
		}

		@Test
		@DisplayName("FR-LS-09 (a): sold to a chat partner, then denied by the buyer: back to ACTIVE")
		void marksSoldToAChatPartnerAndReverts() {
			reservations.markSoldToChatPartner(listing.getId());
			assertThat(reload().getStatus()).isEqualTo(ListingStatus.SOLD);

			reservations.revertDeniedSale(listing.getId());

			Listing reverted = reload();
			assertThat(reverted.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(reverted.getSoldAt()).isNull();
			assertThat(statusChanges()).containsExactly("ACTIVE>SOLD", "SOLD>ACTIVE");
		}

		@Test
		void refusesAnUnknownListing() {
			assertCode(() -> reservations.reserve(UUID.randomUUID(), UUID.randomUUID()), "LISTING_NOT_FOUND");
		}

	}

	@Nested
	class CountersAndScores {

		@Autowired
		ListingPublishingService publishing;

		@Autowired
		ListingImageRepository images;

		@Autowired
		TransactionTemplate transaction;

		@Autowired
		UserRepository users;

		@Autowired
		LocationConsentService locationConsents;

		@Autowired
		AppParameters appParameters;

		@Autowired
		AreaVerificationService areaVerification;

		@Test
		@DisplayName("FR-LS-11: each new conversation adds one to chat_count")
		void countsConversations() {
			counters.incrementChatCount(listing.getId());
			counters.incrementChatCount(listing.getId());
			counters.incrementChatCount(UUID.randomUUID());

			assertThat(reload().getChatCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("FR-DC-03/05: a pushed score reaches every listing of the seller and new ones copy it")
		void copiesTheSellerScore() {
			ListingTestData data = new ListingTestData(new AreaTestAccounts(users, locationConsents, appParameters),
					areaVerification, images, transaction);
			Seller seller = data.aSeller();
			OwnerListingView first = publishing.create(new CreateListing(seller.id(), seller.areaId(),
					form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1)));

			sellerScores.update(seller.id(), new BigDecimal("72.50"), "TRUSTED");
			OwnerListingView second = publishing.create(new CreateListing(seller.id(), seller.areaId(),
					form(seller.areaCenter(), data.readyImages(seller.id(), 1), 1)));

			for (UUID id : List.of(first.id(), second.id())) {
				Listing stored = listings.findById(id).orElseThrow();
				assertThat(stored.getSellerScore()).isEqualByComparingTo("72.50");
				assertThat(stored.getSellerTier()).isEqualTo(SellerTier.TRUSTED);
			}
		}

		@Test
		void refusesAScoreOrTierOutsideTheSrsValues() {
			UUID sellerId = listing.getSellerId();

			assertThatThrownBy(() -> sellerScores.update(sellerId, new BigDecimal("100.01"), "TRUSTED"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> sellerScores.update(sellerId, BigDecimal.TEN, "GOLD"))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Moderation {

		@Test
		@DisplayName("FR-MD-02: a moderator removes a listing with a reason")
		void removesWithAReason() {
			UUID moderatorId = UUID.randomUUID();

			moderation.remove(listing.getId(), moderatorId, "Hàng cấm");

			Listing removed = reload();
			assertThat(removed.getStatus()).isEqualTo(ListingStatus.REMOVED);
			assertThat(removed.getRemovalReason()).isEqualTo("Hàng cấm");
			assertThat(removed.getRemovedBy()).isEqualTo(moderatorId);
			assertThat(statusChanges()).containsExactly("ACTIVE>REMOVED");
		}

		@Test
		void refusesToRemoveADeletedListing() {
			Listing deleted = listings.save(ListingFixtures.aListing(ListingStatus.DELETED));

			assertCode(() -> moderation.remove(deleted.getId(), UUID.randomUUID(), "Hàng cấm"),
					"INVALID_LISTING_STATE");
		}

	}

	private Listing reload() {
		return listings.findById(listing.getId()).orElseThrow();
	}

	private List<String> statusChanges() {
		return events.stream(ListingStatusChanged.class)
			.filter(event -> event.listingId().equals(listing.getId()))
			.map(event -> event.fromStatus() + ">" + event.toStatus())
			.toList();
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", code);
	}

}
