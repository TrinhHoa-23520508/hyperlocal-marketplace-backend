package vn.edu.uit.nearby.nearby_backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPolicy;

class IdentityParametersTests {

	private final IdentityParameters parameters = IdentityParametersFixture.seeded();

	@Test
	void loginLockoutAllowsFiveFailuresInFifteenMinutes() {
		assertThat(parameters.loginMaxFailedAttempts()).isEqualTo(5);
		assertThat(parameters.loginLockoutWindow()).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void emailTokenLifetimesAreConvertedToDurations() {
		assertThat(parameters.emailVerificationTtl()).isEqualTo(Duration.ofHours(24));
		assertThat(parameters.passwordResetTtl()).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void quotasAndLimitsAreReadAsNumbers() {
		assertThat(parameters.registrationsPerHourPerIp()).isEqualTo(10);
		assertThat(parameters.accountEmailsPerHour()).isEqualTo(5);
		assertThat(parameters.imageMaxBytes()).isEqualTo(5L * 1024 * 1024);
	}

	@Test
	void policyVersionsAreReadAsText() {
		assertThat(parameters.locationTermsVersion()).isEqualTo("1.0");
		assertThat(parameters.termsVersion()).isEqualTo("1.0");
		assertThat(parameters.privacyVersion()).isEqualTo("1.0");
	}

	@Test
	void otpPolicyFollowsTheSeededLimits() {
		OtpPolicy policy = parameters.otpPolicy();

		assertThat(policy.ttl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(policy.maxAttempts()).isEqualTo(5);
		assertThat(policy.resendInterval()).isEqualTo(Duration.ofSeconds(60));
		assertThat(policy.maxPerDay()).isEqualTo(5);
	}

	@Test
	void otpSettingsRequireALongSecretAndACodePlaceholder() {
		assertThatThrownBy(() -> new IdentityProperties.Otp("short", "{code}"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new IdentityProperties.Otp(IdentityParametersFixture.OTP.hashSecret(), "no code"))
			.isInstanceOf(IllegalStateException.class);
		assertThat(IdentityParametersFixture.OTP.smsContent("123456")).isEqualTo("123456 la ma Nearby");
	}

	@Test
	void bootstrapAdminNeedsBothEmailAndPassword() {
		assertThat(new IdentityProperties.BootstrapAdmin("a@b.vn", "Secret123", "Admin").isConfigured()).isTrue();
		assertThat(new IdentityProperties.BootstrapAdmin("a@b.vn", "", "Admin").isConfigured()).isFalse();
		assertThat(new IdentityProperties.BootstrapAdmin(" ", "Secret123", "Admin").isConfigured()).isFalse();
		assertThat(new IdentityProperties.BootstrapAdmin(null, null, "Admin").isConfigured()).isFalse();
	}

}
