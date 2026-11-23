package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM HTTP v1 settings (plan DEC-CH-6). With no {@code credentials} every push is logged
 * and marked SKIPPED, so development and CI need no Firebase project.
 *
 * @param credentials path of the service account JSON file; empty = push disabled
 * @param baseUrl the FCM host; changed only by tests
 * @param timeout connect and read timeout of one send (FR-NT-02: FCM must not slow a
 * flow)
 */
@ConfigurationProperties("app.push.fcm")
public record PushProperties(String credentials, String baseUrl, Duration timeout) {

	public boolean isEnabled() {
		return credentials != null && !credentials.isBlank();
	}

}
