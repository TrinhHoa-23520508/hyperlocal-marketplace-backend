package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.listing.OpenTransactionCheck;
import vn.edu.uit.nearby.nearby_backend.listing.SellerTradeHistory;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

/**
 * The SPIs the transaction module will implement (register LS-W1, LS-W2): their absence
 * and their presence.
 */
class ListingSpiTests {

	private static final Instant NOW = ListingFixtures.NOW;

	private final ListingRepository listings = mock(ListingRepository.class);

	private final ListingParameters parameters = new ListingParameters(ListingParametersTests.SEEDED::get);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	@DisplayName("FR-LS-08: without the transaction module a listing has no open transaction")
	void deletesWhenNoTransactionModuleAnswers() {
		Listing listing = ownedListing();

		lifecycle(List.of()).delete(listing.getSellerId(), listing.getId());

		assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
	}

	@Test
	@DisplayName("FR-LS-08: an OFFERED or ACCEPTED transaction is a 409 naming the transaction")
	void refusesToDeleteWithAnOpenTransaction() {
		Listing listing = ownedListing();
		UUID transactionId = UUID.randomUUID();
		OpenTransactionCheck transactions = listingId -> Optional.of(transactionId);

		assertThatThrownBy(() -> lifecycle(List.of(transactions)).delete(listing.getSellerId(), listing.getId()))
			.isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "LISTING_HAS_OPEN_TRANSACTIONS")
			.extracting(ex -> ((ConflictException) ex).getProperties().get("transactionId"))
			.isEqualTo(transactionId.toString());
		assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
	}

	@Test
	@DisplayName("BR-07: without the transaction module the daily limit always applies")
	void appliesTheDailyLimitWithoutTradeHistory() {
		UUID sellerId = UUID.randomUUID();
		postedToday(sellerId, 3);

		assertThat(quota(List.of()).allowanceFor(sellerId, NOW).canPost()).isFalse();
	}

	@Test
	@DisplayName("FR-LS-04: a seller with a completed transaction has no daily limit")
	void liftsTheDailyLimitAfterACompletedTransaction() {
		UUID sellerId = UUID.randomUUID();
		postedToday(sellerId, 3);
		SellerTradeHistory history = id -> true;

		assertThat(quota(List.of(history)).allowanceFor(sellerId, NOW).canPost()).isTrue();
	}

	@Test
	@DisplayName("FR-LS-04: the retry time is when the oldest of the day's listings leaves the 24 h window")
	void retriesWhenTheOldestListingLeavesTheWindow() {
		UUID sellerId = UUID.randomUUID();
		given(listings.findCreationTimesSince(any(), any())).willReturn(List.of(NOW.minus(Duration.ofHours(20)),
				NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1))));

		assertThatThrownBy(() -> quota(List.of()).requireCanPost(sellerId, NOW))
			.isInstanceOf(RateLimitExceededException.class)
			.satisfies(
					ex -> assertThat(((RateLimitExceededException) ex).getRetryAfter()).contains(Duration.ofHours(4)));
	}

	private Listing ownedListing() {
		Listing listing = ListingFixtures.anActiveListing();
		given(listings.getOwned(listing.getId(), listing.getSellerId())).willReturn(listing);
		return listing;
	}

	private void postedToday(UUID sellerId, int count) {
		given(listings.findCreationTimesSince(any(), any()))
			.willReturn(Collections.nCopies(count, NOW.minus(Duration.ofHours(1))));
		given(listings.countBySellerIdAndStatusIn(any(), any())).willReturn((long) count);
	}

	private ListingLifecycleService lifecycle(List<OpenTransactionCheck> checks) {
		ListingEvents events = new ListingEvents(event -> {
		});
		return new ListingLifecycleService(listings, mock(ListingImageRepository.class), checks, parameters, events,
				mock(OwnerListingViews.class), clock);
	}

	private PostingQuotaCheck quota(List<SellerTradeHistory> histories) {
		return new PostingQuotaCheck(listings, histories, parameters);
	}

}
