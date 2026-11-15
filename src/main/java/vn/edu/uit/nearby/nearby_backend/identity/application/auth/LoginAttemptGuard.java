package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountIdentifier;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

/**
 * Blocks login for an account identifier after too many failed attempts within the
 * lockout window (FR-ID-03: 5 failures in 15 minutes). While blocked the password is not
 * checked at all.
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
	public void ensureNotBlocked(AccountIdentifier identifier) {
		String key = keyOf(identifier);
		if (rateLimiter.isExhausted(key, parameters.loginMaxFailedAttempts())) {
			throw new RateLimitExceededException(ErrorCodes.LOGIN_LOCKED,
					"Too many failed logins, please try again later.", rateLimiter.remainingWindow(key));
		}
	}

	public void recordFailure(AccountIdentifier identifier) {
		rateLimiter.tryAcquire(keyOf(identifier), parameters.loginMaxFailedAttempts(), parameters.loginLockoutWindow());
	}

	public void clearFailures(AccountIdentifier identifier) {
		rateLimiter.reset(keyOf(identifier));
	}

	private static String keyOf(AccountIdentifier identifier) {
		return RedisKeyPrefixes.LOGIN_FAILURES + identifier.value();
	}

}
