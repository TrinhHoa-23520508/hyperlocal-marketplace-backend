package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.BLUR_GRID;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.BUMP_RULES;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.NOW;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.PIN;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.aListing;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.anActiveListing;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.changes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.ListingChanges;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

class ListingTests {

	@Nested
	class Publish {

		@Test
		@DisplayName("FR-LS-01: a valid submission creates exactly one ACTIVE listing")
		void publishesAnActiveListing() {
			UUID sellerId = UUID.randomUUID();

			Listing listing = anActiveListing(sellerId);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listing.isOwnedBy(sellerId)).isTrue();
			assertThat(listing.getPublishedAt()).isEqualTo(NOW);
			assertThat(listing.getHiddenReason()).isNull();
			assertThat(listing.getBumpCount()).isZero();
			assertThat(listing.getLocalityName()).isEqualTo("Linh Trung");
		}

		@Test
		@DisplayName("FR-LS-03/BR-05: the public point is the grid-cell center, not the pin")
		void keepsThePinPrivateAndPublishesTheCellCenter() {
			Listing listing = anActiveListing();

			assertThat(listing.pin()).isEqualTo(PIN);
			assertThat(listing.publicPoint()).isEqualTo(BLUR_GRID.snap(PIN).center()).isNotEqualTo(PIN);
		}

