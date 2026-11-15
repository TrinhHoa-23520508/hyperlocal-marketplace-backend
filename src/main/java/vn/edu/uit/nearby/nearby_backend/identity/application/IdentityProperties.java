package vn.edu.uit.nearby.nearby_backend.identity.application;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.identity")
public record IdentityProperties(String emailVerificationUrl, String passwordResetUrl, Otp otp,
		BootstrapAdmin bootstrapAdmin) {

	/**
	 * @param hashSecret HMAC key for the stored code hashes, at least 32 bytes
	 * @param smsTemplate SMS text with a {@value #CODE_PLACEHOLDER} placeholder; must
	 * match the template registered with the SMS provider
	 */
	public record Otp(String hashSecret, String smsTemplate) {

		public static final String CODE_PLACEHOLDER = "{code}";

		private static final int MIN_SECRET_BYTES = 32;

		public Otp {
			if (hashSecret == null || hashSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
				throw new IllegalStateException("app.identity.otp.hash-secret must be at least 32 bytes");
			}
			if (smsTemplate == null || !smsTemplate.contains(CODE_PLACEHOLDER)) {
				throw new IllegalStateException("app.identity.otp.sms-template must contain " + CODE_PLACEHOLDER);
			}
		}

		public String smsContent(String code) {
			return smsTemplate.replace(CODE_PLACEHOLDER, code);
		}

	}

	public record BootstrapAdmin(String email, String password, String displayName) {

		public boolean isConfigured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}

	}

}
