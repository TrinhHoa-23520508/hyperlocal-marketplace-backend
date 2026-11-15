package vn.edu.uit.nearby.nearby_backend.identity.application;

import java.util.Map;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityProperties.Otp;

/**
 * The values seeded by the identity Flyway migrations (V9, V10).
 */
public final class IdentityParametersFixture {

	public static final Map<String, String> SEEDED = Map.ofEntries(Map.entry("auth.login_max_failed_attempts", "5"),
			Map.entry("auth.login_lockout_minutes", "15"), Map.entry("auth.registrations_per_hour_per_ip", "10"),
			Map.entry("auth.account_emails_per_hour", "5"), Map.entry("auth.email_verification_ttl_hours", "24"),
			Map.entry("auth.password_reset_ttl_minutes", "30"), Map.entry("consent.location_terms_version", "1.0"),
			Map.entry("consent.terms_version", "1.0"), Map.entry("consent.privacy_version", "1.0"),
			Map.entry("otp.ttl_s", "300"), Map.entry("otp.max_attempts", "5"), Map.entry("otp.resend_s", "60"),
			Map.entry("otp.max_per_day", "5"), Map.entry("image.max_bytes", "5242880"));

	public static final Otp OTP = new Otp("test-otp-hash-secret-at-least-32-bytes!", "{code} la ma Nearby");

	public static final IdentityProperties PROPERTIES = new IdentityProperties("nearby://verify-email",
			"nearby://reset-password", OTP, null);

	private IdentityParametersFixture() {
	}

	public static IdentityParameters seeded() {
		return new IdentityParameters(key -> {
			String value = SEEDED.get(key);
			if (value == null) {
				throw new IllegalStateException("Missing app parameter: " + key);
			}
			return value;
		});
	}

}
