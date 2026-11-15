package vn.edu.uit.nearby.nearby_backend.shared.sms;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends through eSMS.vn ({@code SendMultipleMessage_V4_post_json}). Message content and
 * the full phone number are never logged because the content carries one-time codes.
 */
@Slf4j
public class EsmsSmsSender implements SmsSender {

	static final String SEND_PATH = "/SendMultipleMessage_V4_post_json/";

	static final String SUCCESS_CODE = "100";

	private static final String VIETNAM_COUNTRY_PREFIX = "+84";

	private static final String NATIONAL_TRUNK_PREFIX = "0";

	private static final String UNICODE_DISABLED = "0";

	private static final String SANDBOX_ENABLED = "1";

	private static final String SANDBOX_DISABLED = "0";

	private static final int VISIBLE_PHONE_DIGITS = 3;

	private final RestClient restClient;

	private final SmsProperties.Esms settings;

	public EsmsSmsSender(RestClient restClient, SmsProperties.Esms settings) {
		this.restClient = restClient;
		this.settings = settings;
	}

	@Override
	public void send(String phoneE164, String content) {
		EsmsResponse response = post(new EsmsRequest(settings.apiKey(), settings.secretKey(),
				nationalFormatOf(phoneE164), content, settings.brandname(), settings.smsType(), UNICODE_DISABLED,
				settings.sandbox() ? SANDBOX_ENABLED : SANDBOX_DISABLED, UUID.randomUUID().toString()));
		if (response == null || !SUCCESS_CODE.equals(response.codeResult())) {
			String code = response == null ? "empty response" : response.codeResult();
			String error = response == null ? "" : response.errorMessage();
			log.error("eSMS rejected the SMS to {}: code {} {}", masked(phoneE164), code, error);
			throw new SmsDeliveryException("eSMS rejected the message with code " + code);
		}
		log.info("SMS to {} accepted by eSMS (SMSID {}, sandbox {})", masked(phoneE164), response.smsId(),
				settings.sandbox());
	}

	private @Nullable EsmsResponse post(EsmsRequest request) {
		try {
			return restClient.post()
				.uri(SEND_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(EsmsResponse.class);
		}
		catch (RestClientException ex) {
			log.error("eSMS could not be reached: {}", ex.getMessage());
			throw new SmsDeliveryException("eSMS could not be reached", ex);
		}
	}

	private static String nationalFormatOf(String phoneE164) {
		return phoneE164.startsWith(VIETNAM_COUNTRY_PREFIX)
				? NATIONAL_TRUNK_PREFIX + phoneE164.substring(VIETNAM_COUNTRY_PREFIX.length()) : phoneE164;
	}

	static String masked(String phoneE164) {
		int visibleFrom = Math.max(0, phoneE164.length() - VISIBLE_PHONE_DIGITS);
		return "*".repeat(visibleFrom) + phoneE164.substring(visibleFrom);
	}

	record EsmsRequest(@JsonProperty("ApiKey") String apiKey, @JsonProperty("SecretKey") String secretKey,
			@JsonProperty("Phone") String phone, @JsonProperty("Content") String content,
			@JsonProperty("Brandname") String brandname, @JsonProperty("SmsType") String smsType,
			@JsonProperty("IsUnicode") String isUnicode, @JsonProperty("Sandbox") String sandbox,
			@JsonProperty("RequestId") String requestId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EsmsResponse(@JsonProperty("CodeResult") String codeResult,
			@JsonProperty("ErrorMessage") @Nullable String errorMessage,
			@JsonProperty("SMSID") @Nullable String smsId) {
	}

}
