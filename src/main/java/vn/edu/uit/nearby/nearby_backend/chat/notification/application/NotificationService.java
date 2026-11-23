package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService.BadgesChanged;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationContent;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationType;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.PushDecision;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.NewNotification;

/**
 * UC-SY-16: creates the in-app notification of one event for one recipient, once
 * (FR-NT-04), and decides whether a push follows (FR-NT-02, FR-NT-03). The in-app row is
 * always created; preferences, quiet hours and missing tokens only skip the push.
 */
@Service
public class NotificationService {

	static final String PENDING = "PENDING";

	static final String SKIPPED = "SKIPPED";

	private final NotificationRepository notifications;

	private final NotificationPreferencesRepository preferences;

	private final DeviceTokenRepository devices;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	NotificationService(NotificationRepository notifications, NotificationPreferencesRepository preferences,
			DeviceTokenRepository devices, ApplicationEventPublisher events, Clock clock) {
		this.notifications = notifications;
		this.preferences = preferences;
		this.devices = devices;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @param targetId what the deep link opens, null for a screen without an id
	 * @param arguments values for the placeholders of the type's title and body
	 * @param sourceEventId the dedup key; build it with {@code SourceEventId}
	 */
	public record Request(UUID recipientId, NotificationType type, @Nullable String targetId,
			Map<String, String> arguments, UUID sourceEventId) {
	}

	/**
	 * Raised inside the module for a notification that should be pushed; sent after the
	 * row has committed, so a slow FCM never holds a database transaction.
	 */
	record PushRequested(UUID notificationId, UUID recipientId, NotificationContent content) {
	}

	/**
	 * @return false when the same event was already notified to this recipient and
	 * nothing was created
	 */
	@Transactional
	public boolean notify(Request request) {
		NotificationContent content = request.type().render(request.targetId(), request.arguments());
		Instant now = clock.instant();
		PushDecision decision = decideAt(request, now);
		UUID id = UUID.randomUUID();
		boolean created = notifications.insertIfAbsent(
				new NewNotification(id, request.recipientId(), request.type().category(), request.type().name(),
						content.title(), content.body(), content.deepLink().asUri(), payloadOf(request.targetId()),
						request.sourceEventId(), decision.shouldSend() ? PENDING : SKIPPED, now));
		if (!created) {
			return false;
		}
		if (decision.shouldSend()) {
			events.publishEvent(new PushRequested(id, request.recipientId(), content));
		}
		events.publishEvent(new BadgesChanged(request.recipientId()));
		return true;
	}

	private PushDecision decideAt(Request request, Instant now) {
		NotificationPreferences stored = preferences.find(request.recipientId())
			.orElseGet(() -> NotificationPreferences.defaults(request.recipientId()));
		boolean hasDeviceToken = !devices.tokensOf(request.recipientId()).isEmpty();
		return PushDecision.decide(stored, request.type().category(), false, hasDeviceToken, now);
	}

	private static @Nullable Map<String, String> payloadOf(@Nullable String targetId) {
		return targetId == null ? null : Map.of("targetId", targetId);
	}

}
