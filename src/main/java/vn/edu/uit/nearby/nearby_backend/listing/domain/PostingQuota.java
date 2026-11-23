package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * FR-LS-04 (BR-07): at most {@code listing.new_account_daily_limit} new listings a day
 * until the seller's first completed transaction, and at most
 * {@code listing.max_open_per_user} open listings (ACTIVE + RESERVED + PENDING_REVIEW).
 */
public record PostingQuota(int dailyLimit, int maxOpenListings) {

	public PostingAllowance allowanceFor(PostingHistory history) {
		Integer remainingToday = history.hasCompletedTransaction() ? null
				: Math.max(0, dailyLimit - history.listingsPostedInLastDay());
		int remainingOpen = Math.max(0, maxOpenListings - history.openListings());
		return new PostingAllowance(remainingToday, dailyLimit, remainingOpen, maxOpenListings);
	}

}
