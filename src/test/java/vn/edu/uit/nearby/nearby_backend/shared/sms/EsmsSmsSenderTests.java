package vn.edu.uit.nearby.nearby_backend.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EsmsSmsSenderTests {

	private static final String BASE_URL = "https://rest.esms.test/MainService.svc/json";

	private static final String PHONE = "+84912345678";

	private MockRestServiceServer server;

	private RestClient.Builder builder;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
	}

	@Test
	void sendsTheMessageInNationalFormatWithTheConfiguredBrandname() {
		server.expect(requestTo(BASE_URL + EsmsSmsSender.SEND_PATH))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.Phone").value("0912345678"))
			.andExpect(jsonPath("$.Content").value("123456 la ma xac minh"))
			.andExpect(jsonPath("$.Brandname").value("NEARBY"))
			.andExpect(jsonPath("$.ApiKey").value("key"))
			.andExpect(jsonPath("$.SecretKey").value("secret"))
			.andExpect(jsonPath("$.SmsType").value("2"))
			.andExpect(jsonPath("$.Sandbox").value("0"))
			.andExpect(jsonPath("$.RequestId").isNotEmpty())
			.andRespond(withSuccess("""
					{"CodeResult":"100","CountRegenerate":0,"SMSID":"abc-1"}""", MediaType.APPLICATION_JSON));

		assertThatCode(() -> sender(false).send(PHONE, "123456 la ma xac minh")).doesNotThrowAnyException();
		server.verify();
	}

	@Test
	void sandboxFlagIsForwarded() {
		server.expect(requestTo(BASE_URL + EsmsSmsSender.SEND_PATH))
			.andExpect(jsonPath("$.Sandbox").value("1"))
			.andRespond(withSuccess("""
					{"CodeResult":"100","SMSID":"abc-2"}""", MediaType.APPLICATION_JSON));

		sender(true).send(PHONE, "content");

		server.verify();
	}

	@Test
	void rejectedRequestRaisesDeliveryFailure() {
		server.expect(requestTo(BASE_URL + EsmsSmsSender.SEND_PATH)).andRespond(withSuccess("""
				{"CodeResult":"146","ErrorMessage":"Sai template Brandname CSKH"}""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> sender(false).send(PHONE, "content")).isInstanceOf(SmsDeliveryException.class)
			.hasMessageContaining("146");
	}

	@Test
	void unreachableGatewayRaisesDeliveryFailure() {
		server.expect(requestTo(BASE_URL + EsmsSmsSender.SEND_PATH)).andRespond(withServerError());

		assertThatThrownBy(() -> sender(false).send(PHONE, "content")).isInstanceOf(SmsDeliveryException.class);
	}

	@Test
	void maskedPhoneKeepsOnlyTheLastThreeDigits() {
		assertThat(EsmsSmsSender.masked(PHONE)).isEqualTo("*********678");
	}

	private EsmsSmsSender sender(boolean sandbox) {
		SmsProperties.Esms settings = new SmsProperties.Esms(BASE_URL, "key", "secret", "NEARBY", "2", sandbox,
				Duration.ofSeconds(5));
		return new EsmsSmsSender(builder.build(), settings);
	}

}
