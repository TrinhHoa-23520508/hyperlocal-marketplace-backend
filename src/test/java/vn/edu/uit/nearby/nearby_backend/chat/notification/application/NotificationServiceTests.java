package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService.BadgesChanged;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationService.PushRequested;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationType;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.NewNotification;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

	private static final Instant DAYTIME = Instant.parse("2026-11-23T05:00:00Z");

	private static final Instant NIGHT = Instant.parse("2026-11-23T16:30:00Z");

	private final UUID recipient = UUID.randomUUID();

	private final UUID sourceEvent = UUID.randomUUID();

	@Mock
	NotificationRepository notifications;

	@Mock
	NotificationPreferencesRepository preferences;

	@Mock
	DeviceTokenRepository devices;

	@Mock
	ApplicationEventPublisher events;

	private NotificationService service(Instant now) {
		return new NotificationService(notifications, preferences, devices, events, Clock.fixed(now, ZoneOffset.UTC));
	}

	private Request request(NotificationType type) {
		return new Request(recipient, type, "c-1", Map.of("sender", "An", "preview", "Còn hàng không?"), sourceEvent);
	}

	private NewNotification inserted() {
		ArgumentCaptor<NewNotification> captor = ArgumentCaptor.forClass(NewNotification.class);
		verify(notifications).insertIfAbsent(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("FR-NT-01: the row carries the rendered title, body and deep link, and a push follows")
	void createsTheRowAndRequestsAPush() {
		when(preferences.find(recipient)).thenReturn(Optional.empty());
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(notifications.insertIfAbsent(any())).thenReturn(true);

		boolean created = service(DAYTIME).notify(request(NotificationType.MESSAGE_RECEIVED));

		assertThat(created).isTrue();
		NewNotification row = inserted();
		assertThat(row.category()).isEqualTo(NotificationCategory.CHAT);
		assertThat(row.type()).isEqualTo("MESSAGE_RECEIVED");
		assertThat(row.title()).isEqualTo("An");
		assertThat(row.body()).isEqualTo("Còn hàng không?");
		assertThat(row.deepLink()).isEqualTo("app://conversations/c-1");
		assertThat(row.payload()).isEqualTo(Map.of("targetId", "c-1"));
		assertThat(row.sourceEventId()).isEqualTo(sourceEvent);
		assertThat(row.pushStatus()).isEqualTo("PENDING");
		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(events, org.mockito.Mockito.times(2)).publishEvent(published.capture());
		assertThat(published.getAllValues()).anyMatch(event -> event instanceof PushRequested push
				&& push.notificationId().equals(row.id()) && push.recipientId().equals(recipient));
		assertThat(published.getAllValues()).contains(new BadgesChanged(recipient));
	}

	@Test
	@DisplayName("FR-NT-04: a replayed event creates nothing and sends nothing")
	void aReplayedEventIsIgnored() {
		when(preferences.find(recipient)).thenReturn(Optional.empty());
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(notifications.insertIfAbsent(any())).thenReturn(false);

		boolean created = service(DAYTIME).notify(request(NotificationType.MESSAGE_RECEIVED));

		assertThat(created).isFalse();
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	@DisplayName("FR-NT-03: TRADE off still creates the in-app row but marks the push SKIPPED")
	void aSwitchedOffCategoryStillCreatesTheRow() {
		NotificationPreferences tradeOff = NotificationPreferences.defaults(recipient)
			.apply(new Change(true, false, true, false, true, false, LocalTime.of(22, 0), LocalTime.of(7, 0),
					"Asia/Ho_Chi_Minh"), false);
		when(preferences.find(recipient)).thenReturn(Optional.of(tradeOff));
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(notifications.insertIfAbsent(any())).thenReturn(true);

		service(DAYTIME).notify(new Request(recipient, NotificationType.OFFER_RECEIVED, "t-1",
				Map.of("buyer", "An", "price", "100.000đ", "listing", "Ghế"), sourceEvent));

		assertThat(inserted().pushStatus()).isEqualTo("SKIPPED");
		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(published.capture());
		assertThat(published.getValue()).isEqualTo(new BadgesChanged(recipient));
	}

	@Test
	@DisplayName("FR-NT-03: quiet hours skip the push but keep the notification")
	void quietHoursSkipThePush() {
		NotificationPreferences quiet = NotificationPreferences.defaults(recipient)
			.apply(new Change(true, true, true, false, true, true, LocalTime.of(22, 0), LocalTime.of(7, 0),
					"Asia/Ho_Chi_Minh"), false);
		when(preferences.find(recipient)).thenReturn(Optional.of(quiet));
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(notifications.insertIfAbsent(any())).thenReturn(true);

		service(NIGHT).notify(request(NotificationType.MESSAGE_RECEIVED));

		assertThat(inserted().pushStatus()).isEqualTo("SKIPPED");
	}

	@Test
	void noDeviceTokenMeansNoPush() {
		when(preferences.find(recipient)).thenReturn(Optional.empty());
		when(devices.tokensOf(recipient)).thenReturn(List.of());
		when(notifications.insertIfAbsent(any())).thenReturn(true);

		service(DAYTIME).notify(request(NotificationType.MESSAGE_RECEIVED));

		assertThat(inserted().pushStatus()).isEqualTo("SKIPPED");
	}

	@Test
	void aNotificationWithoutATargetHasNoPayload() {
		when(preferences.find(recipient)).thenReturn(Optional.empty());
		when(devices.tokensOf(recipient)).thenReturn(List.of());
		when(notifications.insertIfAbsent(any())).thenReturn(true);

		service(DAYTIME).notify(new Request(recipient, NotificationType.WELCOME, null, Map.of(), sourceEvent));

		assertThat(inserted().payload()).isNull();
		assertThat(inserted().deepLink()).isEqualTo("app://home");
	}

}
