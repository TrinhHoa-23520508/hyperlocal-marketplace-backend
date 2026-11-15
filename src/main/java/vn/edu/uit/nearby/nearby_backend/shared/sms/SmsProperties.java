package vn.edu.uit.nearby.nearby_backend.shared.sms;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.sms")
public record SmsProperties(Provider provider, Esms esms) {

	public SmsProperties {
		provider = provider == null ? Provider.LOG : provider;
	}

	public enum Provider {

		LOG, ESMS

	}

	/**
	 * @param smsType eSMS message type; {@code 2} is the customer care / OTP brandname
	 * route
	 * @param sandbox true makes eSMS validate the request without delivering or charging
	 */
	public record Esms(String baseUrl, String apiKey, String secretKey, String brandname, String smsType,
			boolean sandbox, Duration timeout) {

		public boolean isConfigured() {
			return hasText(apiKey) && hasText(secretKey) && hasText(brandname);
		}

		private static boolean hasText(String value) {
			return value != null && !value.isBlank();
		}

	}

}
