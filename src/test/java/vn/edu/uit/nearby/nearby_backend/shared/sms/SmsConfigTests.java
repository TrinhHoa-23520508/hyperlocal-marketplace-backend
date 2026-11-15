package vn.edu.uit.nearby.nearby_backend.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SmsConfigTests {

	private final SmsConfig config = new SmsConfig();

	@Test
	void logProviderNeverSendsAndNeverFails() {
		SmsSender sender = config.smsSender(new SmsProperties(SmsProperties.Provider.LOG, null));

		assertThat(sender).isInstanceOf(LoggingSmsSender.class);
		assertThatCode(() -> sender.send("+84912345678", "content")).doesNotThrowAnyException();
	}

	@Test
	void missingProviderDefaultsToLog() {
		assertThat(new SmsProperties(null, null).provider()).isEqualTo(SmsProperties.Provider.LOG);
	}

	@Test
	void esmsProviderIsUsedWhenCredentialsAreSet() {
		SmsProperties properties = new SmsProperties(SmsProperties.Provider.ESMS, esms("key", "secret", "NEARBY"));

		assertThat(config.smsSender(properties)).isInstanceOf(EsmsSmsSender.class);
	}

	@Test
	void esmsProviderWithoutCredentialsFailsAtStartup() {
		SmsProperties properties = new SmsProperties(SmsProperties.Provider.ESMS, esms("", "secret", "NEARBY"));

		assertThatThrownBy(() -> config.smsSender(properties)).isInstanceOf(IllegalStateException.class);
	}

	private static SmsProperties.Esms esms(String apiKey, String secretKey, String brandname) {
		return new SmsProperties.Esms("https://rest.esms.vn/MainService.svc/json", apiKey, secretKey, brandname, "2",
				true, Duration.ofSeconds(10));
	}

}
