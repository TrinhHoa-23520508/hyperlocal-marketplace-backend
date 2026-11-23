package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.micrometer.core.instrument.MeterRegistry;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationService.PushRequested;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushMessage;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushResult;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;

/**
 * FR-NT-02: pushes a committed notification to every device of the recipient and records
 * the outcome in {@code push_status}. A token FCM rejects is deleted. Nothing here throws
 * into the business flow: the in-app notification already exists.
 */
@Component
class PushDispatcher {

	private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

	private final PushGateway gateway;

	private final DeviceTokenRepository devices;

	private final NotificationRepository notifications;

	private final Clock clock;

	private final MeterRegistry meters;

	PushDispatcher(PushGateway gateway, DeviceTokenRepository devices, NotificationRepository notifications,
			Clock clock, MeterRegistry meters) {
		this.gateway = gateway;
		this.devices = devices;
		this.notifications = notifications;
		this.clock = clock;
		this.meters = meters;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onPushRequested(PushRequested requested) {
		try {
			dispatch(requested);
		}
		catch (RuntimeException ex) {
			log.warn("Push of notification {} failed: {}", requested.notificationId(), ex.toString());
			recordQuietly(requested, "FAILED");
			count("failed");
		}
	}

	private void dispatch(PushRequested requested) {
		List<String> tokens = devices.tokensOf(requested.recipientId());
		PushMessage message = new PushMessage(requested.content().title(), requested.content().body(),
				requested.content().deepLink().asUri());
		int sent = 0;
		int dead = 0;
		int disabled = 0;
		for (String token : tokens) {
			PushResult result = send(token, message);
			switch (result) {
				case SENT -> sent++;
				case INVALID_TOKEN -> {
					devices.deleteByToken(token);
					dead++;
				}
				case DISABLED -> disabled++;
				case FAILED -> {
				}
			}
		}
		if (sent > 0) {
			notifications.recordPush(requested.notificationId(), "SENT", clock.instant());
			count("sent");
		}
		else if (tokens.isEmpty() || dead + disabled == tokens.size()) {
			notifications.recordPush(requested.notificationId(), "SKIPPED", null);
			count("skipped");
		}
		else {
			notifications.recordPush(requested.notificationId(), "FAILED", null);
			count("failed");
		}
	}

	private PushResult send(String token, PushMessage message) {
		try {
			return gateway.send(token, message);
		}
		catch (RuntimeException ex) {
			log.warn("The push gateway threw: {}", ex.toString());
			return PushResult.FAILED;
		}
	}

	/**
	 * Monitoring (ch6): push outcomes, so failures can be alerted on.
	 */
	private void count(String result) {
		meters.counter("nearby.push.notifications", "result", result).increment();
	}

	private void recordQuietly(PushRequested requested, String status) {
		try {
			notifications.recordPush(requested.notificationId(), status, null);
		}
		catch (RuntimeException ex) {
			log.warn("Could not record the push status of {}: {}", requested.notificationId(), ex.toString());
		}
	}

}
