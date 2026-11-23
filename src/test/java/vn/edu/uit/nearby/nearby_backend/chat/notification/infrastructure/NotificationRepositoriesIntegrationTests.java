package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.NewNotification;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.StoredNotification;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The SQL of notifications, preferences and device tokens against PostgreSQL.
 */
@IntegrationTest
class NotificationRepositoriesIntegrationTests {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	@Autowired
	NotificationRepository notifications;

	@Autowired
	NotificationPreferencesRepository preferences;

	@Autowired
	DeviceTokenRepository devices;

	private final UUID user = UUID.randomUUID();

	private NewNotification notification(UUID recipient, UUID sourceEventId, Instant at) {
		return new NewNotification(UUID.randomUUID(), recipient, NotificationCategory.TRADE, "OFFER_RECEIVED",
				"Có đề nghị mới", "Một người muốn mua", "app://transactions/t-1", Map.of("targetId", "t-1"),
				sourceEventId, "PENDING", at);
	}

	@Nested
	@DisplayName("FR-NT-04: dedup by (source event, recipient)")
	class Dedup {

		@Test
		void aReplayedEventCreatesNothingForTheSameRecipient() {
			UUID event = UUID.randomUUID();

			assertThat(notifications.insertIfAbsent(notification(user, event, T0))).isTrue();
			assertThat(notifications.insertIfAbsent(notification(user, event, T0.plusSeconds(60)))).isFalse();

			assertThat(notifications.pageOf(user, null, 10)).hasSize(1);
		}

		@Test
		void twoRecipientsOfOneEventEachGetOne() {
			UUID event = UUID.randomUUID();

			assertThat(notifications.insertIfAbsent(notification(user, event, T0))).isTrue();
			assertThat(notifications.insertIfAbsent(notification(UUID.randomUUID(), event, T0))).isTrue();
		}

	}

	@Nested
	@DisplayName("FR-NT-05: list, read and unread count")
	class Listing {

		@Test
		void newestFirstWithACursor() {
			for (int i = 0; i < 5; i++) {
				notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0.plusSeconds(i)));
			}

			List<StoredNotification> firstPage = notifications.pageOf(user, null, 2);
			Cursor cursor = new Cursor(firstPage.getLast().createdAt(), firstPage.getLast().id());
			List<StoredNotification> second = notifications.pageOf(user, cursor, 10);

