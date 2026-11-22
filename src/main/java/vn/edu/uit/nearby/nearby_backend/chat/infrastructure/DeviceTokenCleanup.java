package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionRequested;
import vn.edu.uit.nearby.nearby_backend.identity.DeviceSignedOut;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;

/**
 * Stops pushes to devices whose user logged out, asked for deletion or was sanctioned
 * (FR-ID-05, FR-ID-16, FR-ID-19).
 */
@Component
public class DeviceTokenCleanup {

	private final JdbcClient jdbc;

	public DeviceTokenCleanup(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@ApplicationModuleListener
	void onDeviceSignedOut(DeviceSignedOut event) {
		jdbc.sql("DELETE FROM device_tokens WHERE user_id = :userId AND fcm_token = :fcmToken")
			.param("userId", event.userId())
			.param("fcmToken", event.fcmToken())
			.update();
	}

	@ApplicationModuleListener
	void onAccountDeletionRequested(AccountDeletionRequested event) {
		deleteAllOf(event.userId());
	}

	@ApplicationModuleListener
	void onUserSanctioned(UserSanctioned event) {
		deleteAllOf(event.userId());
	}

	private void deleteAllOf(UUID userId) {
		jdbc.sql("DELETE FROM device_tokens WHERE user_id = :userId").param("userId", userId).update();
	}

}
