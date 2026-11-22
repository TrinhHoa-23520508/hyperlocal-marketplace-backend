package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AreaVerificationAttempt;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

/**
 * The two limits of FR-AR-06 (BR-06): verification or renewal attempts per day, counted
 * whatever the result, and areas added or replaced per 30 days. Both answer
 * {@code 429 AREA_RATE_LIMITED} with a {@code reason} and {@code Retry-After}.
 */
@Component
class AttemptLimits {

	static final String DAILY_ATTEMPTS = "DAILY_ATTEMPTS";

	static final String AREA_CHANGES = "AREA_CHANGES";

	private static final Duration ONE_DAY = Duration.ofDays(1);

	private final RateLimiter rateLimiter;

	private final AreaVerificationAttemptRepository attempts;

	private final AreaParameters parameters;

	AttemptLimits(RateLimiter rateLimiter, AreaVerificationAttemptRepository attempts, AreaParameters parameters) {
		this.rateLimiter = rateLimiter;
		this.attempts = attempts;
		this.parameters = parameters;
	}

	/**
	 * Counts one attempt; the one past {@code area.max_attempts_per_day} is refused
	 * whatever the data (FR-AR-06 acceptance criterion).
	 */
	void consumeDailyAttempt(UUID userId) {
		String key = RedisKeyPrefixes.AREA_DAILY_ATTEMPTS + userId;
		if (!rateLimiter.tryAcquire(key, parameters.maxAttemptsPerDay(), ONE_DAY)) {
			throw limited(DAILY_ATTEMPTS, rateLimiter.remainingWindow(key));
		}
	}

	/**
	 * Refuses a new or replacing area once {@code area.max_changes_per_30d} accepted
	 * changes happened in the window; the retry time is when the oldest one leaves it.
	 */
	void requireAreaChangeAllowed(UUID userId, Instant now) {
		Duration window = parameters.areaChangesWindow();
		List<AreaVerificationAttempt> changes = attempts.findAreaChangesSince(userId, now.minus(window));
		int maxChanges = parameters.maxAreaChanges();
		if (changes.size() < maxChanges) {
			return;
		}
		Instant freedAt = changes.get(changes.size() - maxChanges).getCreatedAt().plus(window);
		throw limited(AREA_CHANGES, Duration.between(now, freedAt));
	}

	private static RateLimitExceededException limited(String reason, Duration retryAfter) {
		return new RateLimitExceededException(ErrorCodes.AREA_RATE_LIMITED,
				"Too many area verifications, please try again later.", retryAfter, Map.of("reason", reason));
	}

}
