package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PreferencesService.PreferencesView;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.identity.MarketingConsent;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class PreferencesAndDevicesTests {

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final UUID user = UUID.randomUUID();

	@Mock
	NotificationPreferencesRepository preferences;

	@Mock
	MarketingConsent consent;

	@Mock
	DeviceTokenRepository devices;

	private Change change(boolean marketing, boolean system) {
		return new Change(true, false, true, marketing, system, true, LocalTime.of(23, 0), LocalTime.of(6, 0),
				"Asia/Ho_Chi_Minh");
	}

	@Nested
	@DisplayName("UC-NT-02 / FR-NT-03: preferences")
	class Preferences {

		private PreferencesService service() {
			return new PreferencesService(preferences, consent, CLOCK);
		}

		@Test
		@DisplayName("a user without a row reads the defaults, with SYSTEM always on")
		void defaultsForAUserWithoutARow() {
			when(preferences.find(user)).thenReturn(Optional.empty());
			when(consent.allowsPushMarketing(user)).thenReturn(false);

			PreferencesView view = service().get(user);

			assertThat(view.chatPush()).isTrue();
			assertThat(view.marketingPush()).isFalse();
			assertThat(view.systemPush()).isTrue();
			assertThat(view.dndStart()).isEqualTo("22:00");
			assertThat(view.dndEnd()).isEqualTo("07:00");
			assertThat(view.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
			assertThat(view.marketingConsentGranted()).isFalse();
			assertThat(view.version()).isZero();
		}

		@Test
		void aSaveStoresTheChangeWithTheVersionTheClientRead() {
			NotificationPreferences current = NotificationPreferences.defaults(user);
			when(preferences.find(user)).thenReturn(Optional.of(current));
			when(consent.allowsPushMarketing(user)).thenReturn(true);
			when(preferences.saveIfVersion(any(), eq(0L), eq(NOW)))
				.thenAnswer(invocation -> Optional.of(invocation.<NotificationPreferences>getArgument(0)));

			PreferencesView view = service().update(user, change(true, true), 0);

			verify(preferences).createDefaultsIfAbsent(user, NOW);
			assertThat(view.tradePush()).isFalse();
			assertThat(view.marketingPush()).isTrue();
			assertThat(view.marketingConsentGranted()).isTrue();
			assertThat(view.dndEnabled()).isTrue();
			assertThat(view.dndStart()).isEqualTo("23:00");
			assertThat(view.dndEnd()).isEqualTo("06:00");
		}

		@Test
		@DisplayName("a stale version is 409 CONCURRENT_MODIFICATION")
		void aStaleVersionConflicts() {
			when(preferences.find(user)).thenReturn(Optional.of(NotificationPreferences.defaults(user)));
			when(consent.allowsPushMarketing(user)).thenReturn(false);
			when(preferences.saveIfVersion(any(), anyLong(), any())).thenReturn(Optional.empty());

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> service().update(user, change(false, true), 3));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONCURRENT_MODIFICATION);
		}

		@Test
		@DisplayName("FR-NT-03: turning SYSTEM off is 400 and nothing is saved")
		void systemCannotBeTurnedOff() {
			when(preferences.find(user)).thenReturn(Optional.of(NotificationPreferences.defaults(user)));
			when(consent.allowsPushMarketing(user)).thenReturn(false);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> service().update(user, change(false, false), 0));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
			verify(preferences, never()).saveIfVersion(any(), anyLong(), any());
		}

		@Test
		@DisplayName("FR-NT-03: MARKETING needs the PUSH_MARKETING consent")
		void marketingNeedsConsent() {
			when(preferences.find(user)).thenReturn(Optional.of(NotificationPreferences.defaults(user)));
			when(consent.allowsPushMarketing(user)).thenReturn(false);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> service().update(user, change(true, true), 0));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		void theStoredZoneIsShownByItsId() {
			NotificationPreferences tokyo = new NotificationPreferences(user, true, true, true, false, false,
					LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Tokyo"), 5);
			when(preferences.find(user)).thenReturn(Optional.of(tokyo));
			when(consent.allowsPushMarketing(user)).thenReturn(false);

			assertThat(service().get(user).timeZone()).isEqualTo("Asia/Tokyo");
			assertThat(service().get(user).version()).isEqualTo(5);
		}

	}

	@Nested
	@DisplayName("FR-NT-02 / ID-W1: registering the device token")
	class Devices {

		private DeviceService service() {
			return new DeviceService(devices, CLOCK);
		}

		@Test
		void aValidRegistrationIsSaved() {
			service().register(user, new DeviceService.Registration(" device-1 ", " token ", "android", "1.2.3"));

			verify(devices).upsert(user, "device-1", "token", "ANDROID", "1.2.3", NOW);
		}

		@Test
		void theAppVersionIsOptional() {
			service().register(user, new DeviceService.Registration("device-1", "token", "WEB", null));

			verify(devices).upsert(user, "device-1", "token", "WEB", null, NOW);
		}

		@Test
		void aBlankOrTooLongIdOrTokenIsInvalid() {
			assertInvalid(
					() -> service().register(user, new DeviceService.Registration(" ", "token", "ANDROID", null)));
			assertInvalid(
					() -> service().register(user, new DeviceService.Registration(null, "token", "ANDROID", null)));
			assertInvalid(() -> service().register(user,
					new DeviceService.Registration("d".repeat(65), "token", "ANDROID", null)));
			assertInvalid(
					() -> service().register(user, new DeviceService.Registration("device-1", "", "ANDROID", null)));
			assertInvalid(() -> service().register(user,
					new DeviceService.Registration("device-1", "t".repeat(256), "ANDROID", null)));
			verify(devices, never()).upsert(any(), any(), any(), any(), any(), any());
		}

		@Test
		void anUnknownPlatformOrTooLongVersionIsInvalid() {
			assertInvalid(
					() -> service().register(user, new DeviceService.Registration("device-1", "token", "IOS", null)));
			assertInvalid(
					() -> service().register(user, new DeviceService.Registration("device-1", "token", null, null)));
			assertInvalid(() -> service().register(user,
					new DeviceService.Registration("device-1", "token", "ANDROID", "v".repeat(21))));
		}

		@Test
		void forgettingADeviceIsPassedThrough() {
			service().unregister(user, "device-1");

			verify(devices).deleteDevice(user, "device-1");
		}

		private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
			BusinessException error = catchThrowableOfType(BusinessException.class, call);
			assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

	}

}