			assertThat(firstPage).extracting(StoredNotification::createdAt)
				.containsExactly(T0.plusSeconds(4), T0.plusSeconds(3));
			assertThat(second).extracting(StoredNotification::createdAt)
				.containsExactly(T0.plusSeconds(2), T0.plusSeconds(1), T0);
		}

		@Test
		void aPayloadAndADeepLinkRoundTrip() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));

			StoredNotification stored = notifications.pageOf(user, null, 1).getFirst();

			assertThat(stored.deepLink()).isEqualTo("app://transactions/t-1");
			assertThat(stored.payload()).isEqualTo(Map.of("targetId", "t-1"));
			assertThat(stored.category()).isEqualTo(NotificationCategory.TRADE);
			assertThat(stored.isRead()).isFalse();
		}

		@Test
		void readingOneKeepsTheFirstReadTimeAndOthersUnread() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0.plusSeconds(1)));
			UUID first = notifications.pageOf(user, null, 10).getLast().id();

			assertThat(notifications.markRead(first, user, T0.plusSeconds(10))).isTrue();
			assertThat(notifications.markRead(first, user, T0.plusSeconds(99))).isTrue();

			assertThat(notifications.findOwned(first, user).orElseThrow().readAt()).isEqualTo(T0.plusSeconds(10));
			assertThat(notifications.unreadCountOf(user)).isEqualTo(1);
		}

		@Test
		void aNotificationOfSomeoneElseIsNotFound() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));
			UUID id = notifications.pageOf(user, null, 1).getFirst().id();

			assertThat(notifications.markRead(id, UUID.randomUUID(), T0)).isFalse();
			assertThat(notifications.markRead(UUID.randomUUID(), user, T0)).isFalse();
			assertThat(notifications.findOwned(id, UUID.randomUUID())).isEmpty();
		}

		@Test
		@DisplayName("FR-NT-05: marking everything read sets the count to 0")
		void markAllReadClearsTheBadge() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));

			assertThat(notifications.markAllRead(user, T0)).isEqualTo(2);

			assertThat(notifications.unreadCountOf(user)).isZero();
			assertThat(notifications.markAllRead(user, T0)).isZero();
		}

		@Test
		void thePushOutcomeIsRecorded() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));
			UUID id = notifications.pageOf(user, null, 1).getFirst().id();

			notifications.recordPush(id, "SENT", T0.plusSeconds(2));

			StoredNotification stored = notifications.findOwned(id, user).orElseThrow();
			assertThat(stored.pushStatus()).isEqualTo("SENT");
			assertThat(stored.pushedAt()).isEqualTo(T0.plusSeconds(2));
		}

	}

	@Nested
	@DisplayName("retention and account deletion")
	class Removal {

		@Test
		void notificationsBeforeTheCutoffAreDeleted() {
			UUID other = UUID.randomUUID();
			notifications.insertIfAbsent(notification(other, UUID.randomUUID(), T0.minus(Duration.ofDays(200))));
			notifications.insertIfAbsent(notification(other, UUID.randomUUID(), T0.minus(Duration.ofDays(10))));

			int deleted = notifications.deleteCreatedBefore(T0.minus(Duration.ofDays(180)));

			assertThat(deleted).isGreaterThanOrEqualTo(1);
			assertThat(notifications.pageOf(other, null, 10)).hasSize(1);
		}

		@Test
		void everyNotificationOfAUserCanBeDeleted() {
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));
			notifications.insertIfAbsent(notification(user, UUID.randomUUID(), T0));

			assertThat(notifications.deleteOf(user)).isEqualTo(2);
			assertThat(notifications.unreadCountOf(user)).isZero();
		}

	}

	@Nested
	@DisplayName("FR-NT-03: preferences")
	class Preferences {

		@Test
		void defaultsAreCreatedOnceAndANewUserHasNoRowBefore() {
			assertThat(preferences.find(user)).isEmpty();

			preferences.createDefaultsIfAbsent(user, T0);
			preferences.createDefaultsIfAbsent(user, T0.plusSeconds(5));

			assertThat(preferences.find(user)).contains(NotificationPreferences.defaults(user));
		}

		@Test
		@DisplayName("a save with the version that was read succeeds and bumps it; a stale one saves nothing")
		void optimisticLock() {
			preferences.createDefaultsIfAbsent(user, T0);
			NotificationPreferences changed = new NotificationPreferences(user, false, true, true, false, true,
					LocalTime.of(23, 0), LocalTime.of(6, 30), ZoneId.of("Asia/Tokyo"), 0);

			NotificationPreferences saved = preferences.saveIfVersion(changed, 0, T0).orElseThrow();
			var stale = preferences.saveIfVersion(changed, 0, T0);

			assertThat(saved.version()).isEqualTo(1);
			assertThat(saved.chatPush()).isFalse();
			assertThat(saved.dndStart()).isEqualTo(LocalTime.of(23, 0));
			assertThat(saved.timeZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
			assertThat(stale).isEmpty();
		}

		@Test
		void deletingRemovesTheRow() {
			preferences.createDefaultsIfAbsent(user, T0);

			preferences.delete(user);

			assertThat(preferences.find(user)).isEmpty();
		}

	}

	@Nested
	@DisplayName("FR-NT-02: device tokens")
	class Devices {

		@Test
		void aDeviceKeepsOneTokenAndARotatedTokenReplacesTheOldOne() {
			devices.upsert(user, "device-1", "token-a-" + user, "ANDROID", "1.0.0", T0);
			devices.upsert(user, "device-1", "token-b-" + user, "ANDROID", "1.0.1", T0.plusSeconds(5));
			devices.upsert(user, "device-2", "token-c-" + user, "WEB", null, T0.plusSeconds(6));

			assertThat(devices.tokensOf(user)).containsExactly("token-b-" + user, "token-c-" + user);
		}

		@Test
		@DisplayName("a token that another user registered moves to the new owner")
		void aTokenMovesToTheLatestUser() {
			UUID previous = UUID.randomUUID();
			String shared = "shared-" + user;
			devices.upsert(previous, "device-9", shared, "ANDROID", null, T0);

			devices.upsert(user, "device-1", shared, "ANDROID", null, T0.plusSeconds(1));

			assertThat(devices.tokensOf(previous)).isEmpty();
			assertThat(devices.tokensOf(user)).containsExactly(shared);
		}

		@Test
		void aDeviceCanBeForgottenByIdOrByToken() {
			devices.upsert(user, "device-1", "token-1-" + user, "ANDROID", null, T0);
			devices.upsert(user, "device-2", "token-2-" + user, "ANDROID", null, T0);

			assertThat(devices.deleteDevice(user, "device-1")).isTrue();
			assertThat(devices.deleteDevice(user, "device-1")).isFalse();
			devices.deleteByToken("token-2-" + user);

			assertThat(devices.tokensOf(user)).isEmpty();
		}

	}

}
