package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTests {

	private static final LoginAttempt ATTEMPT = new LoginAttempt(PhoneNumber.of("0912345678"), "203.0.113.5");

	private static final String ACCOUNT_KEY = "login-failures:+84912345678";

	private static final String IP_KEY = "login-failures-ip:203.0.113.5";

	@Mock
	RateLimiter rateLimiter;

	private LoginAttemptGuard guard() {
		return new LoginAttemptGuard(rateLimiter, IdentityParametersFixture.seeded());
	}

	@Test
	void attemptUnderBothFailureLimitsMayTryAgain() {
		when(rateLimiter.isExhausted(ACCOUNT_KEY, 5)).thenReturn(false);
		when(rateLimiter.isExhausted(IP_KEY, 20)).thenReturn(false);

		assertThatCode(() -> guard().ensureNotBlocked(ATTEMPT)).doesNotThrowAnyException();
	}

	@Test
	void identifierAtTheFailureLimitIsBlockedUntilTheWindowEnds() {
		when(rateLimiter.isExhausted(ACCOUNT_KEY, 5)).thenReturn(true);
		when(rateLimiter.remainingWindow(ACCOUNT_KEY)).thenReturn(Duration.ofMinutes(7));

		assertThatThrownBy(() -> guard().ensureNotBlocked(ATTEMPT))
			.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
				assertThat(ex.getRetryAfter()).contains(Duration.ofMinutes(7));
				assertThat(((BusinessException) ex).getCode()).isEqualTo("LOGIN_LOCKED");
			});
	}

	@Test
	void clientIpAtItsFailureLimitIsBlockedForEveryAccount() {
		when(rateLimiter.isExhausted(ACCOUNT_KEY, 5)).thenReturn(false);
		when(rateLimiter.isExhausted(IP_KEY, 20)).thenReturn(true);
		when(rateLimiter.remainingWindow(IP_KEY)).thenReturn(Duration.ofMinutes(3));

		assertThatThrownBy(() -> guard().ensureNotBlocked(ATTEMPT)).isInstanceOfSatisfying(
				RateLimitExceededException.class, ex -> assertThat(ex.getRetryAfter()).contains(Duration.ofMinutes(3)));
	}

	@Test
	void failureIsCountedForTheAccountAndTheIpInTheLockoutWindow() {
		guard().recordFailure(ATTEMPT);

		verify(rateLimiter).tryAcquire(ACCOUNT_KEY, 5, Duration.ofMinutes(15));
		verify(rateLimiter).tryAcquire(IP_KEY, 20, Duration.ofMinutes(15));
	}

	@Test
	void successfulLoginClearsOnlyTheAccountFailures() {
		guard().clearFailures(ATTEMPT);

		verify(rateLimiter).reset(ACCOUNT_KEY);
		verifyNoMoreInteractions(rateLimiter);
	}

}
