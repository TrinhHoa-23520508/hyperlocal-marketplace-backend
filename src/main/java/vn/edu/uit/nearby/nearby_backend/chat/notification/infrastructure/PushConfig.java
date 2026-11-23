package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway;

/**
 * Chooses the push gateway: FCM when a service account file is configured, otherwise the
 * logging gateway (plan DEC-CH-6).
 */
@Configuration(proxyBeanMethods = false)
class PushConfig {

	private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

	@Bean
	PushGateway pushGateway(PushProperties properties) {
		if (!properties.isEnabled()) {
			return new LoggingPushGateway();
		}
		ServiceAccountCredentials credentials = load(properties.credentials());
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.timeout());
		requestFactory.setReadTimeout(properties.timeout());
		RestClient client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
		GoogleCredentials scoped = credentials.createScoped(List.of(FCM_SCOPE));
		return new FcmPushGateway(client, credentials.getProjectId(), () -> accessTokenOf(scoped));
	}

	private static ServiceAccountCredentials load(String path) {
		try (InputStream file = new FileInputStream(path)) {
			return ServiceAccountCredentials.fromStream(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the FCM service account file " + path, ex);
		}
	}

	private static String accessTokenOf(GoogleCredentials credentials) {
		try {
			credentials.refreshIfExpired();
			return credentials.getAccessToken().getTokenValue();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot get an FCM access token", ex);
		}
	}

}
