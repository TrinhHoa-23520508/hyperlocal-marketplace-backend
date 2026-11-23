package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpired;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpiring;
import vn.edu.uit.nearby.nearby_backend.chat.MessageSent;
import vn.edu.uit.nearby.nearby_backend.chat.application.UserPresence;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationType;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.SourceEventId;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionType;
import vn.edu.uit.nearby.nearby_backend.identity.UserRegistered;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummaries;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;

/**
 * UC-SY-16: the domain events that create notifications. Each handler is safe to run
 * twice for the same event, because the source event id is derived from the event itself
 * (FR-NT-04, plan DEC-CH-5). The producers of trade, payment, review and moderation
 * notifications (register rows NT-W1..NT-W4) add their own listeners here when their
 * modules exist.
 */
@Component
class NotificationListeners {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VIETNAM);

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
		.withZone(VIETNAM);

	private static final String UNKNOWN_SENDER = "Tin nhắn mới";

	private final NotificationService notifications;

	private final NotificationRepository notificationRows;

	private final NotificationPreferencesRepository preferences;

	private final UserPresence presence;

	private final UserSummaries users;

	private final Clock clock;

	NotificationListeners(NotificationService notifications, NotificationRepository notificationRows,
			NotificationPreferencesRepository preferences, UserPresence presence, UserSummaries users, Clock clock) {
		this.notifications = notifications;
		this.notificationRows = notificationRows;
		this.preferences = preferences;
		this.presence = presence;
		this.users = users;
		this.clock = clock;
	}

	/**
	 * FR-NT-01: a message reaches an offline recipient as a notification and a push. An
	 * online recipient already sees it on the socket, so no row is created.
	 */
	@ApplicationModuleListener
	void onMessageSent(MessageSent sent) {
		if (presence.isOnline(sent.recipientId())) {
			return;
		}
		String sender = users.summariesOf(Set.of(sent.senderId()))
			.values()
			.stream()
			.findFirst()
			.map(UserSummary::displayName)
			.orElse(UNKNOWN_SENDER);
		notifications.notify(new Request(sent.recipientId(), NotificationType.MESSAGE_RECEIVED,
				sent.conversationId().toString(), Map.of("sender", sender, "preview", sent.preview()),
				SourceEventId.of("MessageSent", sent.messageId())));
	}

	/**
	 * appB: the preferences row is created with the account, and the user is welcomed
	 * (closes ID-W4 for the welcome).
	 */
	@ApplicationModuleListener
	void onUserRegistered(UserRegistered registered) {
		preferences.createDefaultsIfAbsent(registered.userId(), clock.instant());
		notifications.notify(new Request(registered.userId(), NotificationType.WELCOME, null, Map.of(),
				SourceEventId.of("UserRegistered", registered.userId())));
	}

	/**
	 * Closes ID-W4 for sanctions. The device tokens are already gone ({@code
	 * DeviceTokenCleanup}), so only the in-app notification is created.
	 */
	@ApplicationModuleListener
	void onUserSanctioned(UserSanctioned sanctioned) {
		boolean ban = sanctioned.type() == SanctionType.BAN;
		Map<String, String> arguments = ban || sanctioned.until() == null ? Map.of()
				: Map.of("until", DATE_TIME.format(sanctioned.until()));
		notifications.notify(new Request(sanctioned.userId(),
				ban ? NotificationType.SANCTION_BANNED : NotificationType.SANCTION_SUSPENDED, null, arguments,
				SourceEventId.of("UserSanctioned", sanctioned.userId(), sanctioned.sanctionedAt())));
	}

	@ApplicationModuleListener
	void onSanctionLifted(SanctionLifted lifted) {
		notifications.notify(new Request(lifted.userId(), NotificationType.SANCTION_LIFTED, null, Map.of(),
				SourceEventId.of("SanctionLifted", lifted.userId(), lifted.liftedAt())));
	}

	/**
	 * Closes AR-W2 (UC-SY-05 renewal reminder): the notification opens the area screen
	 * A28.
	 */
	@ApplicationModuleListener
	void onAreaExpiring(AreaExpiring expiring) {
		notifications.notify(new Request(expiring.userId(), NotificationType.AREA_EXPIRING,
				expiring.areaId().toString(), Map.of("expiresOn", DATE.format(expiring.expiresAt())),
				SourceEventId.of("AreaExpiring", expiring.areaId(), expiring.expiresAt())));
	}

	@ApplicationModuleListener
	void onAreaExpired(AreaExpired expired) {
		notifications.notify(new Request(expired.userId(), NotificationType.AREA_EXPIRED, expired.areaId().toString(),
				Map.of(), SourceEventId.of("AreaExpired", expired.areaId(), expired.expiredAt())));
	}

	/**
	 * FR-ID-16: the account's notifications and preferences go with it. Messages stay
	 * with an anonymous author (ch4 retention).
	 */
	@ApplicationModuleListener
	void onAccountDeleted(AccountDeleted deleted) {
		UUID userId = deleted.userId();
		notificationRows.deleteOf(userId);
		preferences.delete(userId);
	}

}
