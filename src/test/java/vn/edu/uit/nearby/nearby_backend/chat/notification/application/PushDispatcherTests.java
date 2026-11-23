package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationService.PushRequested;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushMessage;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushResult;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationType;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class PushDispatcherTests {

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	private final UUID recipient = UUID.randomUUID();

	private final UUID notificationId = UUID.randomUUID();

	private final PushRequested requested = new PushRequested(notificationId, recipient,
			NotificationType.WELCOME.render(null, java.util.Map.of()));

	@Mock
	PushGateway gateway;

	@Mock
	DeviceTokenRepository devices;

	@Mock
	NotificationRepository notifications;

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

	private PushDispatcher dispatcher() {
		return new PushDispatcher(gateway, devices, notifications, Clock.fixed(NOW, ZoneOffset.UTC), meters);
	}

	@Test
	@DisplayName("FR-NT-02: a push that reaches a device is SENT with its time")
	void aSentPushIsRecorded() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(gateway.send(any(), any())).thenReturn(PushResult.SENT);

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(notificationId, "SENT", NOW);
		assertThat(meters.counter("nearby.push.notifications", "result", "sent").count()).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-NT-02: the message handed to FCM carries the title, body and deep link")
	void theMessageCarriesTheDeepLink() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(gateway.send(any(), any())).thenReturn(PushResult.SENT);

		dispatcher().onPushRequested(requested);

		verify(gateway).send("token-1", new PushMessage("Chào mừng đến với Nearby",
				"Xác minh khu vực của bạn để bắt đầu mua bán với hàng xóm gần nhà.", "app://home"));
	}

	@Test
	@DisplayName("FR-NT-02: a token FCM rejects is deleted; if none is left the push is SKIPPED")
	void aDeadTokenIsDeleted() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("dead-token"));
		when(gateway.send(any(), any())).thenReturn(PushResult.INVALID_TOKEN);

		dispatcher().onPushRequested(requested);

		verify(devices).deleteByToken("dead-token");
		verify(notifications).recordPush(notificationId, "SKIPPED", null);
	}

	@Test
	void oneDeadTokenAndOneGoodTokenIsStillSent() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("dead", "good"));
		when(gateway.send(org.mockito.ArgumentMatchers.eq("dead"), any())).thenReturn(PushResult.INVALID_TOKEN);
		when(gateway.send(org.mockito.ArgumentMatchers.eq("good"), any())).thenReturn(PushResult.SENT);

		dispatcher().onPushRequested(requested);

		verify(devices).deleteByToken("dead");
		verify(devices, never()).deleteByToken("good");
		verify(notifications).recordPush(notificationId, "SENT", NOW);
	}

	@Test
	@DisplayName("FR-NT-02: an FCM failure is recorded as FAILED and nothing is thrown")
	void aFailureIsRecorded() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(gateway.send(any(), any())).thenReturn(PushResult.FAILED);

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(notificationId, "FAILED", null);
		assertThat(meters.counter("nearby.push.notifications", "result", "failed").count()).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-NT-02: a gateway that throws never breaks the flow")
	void aThrowingGatewayIsContained() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(gateway.send(any(), any())).thenThrow(new IllegalStateException("boom"));

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(notificationId, "FAILED", null);
	}

	@Test
	void aDatabaseFailureWhilePushingIsContained() {
		when(devices.tokensOf(recipient)).thenThrow(new IllegalStateException("db down"));

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(notificationId, "FAILED", null);
	}

	@Test
	void aDisabledGatewayIsSkippedNotFailed() {
		when(devices.tokensOf(recipient)).thenReturn(List.of("token-1"));
		when(gateway.send(any(), any())).thenReturn(PushResult.DISABLED);

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(notificationId, "SKIPPED", null);
		verify(devices, never()).deleteByToken(any());
	}

	@Test
	void noTokenLeftMeansSkipped() {
		when(devices.tokensOf(recipient)).thenReturn(List.of());

		dispatcher().onPushRequested(requested);

		verify(notifications).recordPush(org.mockito.ArgumentMatchers.eq(notificationId),
				org.mockito.ArgumentMatchers.eq("SKIPPED"), isNull());
	}

}
