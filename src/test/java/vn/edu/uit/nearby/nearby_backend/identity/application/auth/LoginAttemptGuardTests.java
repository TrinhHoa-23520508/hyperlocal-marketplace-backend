package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

	private static final PhoneNumber PHONE = PhoneNumber.of("0912345678");

	private static final String KEY = "login-failures:+84912345678";

	@Mock
	RateLimiter rateLimiter;

	private LoginAttemptGuard guard() {
		return new LoginAttemptGuard(rateLimiter, IdentityParametersFixture.seeded());
	}

	@Test
	void identifierUnderTheFailureLimitMayTryAgain() {
		when(rateLimiter.isExhausted(KEY, 5)).thenReturn(false);

		assertThatCode(() -> guard().ensureNotBlocked(PHONE)).doesNotThrowAnyException();
	}

	@Test
	void identifierAtTheFailureLimitIsBlockedUntilTheWindowEnds() {
		when(rateLimiter.isExhausted(KEY, 5)).thenReturn(true);
		when(rateLimiter.remainingWindow(KEY)).thenReturn(Duration.ofMinutes(7));

		assertThatThrownBy(() -> guard().ensureNotBlocked(PHONE))
			.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
				assertThat(ex.getRetryAfter()).contains(Duration.ofMinutes(7));
				assertThat(((BusinessException) ex).getCode()).isEqualTo("LOGIN_LOCKED");
			});
	}

	@Test
	void failureIsCountedInTheLockoutWindow() {
		guard().recordFailure(PHONE);

		verify(rateLimiter).tryAcquire(KEY, 5, Duration.ofMinutes(15));
	}

	@Test
	void successfulLoginClearsTheFailures() {
		guard().clearFailures(PHONE);

		verify(rateLimiter).reset(KEY);
	}

}
