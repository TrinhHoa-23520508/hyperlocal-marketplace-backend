package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

/**
 * How many more listings a seller may post now (UC-LS-01 step 1 shows it; step 7 enforces
 * it).
 *
 * @param remainingToday null once the seller has a completed transaction (no daily limit)
 */
public record PostingAllowance(@Nullable Integer remainingToday, int dailyLimit, int remainingOpen,
		int maxOpenListings) {

	public OptionalInt remainingTodayIfLimited() {
		return remainingToday == null ? OptionalInt.empty() : OptionalInt.of(remainingToday);
	}

	public boolean canPost() {
		return (remainingToday == null || remainingToday > 0) && remainingOpen > 0;
	}

	/**
	 * @param dailyWindowResetsIn time until the oldest listing of the window leaves it
	 * @throws RateLimitExceededException {@code 429 LISTING_DAILY_LIMIT} with the
	 * remaining quota
	 * @throws ConflictException {@code 409 LISTING_OPEN_LIMIT} (D3)
	 */
	public void requireCanPost(Duration dailyWindowResetsIn) {
		if (remainingToday != null && remainingToday == 0) {
			throw new RateLimitExceededException(ErrorCodes.LISTING_DAILY_LIMIT,
					"New accounts can post " + dailyLimit + " listings a day until their first completed transaction.",
					dailyWindowResetsIn, Map.of("remaining", 0, "dailyLimit", dailyLimit));
		}
		if (remainingOpen == 0) {
			throw new ConflictException(ErrorCodes.LISTING_OPEN_LIMIT,
					"You can have at most " + maxOpenListings + " open listings.",
					Map.of("maxOpenListings", maxOpenListings));
		}
	}

}
