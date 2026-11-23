package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.WebSocketDestinations;

/**
 * FR-NT-06: the unread counts behind the two badges of the bottom navigation, and their
 * push to every session of the user so a read on one device updates the others.
 */
@Service
public class BadgeService {

	private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

	private final ConversationRepository conversations;

	private final NotificationRepository notifications;

	private final SimpMessagingTemplate template;

	BadgeService(ConversationRepository conversations, NotificationRepository notifications,
			SimpMessagingTemplate template) {
		this.conversations = conversations;
		this.notifications = notifications;
		this.template = template;
	}

	/**
	 * @param chatUnread messages not yet read across all conversations
	 * @param notificationUnread notifications not yet read
	 */
	public record BadgeCounts(@Schema(example = "3") int chatUnread, @Schema(example = "1") int notificationUnread) {
	}

	/**
	 * Something changed the counts of this user; once the change commits, the new counts
	 * are pushed to their sessions.
	 */
	public record BadgesChanged(UUID userId) {
	}

	@Transactional(readOnly = true)
	public BadgeCounts countsOf(UUID userId) {
		return new BadgeCounts(conversations.unreadTotalOf(userId), notifications.unreadCountOf(userId));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onBadgesChanged(BadgesChanged changed) {
		pushTo(changed.userId());
	}

	/**
	 * Pushes the current counts on {@code /user/queue/badges}. A failure is only logged:
	 * the app also reads the counts by REST (FR-NT-06 "trong một chu kỳ làm mới").
	 */
	public void pushTo(UUID userId) {
		try {
			template.convertAndSendToUser(userId.toString(), WebSocketDestinations.QUEUE_BADGES, countsOf(userId));
		}
		catch (RuntimeException ex) {
			log.warn("Could not push the badges of {}: {}", userId, ex.toString());
		}
	}

}
