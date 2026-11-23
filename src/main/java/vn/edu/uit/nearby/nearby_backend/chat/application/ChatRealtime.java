package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import vn.edu.uit.nearby.nearby_backend.chat.application.MessageWriter.MessageStored;
import vn.edu.uit.nearby.nearby_backend.chat.application.ReadService.ReadRecorded;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService;
import vn.edu.uit.nearby.nearby_backend.shared.constant.WebSocketDestinations;

/**
 * Pushes chat events over STOMP once the database transaction has committed, so a client
 * never receives a message that was then rolled back. REST stays the source of truth: a
 * push that fails is only logged, and the app catches up through the sync call
 * (FR-CH-07). With the RabbitMQ broker relay the message reaches the recipient on any
 * replica.
 */
@Component
class ChatRealtime {

	private static final Logger log = LoggerFactory.getLogger(ChatRealtime.class);

	/**
	 * UC-CH-02 step 7, FR-CH-06: the counterpart sees the message as read.
	 */
	record ReadReceipt(UUID conversationId, UUID readerId, long lastReadMessageId) {
	}

	private final SimpMessagingTemplate template;

	private final MessageViews views;

	private final BadgeService badges;

	ChatRealtime(SimpMessagingTemplate template, MessageViews views, BadgeService badges) {
		this.template = template;
		this.views = views;
		this.badges = badges;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onMessageStored(MessageStored stored) {
		Conversation conversation = stored.conversation();
		StoredMessage message = stored.message();
		MessageView view = views.of(message);
		// Both members get the message: the sender's other devices show it too.
		sendTo(conversation.buyerId(), WebSocketDestinations.QUEUE_MESSAGES, view);
		sendTo(conversation.sellerId(), WebSocketDestinations.QUEUE_MESSAGES, view);
		if (stored.viaSocket() && message.senderId() != null) {
			sendTo(message.senderId(), WebSocketDestinations.QUEUE_ACKS,
					new MessageAck(message.clientMsgId(), message.id(), message.createdAt()));
		}
		// FR-NT-06: every session of the members sees the new unread count.
		badges.pushTo(conversation.buyerId());
		badges.pushTo(conversation.sellerId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onReadRecorded(ReadRecorded read) {
		sendTo(read.conversation().counterpartOf(read.readerId()), WebSocketDestinations.QUEUE_READ_RECEIPTS,
				new ReadReceipt(read.conversation().id(), read.readerId(), read.lastReadMessageId()));
		// FR-CH-06: opening a conversation sets the badge to 0 on every device of the
		// reader.
		badges.pushTo(read.readerId());
	}

	private void sendTo(UUID userId, String destination, Object payload) {
		try {
			template.convertAndSendToUser(userId.toString(), destination, payload);
		}
		catch (RuntimeException ex) {
			log.warn("Could not push to {} on {}: {}", userId, destination, ex.toString());
		}
	}

}
