package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.listing.SellerTradeHistory;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingAllowance;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingHistory;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingQuota;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;

/**
 * FR-LS-04 (BR-07) from the seller's stored listings: the daily limit counts listings
 * created in the last 24 hours, whatever happened to them since (DEC-LS-7).
 */
@Component
class PostingQuotaCheck {

	private final ListingRepository listings;

	private final List<SellerTradeHistory> tradeHistories;

	private final ListingParameters parameters;

	PostingQuotaCheck(ListingRepository listings, List<SellerTradeHistory> tradeHistories,
			ListingParameters parameters) {
		this.listings = listings;
		this.tradeHistories = tradeHistories;
		this.parameters = parameters;
	}

	PostingAllowance allowanceFor(UUID sellerId, Instant now) {
		return parameters.postingQuota().allowanceFor(historyOf(sellerId, recentPostings(sellerId, now)));
	}

	/**
	 * UC-LS-01 step 7. The caller holds the seller's posting lock.
	 */
	void requireCanPost(UUID sellerId, Instant now) {
		List<Instant> recentPostings = recentPostings(sellerId, now);
		PostingQuota quota = parameters.postingQuota();
		quota.allowanceFor(historyOf(sellerId, recentPostings))
			.requireCanPost(timeUntilAPostingLeavesTheWindow(recentPostings, quota.dailyLimit(), now));
	}

	private List<Instant> recentPostings(UUID sellerId, Instant now) {
		return listings.findCreationTimesSince(sellerId, now.minus(parameters.dailyPostingWindow()));
	}

	private PostingHistory historyOf(UUID sellerId, List<Instant> recentPostings) {
		long openListings = listings.countBySellerIdAndStatusIn(sellerId, ListingStatus.openStatuses());
		return new PostingHistory(recentPostings.size(), Math.toIntExact(openListings),
				hasCompletedTransaction(sellerId));
	}

	private boolean hasCompletedTransaction(UUID sellerId) {
		return tradeHistories.stream().anyMatch(history -> history.hasCompletedTransaction(sellerId));
	}

	private Duration timeUntilAPostingLeavesTheWindow(List<Instant> recentPostings, int dailyLimit, Instant now) {
		if (recentPostings.size() < dailyLimit || dailyLimit <= 0) {
			return Duration.ZERO;
		}
		Instant freedAt = recentPostings.get(recentPostings.size() - dailyLimit).plus(parameters.dailyPostingWindow());
		return Duration.between(now, freedAt);
	}

}
