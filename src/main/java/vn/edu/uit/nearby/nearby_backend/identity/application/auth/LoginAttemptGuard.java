package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import java.util.List;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

/**
 * Blocks login after too many failed attempts within the lockout window, counted per
 * account identifier (5 in 15 minutes) and per client IP (FR-ID-03). While blocked the
 * password is not checked at all.
 */
@Component
public class LoginAttemptGuard {

	private final RateLimiter rateLimiter;

	private final IdentityParameters parameters;

	public LoginAttemptGuard(RateLimiter rateLimiter, IdentityParameters parameters) {
		this.rateLimiter = rateLimiter;
		this.parameters = parameters;
	}

	/**
	 * @throws RateLimitExceededException {@code LOGIN_LOCKED} with the time left in the
	 * window
	 */
	public void ensureNotBlocked(LoginAttempt attempt) {
		for (FailureCounter counter : countersOf(attempt)) {
			if (rateLimiter.isExhausted(counter.key(), counter.limit())) {
				throw new RateLimitExceededException(ErrorCodes.LOGIN_LOCKED,
						"Too many failed logins, please try again later.", rateLimiter.remainingWindow(counter.key()));
			}
		}
	}

	public void recordFailure(LoginAttempt attempt) {
		countersOf(attempt).forEach(
				counter -> rateLimiter.tryAcquire(counter.key(), counter.limit(), parameters.loginLockoutWindow()));
	}

	/**
	 * Clears only the account counter: other accounts behind the same IP keep counting.
	 */
	public void clearFailures(LoginAttempt attempt) {
		rateLimiter.reset(identifierCounter(attempt).key());
	}

	private List<FailureCounter> countersOf(LoginAttempt attempt) {
		return List.of(identifierCounter(attempt), new FailureCounter(
				RedisKeyPrefixes.LOGIN_FAILURES_BY_IP + attempt.clientIp(), parameters.loginMaxFailedAttemptsPerIp()));
	}

	private FailureCounter identifierCounter(LoginAttempt attempt) {
		return new FailureCounter(RedisKeyPrefixes.LOGIN_FAILURES + attempt.identifier().value(),
				parameters.loginMaxFailedAttempts());
	}

	private record FailureCounter(String key, int limit) {
	}

}
