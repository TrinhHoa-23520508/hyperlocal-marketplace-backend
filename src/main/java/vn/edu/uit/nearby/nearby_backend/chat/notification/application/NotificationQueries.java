package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService.BadgesChanged;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.StoredNotification;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * UC-NT-01 (A29): the notification list, newest first with a cursor, mark one read and
 * mark all read (FR-NT-05). Every change of the unread count is pushed to the user's
 * other sessions (FR-NT-06).
 */
@Service
public class NotificationQueries {

	private final NotificationRepository notifications;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	NotificationQueries(NotificationRepository notifications, ApplicationEventPublisher events, Clock clock) {
		this.notifications = notifications;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @param deepLink {@code app://<screen>/<id>}; the screen answers 404 or "không còn
	 * khả dụng" when its target is gone (FR-NT-05)
	 * @param read false for the items A29 highlights
	 */
	public record NotificationView(UUID id, @Schema(example = "TRADE") String category,
			@Schema(example = "OFFER_RECEIVED") String type, String title, String body, @Nullable String deepLink,
			boolean read, @Nullable Instant readAt, Instant createdAt) {
	}

	@Transactional(readOnly = true)
	public CursorPage<NotificationView> list(UUID userId, @Nullable String cursor, @Nullable Integer requestedLimit) {
		int limit = CursorPage.clampLimit(requestedLimit);
		List<StoredNotification> fetched = notifications.pageOf(userId, Cursor.decode(cursor).orElse(null), limit + 1);
		return CursorPage.of(fetched, limit, row -> new Cursor(row.createdAt(), row.id()))
			.map(NotificationQueries::viewOf);
	}

	/**
	 * Marking a read notification again changes nothing.
	 * @throws ResourceNotFoundException {@code NOTIFICATION_NOT_FOUND} for an unknown id
	 * or someone else's notification
	 */
	@Transactional
	public void markRead(UUID userId, UUID notificationId) {
		if (!notifications.markRead(notificationId, userId, clock.instant())) {
			throw new ResourceNotFoundException(ErrorCodes.NOTIFICATION_NOT_FOUND, "Notification not found.");
		}
		events.publishEvent(new BadgesChanged(userId));
	}

	/**
	 * @return how many were unread; the badge is 0 afterwards (FR-NT-05 acceptance)
	 */
	@Transactional
	public int markAllRead(UUID userId) {
		int changed = notifications.markAllRead(userId, clock.instant());
		if (changed > 0) {
			events.publishEvent(new BadgesChanged(userId));
		}
		return changed;
	}

	private static NotificationView viewOf(StoredNotification row) {
		return new NotificationView(row.id(), row.category().name(), row.type(), row.title(), row.body(),
				row.deepLink(), row.isRead(), row.readAt(), row.createdAt());
	}

}
