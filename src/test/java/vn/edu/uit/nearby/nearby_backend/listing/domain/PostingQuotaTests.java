package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

/**
 * FR-LS-04 / BR-07 with {@code listing.new_account_daily_limit} = 3 and
 * {@code listing.max_open_per_user} = 30.
 */
class PostingQuotaTests {

	private static final PostingQuota QUOTA = new PostingQuota(3, 30);

	private static final Duration RESETS_IN = Duration.ofHours(5);

	@Test
	@DisplayName("FR-LS-04: the 3rd listing of the day is allowed")
	void allowsTheThirdListingOfTheDay() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(2, 2, false));

		assertThat(allowance.canPost()).isTrue();
		assertThat(allowance.remainingTodayIfLimited()).hasValue(1);
		assertThatCode(() -> allowance.requireCanPost(RESETS_IN)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("BR-07: the 4th listing in a day before a completed transaction is a 429 LISTING_DAILY_LIMIT")
	void refusesTheFourthListingOfTheDay() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(3, 3, false));

		assertThat(allowance.canPost()).isFalse();
		assertThatThrownBy(() -> allowance.requireCanPost(RESETS_IN)).isInstanceOf(RateLimitExceededException.class)
			.hasFieldOrPropertyWithValue("code", "LISTING_DAILY_LIMIT")
			.satisfies(ex -> {
				RateLimitExceededException rateLimited = (RateLimitExceededException) ex;
				assertThat(rateLimited.getProperties()).containsEntry("remaining", 0).containsEntry("dailyLimit", 3);
				assertThat(rateLimited.getRetryAfter()).contains(RESETS_IN);
			});
	}

	@Test
	@DisplayName("FR-LS-04: after the first completed transaction there is no daily limit")
	void liftsTheDailyLimitAfterTheFirstCompletedTransaction() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(7, 7, true));

		assertThat(allowance.remainingTodayIfLimited()).isEmpty();
		assertThat(allowance.canPost()).isTrue();
	}

	@Test
	@DisplayName("FR-LS-04: the 30th open listing is allowed")
	void allowsTheThirtiethOpenListing() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(0, 29, true));

		assertThat(allowance.remainingOpen()).isOne();
		assertThatCode(() -> allowance.requireCanPost(RESETS_IN)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-LS-04: the 31st open listing is a 409 LISTING_OPEN_LIMIT")
	void refusesTheThirtyFirstOpenListing() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(0, 30, true));

		assertThatThrownBy(() -> allowance.requireCanPost(RESETS_IN)).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "LISTING_OPEN_LIMIT");
	}

	@Test
	void neverReportsANegativeRemainder() {
		PostingAllowance allowance = QUOTA.allowanceFor(new PostingHistory(5, 40, false));

		assertThat(allowance.remainingToday()).isZero();
		assertThat(allowance.remainingOpen()).isZero();
	}

}
