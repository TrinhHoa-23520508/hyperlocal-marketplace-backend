package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Registers the FCM token of the app installation after login and whenever FCM rotates it
 * (FR-NT-02; closes register row ID-W1 for FR-ID-03). One device has one token; logout
 * already removes it through {@code DeviceSignedOut}.
 */
@Service
public class DeviceService {

	static final int MAX_DEVICE_ID_CHARS = 64;

	static final int MAX_TOKEN_CHARS = 255;

	static final int MAX_APP_VERSION_CHARS = 20;

	private static final Set<String> PLATFORMS = Set.of("ANDROID", "WEB");

	private final DeviceTokenRepository devices;

	private final Clock clock;

	DeviceService(DeviceTokenRepository devices, Clock clock) {
		this.devices = devices;
		this.clock = clock;
	}

	/**
	 * What the app sends for one installation.
	 */
	public record Registration(@Nullable String deviceId, @Nullable String fcmToken, @Nullable String platform,
			@Nullable String appVersion) {
	}

	/**
	 * Saves the device's token; calling again with the same token only refreshes it.
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for a blank or too long
	 * id or token, an unknown platform or a too long app version
	 */
	@Transactional
	public void register(UUID userId, Registration registration) {
		String id = required("deviceId", registration.deviceId(), MAX_DEVICE_ID_CHARS);
		String token = required("fcmToken", registration.fcmToken(), MAX_TOKEN_CHARS);
		String platform = registration.platform();
		String appVersion = registration.appVersion();
		String normalizedPlatform = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
		if (!PLATFORMS.contains(normalizedPlatform)) {
			throw InvalidRequestException.invalidField("platform", "The platform is ANDROID or WEB.");
		}
		if (appVersion != null && appVersion.length() > MAX_APP_VERSION_CHARS) {
			throw InvalidRequestException.invalidField("appVersion",
					"The app version has at most " + MAX_APP_VERSION_CHARS + " characters.");
		}
		devices.upsert(userId, id, token, normalizedPlatform, appVersion, clock.instant());
	}

	/**
	 * Forgets the device's token; nothing happens when it has none, so the app can call
	 * it without checking.
	 */
	@Transactional
	public void unregister(UUID userId, String deviceId) {
		devices.deleteDevice(userId, deviceId);
	}

	private static String required(String field, @Nullable String value, int maxChars) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty() || trimmed.length() > maxChars) {
			throw InvalidRequestException.invalidField(field,
					"The " + field + " needs 1 to " + maxChars + " characters.");
		}
		return trimmed;
	}

}
