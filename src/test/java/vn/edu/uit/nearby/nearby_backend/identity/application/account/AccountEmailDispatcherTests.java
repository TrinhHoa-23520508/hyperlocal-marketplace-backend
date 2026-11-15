package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityProperties;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.AccountMailer;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

@ExtendWith(MockitoExtension.class)
class AccountEmailDispatcherTests {

	private static final IdentityProperties PROPERTIES = new IdentityProperties("nearby://verify-email",
			"https://nearby.example/reset?lang=vi", IdentityParametersFixture.OTP,
			new IdentityProperties.BootstrapAdmin(null, null, "Admin"));

	private static final EmailAddress RECIPIENT = EmailAddress.of(UserFixtures.EMAIL);

	private final User user = UserFixtures.userWithUnverifiedEmail();

	private final OneTimeTokenSubject subject = new OneTimeTokenSubject(user.getId(), UserFixtures.EMAIL);

	@Mock
	OneTimeTokenStore tokens;

	@Mock
	AccountMailer mailer;

	@Mock
	RateLimiter rateLimiter;

	private AccountEmailDispatcher dispatcher() {
		return new AccountEmailDispatcher(tokens, mailer, rateLimiter, IdentityParametersFixture.seeded(), PROPERTIES);
	}

	@Test
	void verificationEmailCarriesASingleUseTokenValidFor24Hours() {
		givenWithinQuota();
		when(tokens.issue(OneTimeTokenPurpose.EMAIL_VERIFICATION, subject, Duration.ofHours(24))).thenReturn("tok123");

		dispatcher().sendEmailVerification(user, RECIPIENT);

		verify(mailer).sendEmailVerification(RECIPIENT, UserFixtures.DISPLAY_NAME,
				"nearby://verify-email?token=tok123");
	}

	@Test
	void resetEmailKeepsTheQueryOfTheConfiguredUrl() {
		givenWithinQuota();
		when(tokens.issue(OneTimeTokenPurpose.PASSWORD_RESET, subject, Duration.ofMinutes(30))).thenReturn("tok456");

		dispatcher().sendPasswordReset(user, RECIPIENT);

		verify(mailer).sendPasswordReset(RECIPIENT, UserFixtures.DISPLAY_NAME,
				"https://nearby.example/reset?lang=vi&token=tok456");
	}

	@Test
	void emailsOverTheHourlyQuotaAreSkippedSilently() {
		when(rateLimiter.tryAcquire(anyString(), anyInt(), eq(Duration.ofHours(1)))).thenReturn(false);

		dispatcher().sendEmailVerification(user, RECIPIENT);
		dispatcher().sendPasswordReset(user, RECIPIENT);

		verifyNoInteractions(tokens, mailer);
	}

	private void givenWithinQuota() {
		when(rateLimiter.tryAcquire("account-emails:" + UserFixtures.EMAIL, 5, Duration.ofHours(1))).thenReturn(true);
	}

}
