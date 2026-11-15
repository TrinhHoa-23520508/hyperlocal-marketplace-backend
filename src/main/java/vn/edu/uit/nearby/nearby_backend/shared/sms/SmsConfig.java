package vn.edu.uit.nearby.nearby_backend.shared.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class SmsConfig {

	@Bean
	SmsSender smsSender(SmsProperties properties) {
		if (properties.provider() == SmsProperties.Provider.LOG) {
			log.warn("SMS is disabled (app.sms.provider=log): one-time codes are written to the log");
			return new LoggingSmsSender();
		}
		SmsProperties.Esms esms = properties.esms();
		if (esms == null || !esms.isConfigured()) {
			throw new IllegalStateException(
					"app.sms.provider=esms requires ESMS_API_KEY, ESMS_SECRET_KEY and ESMS_BRANDNAME");
		}
		return new EsmsSmsSender(esmsRestClient(esms), esms);
	}

	private static RestClient esmsRestClient(SmsProperties.Esms esms) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(esms.timeout());
		requestFactory.setReadTimeout(esms.timeout());
		return RestClient.builder().baseUrl(esms.baseUrl()).requestFactory(requestFactory).build();
	}

}