		@Test
		void storesTheContentFields() {
			Listing listing = anActiveListing();

			assertThat(listing.getTitle()).isEqualTo("Áo khoác da nam");
			assertThat(listing.getDescription()).isEqualTo("Còn mới 90%");
			assertThat(listing.getCondition()).isEqualTo(ItemCondition.LIKE_NEW);
			assertThat(listing.askingPrice()).isEqualTo(new Price(150_000));
			assertThat(listing.isAcceptOffers()).isTrue();
			assertThat(listing.getMeetingSpotText()).isEqualTo("Cổng KTX khu A");
		}

	}

	@Nested
	class Edit {

		@Test
		@DisplayName("FR-LS-06: every field but the area can change")
		void changesTheContentAndMovesThePublicPointWithThePin() {
			Listing listing = anActiveListing();
			UUID areaId = listing.getAreaId();
			GeoPoint movedPin = new GeoPoint(10.8800, 106.8100);

			listing.edit(new ListingChanges((short) 2, ListingFixtures.content(90_000),
					ListingLocation.pinnedAt(movedPin, BLUR_GRID)), NOW.plusSeconds(60));

			assertThat(listing.getCategoryId()).isEqualTo((short) 2);
			assertThat(listing.askingPrice()).isEqualTo(new Price(90_000));
			assertThat(listing.pin()).isEqualTo(movedPin);
			assertThat(listing.publicPoint()).isEqualTo(BLUR_GRID.snap(movedPin).center());
			assertThat(listing.getAreaId()).isEqualTo(areaId);
			assertThat(listing.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "SOLD", "DELETED", "REMOVED" })
		@DisplayName("FR-LS-06: SOLD, REMOVED and DELETED listings cannot be edited")
		void refusesToEditAClosedListing(ListingStatus status) {
			Listing listing = aListing(status);

			assertThatThrownBy(() -> listing.edit(changes(150_000), NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("FR-LS-06: a price change of a RESERVED listing is refused, naming the transaction")
		void refusesAPriceChangeWhileReserved() {
			Listing listing = anActiveListing();
			UUID transactionId = UUID.randomUUID();
			listing.reserve(transactionId, NOW);

			assertThatThrownBy(() -> listing.edit(changes(100_000), NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "LISTING_HAS_OPEN_TRANSACTIONS")
				.extracting(ex -> ((BusinessException) ex).getProperties().get("transactionId"))
				.isEqualTo(transactionId.toString());
		}

		@Test
		void allowsOtherChangesWhileReserved() {
			Listing listing = aListing(ListingStatus.RESERVED);

			listing.edit(changes(150_000), NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
		}

		@Test
		void editsAHiddenListing() {
			Listing listing = aListing(ListingStatus.HIDDEN);

			listing.edit(changes(120_000), NOW);

			assertThat(listing.askingPrice()).isEqualTo(new Price(120_000));
		}

	}

	@Nested
	class Version {

		@Test
		@DisplayName("FR-LS-13: a stale version is a 409 CONCURRENT_MODIFICATION")
		void refusesAStaleVersion() {
			Listing listing = anActiveListing();

			assertThatThrownBy(() -> listing.requireVersion(3)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "CONCURRENT_MODIFICATION");
		}

		@Test
		void acceptsTheCurrentVersion() {
			Listing listing = anActiveListing();

			listing.requireVersion(listing.getVersion());

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
		}

	}

	@Nested
	class HideAndUnhide {

		@Test
		@DisplayName("FR-LS-07: hiding sets hidden_reason = OWNER")
		void hidesWithTheOwnerReason() {
			Listing listing = anActiveListing();

			listing.hide(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.OWNER);
		}

		@Test
		@DisplayName("FR-LS-07: showing an owner-hidden listing makes it ACTIVE again")
		void unhidesAnOwnerHiddenListing() {
			Listing listing = aListing(ListingStatus.HIDDEN);

			listing.unhide(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listing.getHiddenReason()).isNull();
		}

		@Test
		@DisplayName("FR-LS-07: hiding a RESERVED listing is refused, naming the transaction")
		void refusesToHideAReservedListing() {
			Listing listing = aListing(ListingStatus.RESERVED);

			assertThatThrownBy(() -> listing.hide(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "LISTING_HAS_OPEN_TRANSACTIONS");
		}

		@Test
		void refusesToHideASoldListing() {
			Listing listing = aListing(ListingStatus.SOLD);

			assertThatThrownBy(() -> listing.hide(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("FR-AR-07: the owner cannot show a listing hidden because its area was removed")
		void refusesToUnhideAListingHiddenForItsRemovedArea() {
			Listing listing = anActiveListing();
			listing.hideForRemovedArea(NOW);

			assertThatThrownBy(() -> listing.unhide(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.AREA_REMOVED);
		}

		@Test
		@DisplayName("FR-MD-05: the owner cannot show a listing hidden by a sanction")
		void refusesToUnhideAListingHiddenByASanction() {
			Listing listing = anActiveListing();
			listing.hideForSanction(NOW);

			assertThatThrownBy(() -> listing.unhide(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		void refusesToUnhideAnActiveListing() {
			Listing listing = anActiveListing();

			assertThatThrownBy(() -> listing.unhide(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

	}

	@Nested
	class Delete {

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "ACTIVE", "HIDDEN" })
		@DisplayName("FR-LS-08: soft delete sets DELETED and deleted_at")
		void deletesAnActiveOrHiddenListing(ListingStatus status) {
			Listing listing = aListing(status);

			listing.delete(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
			assertThat(listing.getDeletedAt()).isEqualTo(NOW);
			assertThat(listing.getHiddenReason()).isNull();
		}

		@Test
		@DisplayName("tab:retention: the exact pin is not kept once the listing is deleted")
		void forgetsThePinOnDeletion() {
			Listing listing = anActiveListing();

			listing.delete(NOW);

			assertThat(listing.pin()).isEqualTo(listing.publicPoint());
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "ACTIVE", "HIDDEN", "RESERVED", "SOLD" })
		@DisplayName("tab:retention: anonymising the seller's account moves every listing to DELETED without its pin")
		void deletesEveryListingOfAnAnonymisedAccount(ListingStatus status) {
			Listing listing = aListing(status);

			listing.anonymiseForDeletedAccount(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
			assertThat(listing.getReservedTransactionId()).isNull();
			assertThat(listing.pin()).isEqualTo(listing.publicPoint());
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "DELETED", "REMOVED" })
		@DisplayName("tab:retention: a deleted or removed listing of an anonymised account keeps its state, not its pin")
		void forgetsThePinOfAGoneListing(ListingStatus status) {
			Listing listing = aListing(status);

			listing.anonymiseForDeletedAccount(NOW);
			listing.anonymiseForDeletedAccount(NOW);

			assertThat(listing.getStatus()).isEqualTo(status);
			assertThat(listing.pin()).isEqualTo(listing.publicPoint());
		}

		@Test
		@DisplayName("FR-LS-08: deleting a RESERVED listing is refused, naming the transaction")
		void refusesToDeleteAReservedListing() {
			Listing listing = aListing(ListingStatus.RESERVED);

			assertThatThrownBy(() -> listing.delete(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "LISTING_HAS_OPEN_TRANSACTIONS");
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "SOLD", "DELETED", "REMOVED" })
		void refusesToDeleteAClosedListing(ListingStatus status) {
			Listing listing = aListing(status);

			assertThatThrownBy(() -> listing.delete(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

	}

	@Nested
	class Sales {

		@Test
		@DisplayName("FR-LS-09 (b): an ACTIVE listing sold outside the app becomes SOLD")
		void marksAnActiveListingSold() {
			Listing listing = anActiveListing();

			listing.markSold(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
			assertThat(listing.getSoldAt()).isEqualTo(NOW);
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "HIDDEN", "SOLD", "DELETED", "REMOVED" })
		@DisplayName("FR-LS-09: a listing that is not ACTIVE cannot be marked sold directly")
		void refusesToMarkSoldAListingThatIsNotActive(ListingStatus status) {
			Listing listing = aListing(status);

			assertThatThrownBy(() -> listing.markSold(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("tab:sm-listing: an ACCEPTED transaction reserves the listing")
		void reservesForAnAcceptedTransaction() {
			Listing listing = anActiveListing();
			UUID transactionId = UUID.randomUUID();

			listing.reserve(transactionId, NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
			assertThat(listing.getReservedTransactionId()).isEqualTo(transactionId);
		}

		@Test
		@DisplayName("BR-04: a second ACCEPTED transaction cannot reserve a RESERVED listing")
		void refusesASecondReservation() {
			Listing listing = aListing(ListingStatus.RESERVED);

			assertThatThrownBy(() -> listing.reserve(UUID.randomUUID(), NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("tab:sm-listing: a CANCELLED or EXPIRED transaction makes the listing ACTIVE again")
		void releasesTheReservation() {
			Listing listing = anActiveListing();
			UUID transactionId = UUID.randomUUID();
			listing.reserve(transactionId, NOW);

			listing.releaseReservation(transactionId, NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listing.getReservedTransactionId()).isNull();
		}

		@Test
		void refusesToReleaseForAnotherTransaction() {
			Listing listing = aListing(ListingStatus.RESERVED);

			assertThatThrownBy(() -> listing.releaseReservation(UUID.randomUUID(), NOW))
				.isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

		@Test
		@DisplayName("tab:sm-listing: a COMPLETED transaction makes the RESERVED listing SOLD")
		void completesTheReservedSale() {
			Listing listing = anActiveListing();
			UUID transactionId = UUID.randomUUID();
			listing.reserve(transactionId, NOW);

			listing.completeReservedSale(transactionId, NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
			assertThat(listing.getReservedTransactionId()).isNull();
			assertThat(listing.getSoldAt()).isEqualTo(NOW);
		}

		@Test
		void refusesToCompleteForAnotherTransaction() {
			Listing listing = aListing(ListingStatus.RESERVED);

			assertThatThrownBy(() -> listing.completeReservedSale(UUID.randomUUID(), NOW))
				.isInstanceOf(ConflictException.class);
		}

		@Test
		@DisplayName("FR-LS-09: a denied sale puts the listing back to ACTIVE and clears sold_at")
		void revertsADeniedSale() {
			Listing listing = aListing(ListingStatus.SOLD);

			listing.revertDeniedSale(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listing.getSoldAt()).isNull();
		}

	}

	@Nested
	class Moderation {

		@Test
		@DisplayName("tab:sm-listing: a moderator removes a listing with a reason")
		void removesWithAReason() {
			Listing listing = aListing(ListingStatus.RESERVED);
			UUID moderatorId = UUID.randomUUID();

			listing.removeByModerator("  Hàng cấm  ", moderatorId, NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.REMOVED);
			assertThat(listing.getRemovalReason()).isEqualTo("Hàng cấm");
			assertThat(listing.getRemovedBy()).isEqualTo(moderatorId);
			assertThat(listing.getReservedTransactionId()).isNull();
		}

		@Test
		void refusesARemovalWithoutAReason() {
			Listing listing = anActiveListing();

			assertThatThrownBy(() -> listing.removeByModerator(" ", UUID.randomUUID(), NOW))
				.isInstanceOf(IllegalArgumentException.class);
			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
		}

		@Test
		@DisplayName("FR-AR-07: an ACTIVE listing of a removed area becomes HIDDEN (AREA_REMOVED)")
		void hidesAListingOfARemovedArea() {
			Listing listing = anActiveListing();

			listing.hideForRemovedArea(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.AREA_REMOVED);
		}

		@Test
		@DisplayName("FR-AR-07: an owner-hidden listing of a removed area is hidden for that reason")
		void replacesTheOwnerReasonWhenTheAreaIsRemoved() {
			Listing listing = aListing(ListingStatus.HIDDEN);

			listing.hideForRemovedArea(NOW);

			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.AREA_REMOVED);
		}

		@Test
		@DisplayName("FR-MD-05: a sanctioned seller's ACTIVE listing becomes HIDDEN (SANCTION)")
		void hidesForASanction() {
			Listing listing = anActiveListing();

			listing.hideForSanction(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.SANCTION);
		}

		@Test
		@DisplayName("tab:sm-listing: lifting the sanction restores only what the sanction hid")
		void restoresAfterTheSanctionIsLifted() {
			Listing listing = anActiveListing();
			listing.hideForSanction(NOW);

			listing.restoreAfterSanction(NOW);

			assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(listing.getHiddenReason()).isNull();
		}

		@Test
		void doesNotRestoreAnOwnerHiddenListingAfterASanction() {
			Listing listing = aListing(ListingStatus.HIDDEN);

			assertThatThrownBy(() -> listing.restoreAfterSanction(NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
			assertThat(listing.getHiddenReason()).isEqualTo(HiddenReason.OWNER);
		}

	}

	@Nested
	class Visibility {

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "ACTIVE", "RESERVED", "SOLD" })
		void publicListingsAreVisibleToEveryone(ListingStatus status) {
			Listing listing = aListing(status);

			assertThat(listing.isVisibleTo(null)).isTrue();
			assertThat(listing.isVisibleTo(UUID.randomUUID())).isTrue();
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "HIDDEN", "REMOVED" })
		@DisplayName("FR-ID-18: a hidden or removed listing is visible only to its owner")
		void privateListingsAreVisibleOnlyToTheOwner(ListingStatus status) {
			Listing listing = aListing(status);

			assertThat(listing.isVisibleTo(listing.getSellerId())).isTrue();
			assertThat(listing.isVisibleTo(UUID.randomUUID())).isFalse();
			assertThat(listing.isVisibleTo(null)).isFalse();
		}

		@Test
		@DisplayName("FR-LS-08: a deleted listing is visible to nobody")
		void deletedListingsAreVisibleToNobody() {
			Listing listing = aListing(ListingStatus.DELETED);

			assertThat(listing.isVisibleTo(listing.getSellerId())).isFalse();
		}

	}

	@Nested
	class Bump {

		@Test
		@DisplayName("FR-LS-10: bumping sets bumped_at = now and counts the bump")
		void bumpsAnActiveListing() {
			Listing listing = anActiveListing();

			listing.bump(BUMP_RULES, NOW.plusSeconds(1));

			assertThat(listing.getBumpedAt()).isEqualTo(NOW.plusSeconds(1));
			assertThat(listing.getBumpCount()).isEqualTo((short) 1);
		}

		@Test
		@DisplayName("FR-LS-10: a bump 1 ms before the 48 h cooldown is a 429 BUMP_TOO_SOON with nextAllowedAt")
		void refusesABumpWithinTheCooldown() {
			Listing listing = anActiveListing();
			listing.bump(BUMP_RULES, NOW);
			Instant nextAllowedAt = NOW.plus(Duration.ofHours(48));

			assertThatThrownBy(() -> listing.bump(BUMP_RULES, nextAllowedAt.minusMillis(1)))
				.isInstanceOf(RateLimitExceededException.class)
				.hasFieldOrPropertyWithValue("code", "BUMP_TOO_SOON")
				.satisfies(ex -> {
					RateLimitExceededException rateLimited = (RateLimitExceededException) ex;
					assertThat(rateLimited.getProperties()).containsEntry("nextAllowedAt", nextAllowedAt.toString());
					assertThat(rateLimited.getRetryAfter()).contains(Duration.ofMillis(1));
				});
		}

		@Test
		@DisplayName("FR-LS-10: a bump exactly 48 h after the last one is allowed")
		void allowsABumpExactlyAfterTheCooldown() {
			Listing listing = anActiveListing();
			listing.bump(BUMP_RULES, NOW);

			listing.bump(BUMP_RULES, NOW.plus(Duration.ofHours(48)));

			assertThat(listing.getBumpCount()).isEqualTo((short) 2);
		}

		@Test
		@DisplayName("FR-LS-10: the 10th bump is allowed, the 11th is a 409 BUMP_LIMIT_REACHED")
		void refusesTheEleventhBump() {
			Listing listing = anActiveListing();
			Instant bumpAt = NOW;
			for (int bump = 1; bump <= 10; bump++) {
				listing.bump(BUMP_RULES, bumpAt);
				bumpAt = bumpAt.plus(Duration.ofHours(48));
			}
			Instant eleventhBumpAt = bumpAt;

			assertThat(listing.getBumpCount()).isEqualTo((short) 10);
			assertThatThrownBy(() -> listing.bump(BUMP_RULES, eleventhBumpAt)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "BUMP_LIMIT_REACHED");
		}

		@ParameterizedTest
		@EnumSource(value = ListingStatus.class, names = { "HIDDEN", "RESERVED", "SOLD", "DELETED", "REMOVED" })
		@DisplayName("FR-LS-10: only an ACTIVE listing can be bumped")
		void refusesToBumpAListingThatIsNotActive(ListingStatus status) {
			Listing listing = aListing(status);

			assertThatThrownBy(() -> listing.bump(BUMP_RULES, NOW)).isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE");
		}

	}

}
