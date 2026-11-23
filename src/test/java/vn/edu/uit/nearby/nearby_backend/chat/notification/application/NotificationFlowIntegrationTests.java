package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpired;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpiring;
import vn.edu.uit.nearby.nearby_backend.chat.MessageSent;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatParameters;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService.BadgeCounts;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationQueries.NotificationView;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.StoredNotification;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionType;
import vn.edu.uit.nearby.nearby_backend.identity.UserRegistered;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * UC-SY-16, UC-NT-01 and FR-NT-01..06 through the real application: events in, in-app
 * rows and push outcomes out. The push gateway is the logging one, because no FCM
 * credentials are configured.
 */
@IntegrationTest
class NotificationFlowIntegrationTests {

	private static final Duration ASYNC = Duration.ofSeconds(15);

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	NotificationRepository notifications;

	@Autowired
	NotificationPreferencesRepository preferences;

	@Autowired
	DeviceTokenRepository devices;

	@Autowired
	NotificationQueries queries;

	@Autowired
	BadgeService badges;

	@Autowired
	DeviceService deviceService;

	@Autowired
	NotificationRetentionJob retention;

	@Autowired
	ChatParameters parameters;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	ConversationRepository conversations;

	@Autowired
	MessageRepository messages;

	private final UUID user = UUID.randomUUID();

	/**
	 * A conversation in which the counterpart sent {@code count} messages that
	 * {@code reader} has not read.
	 */
	private void givenUnreadMessages(UUID reader, int count) {
		UUID counterpart = UUID.randomUUID();
		UUID conversation = conversations.insertIfAbsent(UUID.randomUUID(), reader, counterpart, Instant.now())
			.conversation()
			.id();
		for (int i = 0; i < count; i++) {
			messages.insert(conversation, counterpart, MessageContent.text("tin " + i, 1000), UUID.randomUUID(),
					Instant.now());
			conversations.addUnreadForOthers(conversation, counterpart);
		}
	}

	private void publish(Object event) {
		transaction.executeWithoutResult(status -> publisher.publishEvent(event));
	}

	private List<StoredNotification> rowsOf(UUID recipient) {
		return notifications.pageOf(recipient, null, 50);
	}

	private void awaitRows(UUID recipient, int count) {
		await().atMost(ASYNC).untilAsserted(() -> assertThat(rowsOf(recipient)).hasSize(count));
	}

	@Nested
	@DisplayName("events that notify")
	class Events {

		@Test
		@DisplayName("ID-W4 / appB: a new account gets its preferences row and a welcome notification")
		void registrationWelcomesTheUser() {
			publish(new UserRegistered(user, Instant.now()));

			awaitRows(user, 1);
			StoredNotification welcome = rowsOf(user).getFirst();
			assertThat(welcome.type()).isEqualTo("WELCOME");
			assertThat(welcome.category().name()).isEqualTo("SYSTEM");
			assertThat(welcome.deepLink()).isEqualTo("app://home");
			assertThat(preferences.find(user)).isPresent();
		}

		@Test
		@DisplayName("FR-NT-04: delivering the same event again creates no second notification")
		void aReplayedEventIsIgnored() {
			UserRegistered event = new UserRegistered(user, Instant.now());

			publish(event);
			awaitRows(user, 1);
			publish(event);
			// the second delivery has been handled once the idempotent handler had its
			// chance
			publish(new AreaExpired(user, UUID.randomUUID(), Instant.now()));
			awaitRows(user, 2);

			assertThat(rowsOf(user)).extracting(StoredNotification::type)
				.containsExactlyInAnyOrder("WELCOME", "AREA_EXPIRED");
		}

		@Test
		@DisplayName("ID-W4: a suspension, a ban and a lifted sanction each notify the user")
		void sanctionsNotifyTheUser() {
			Instant now = Instant.now();
			publish(new UserSanctioned(user, SanctionType.SUSPENSION, now.plus(Duration.ofDays(7)), now));
			publish(new UserSanctioned(user, SanctionType.BAN, null, now.plusSeconds(1)));
			publish(new SanctionLifted(user, now.plusSeconds(2)));

			awaitRows(user, 3);
			assertThat(rowsOf(user)).extracting(StoredNotification::type)
				.containsExactlyInAnyOrder("SANCTION_SUSPENDED", "SANCTION_BANNED", "SANCTION_LIFTED");
			assertThat(rowsOf(user).stream().filter(row -> row.type().equals("SANCTION_SUSPENDED")).findFirst())
				.hasValueSatisfying(row -> assertThat(row.body()).contains("tạm khoá đến"));
			assertThat(rowsOf(user)).allSatisfy(row -> assertThat(row.deepLink()).isEqualTo("app://account"));
		}

		@Test
		@DisplayName("AR-W2: the expiring and expired area notifications open the area screen")
		void areaNotificationsLinkToTheArea() {
			UUID area = UUID.randomUUID();
			publish(new AreaExpiring(user, area, Instant.parse("2026-12-20T00:00:00Z")));
			publish(new AreaExpired(user, area, Instant.parse("2026-12-27T00:00:00Z")));

			awaitRows(user, 2);
			assertThat(rowsOf(user)).extracting(StoredNotification::deepLink).containsOnly("app://areas/" + area);
			assertThat(rowsOf(user).stream().filter(row -> row.type().equals("AREA_EXPIRING")).findFirst())
				.hasValueSatisfying(row -> assertThat(row.body()).contains("20/12/2026"));
		}

		@Test
		@DisplayName("FR-NT-01: a message to an offline user becomes a notification that opens the conversation")
		void anOfflineRecipientIsNotified() {
			UUID conversation = UUID.randomUUID();
			publish(new MessageSent(11, conversation, UUID.randomUUID(), UUID.randomUUID(), user, "TEXT",
					"Còn hàng không bạn?", Instant.now()));

			awaitRows(user, 1);
			StoredNotification row = rowsOf(user).getFirst();
			assertThat(row.type()).isEqualTo("MESSAGE_RECEIVED");
			assertThat(row.body()).isEqualTo("Còn hàng không bạn?");
			assertThat(row.deepLink()).isEqualTo("app://conversations/" + conversation);
		}

		@Test
		@DisplayName("FR-NT-01: a message to a user who is connected creates no notification")
		void anOnlineRecipientIsNotNotified() {
			redis.opsForZSet()
				.add(RedisKeyPrefixes.CHAT_PRESENCE + user, "session-1", System.currentTimeMillis() + 60_000);
			publish(new MessageSent(12, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), user, "TEXT", "hi",
					Instant.now()));
			UUID marker = UUID.randomUUID();
			publish(new UserRegistered(marker, Instant.now()));
			awaitRows(marker, 1);

			assertThat(rowsOf(user)).isEmpty();
		}

		@Test
		@DisplayName("FR-ID-16: deleting the account deletes its notifications and preferences")
		void deletionRemovesTheUsersData() {
			publish(new UserRegistered(user, Instant.now()));
			awaitRows(user, 1);

			publish(new AccountDeleted(user, Instant.now()));

			await().atMost(ASYNC).untilAsserted(() -> {
				assertThat(rowsOf(user)).isEmpty();
				assertThat(preferences.find(user)).isEmpty();
			});
		}

	}

	@Nested
	@DisplayName("FR-NT-02: push outcome")
	class Push {

		@Test
		@DisplayName("with no FCM credentials the push is SKIPPED and the in-app notification stays")
		void aDisabledGatewayMarksThePushSkipped() {
			deviceService.register(user,
					new DeviceService.Registration("device-1", "token-" + user, "ANDROID", "1.0.0"));

			publish(new UserRegistered(user, Instant.now()));

			await().atMost(ASYNC).untilAsserted(() -> {
				assertThat(rowsOf(user)).hasSize(1);
				assertThat(rowsOf(user).getFirst().pushStatus()).isEqualTo("SKIPPED");
			});
			assertThat(devices.tokensOf(user)).containsExactly("token-" + user);
		}

		@Test
		void aUserWithoutADeviceIsSkippedAtOnce() {
			publish(new UserRegistered(user, Instant.now()));

			awaitRows(user, 1);
			assertThat(rowsOf(user).getFirst().pushStatus()).isEqualTo("SKIPPED");
		}

	}

	@Nested
	@DisplayName("UC-NT-01: the notification list")
	class Listing {

		private void givenNotifications(int count) {
			for (int i = 0; i < count; i++) {
				publish(new AreaExpired(user, UUID.randomUUID(), Instant.now().plusSeconds(i)));
			}
			awaitRows(user, count);
		}

		@Test
		void pagesNewestFirstWithACursor() {
			givenNotifications(5);

			CursorPage<NotificationView> first = queries.list(user, null, 2);
			CursorPage<NotificationView> second = queries.list(user, first.nextCursor(), 2);
			CursorPage<NotificationView> third = queries.list(user, second.nextCursor(), 2);

			assertThat(first.items()).hasSize(2);
			assertThat(first.hasMore()).isTrue();
			assertThat(second.items()).hasSize(2);
			assertThat(third.items()).hasSize(1);
			assertThat(third.hasMore()).isFalse();
			assertThat(first.items().getFirst().createdAt()).isAfterOrEqualTo(first.items().getLast().createdAt());
			assertThat(first.items()).allSatisfy(item -> assertThat(item.read()).isFalse());
		}

		@Test
		@DisplayName("FR-NT-05: reading one lowers the badge; reading all sets it to 0")
		void readingChangesTheBadge() {
			givenNotifications(3);
			UUID first = queries.list(user, null, 10).items().getFirst().id();

			queries.markRead(user, first);
			assertThat(badges.countsOf(user).notificationUnread()).isEqualTo(2);
			assertThat(queries.list(user, null, 10).items().stream().filter(NotificationView::read).count())
				.isEqualTo(1);

			assertThat(queries.markAllRead(user)).isEqualTo(2);
			assertThat(badges.countsOf(user).notificationUnread()).isZero();
			assertThat(queries.markAllRead(user)).isZero();
		}

		@Test
		void markingSomeoneElsesNotificationIsNotFound() {
			givenNotifications(1);
			UUID id = queries.list(user, null, 10).items().getFirst().id();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> queries.markRead(UUID.randomUUID(), id));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.NOTIFICATION_NOT_FOUND);
		}

		@Test
		void aBrokenCursorIsRejected() {
			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> queries.list(user, "not-a-cursor", 10));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.INVALID_CURSOR);
		}

	}

	@Nested
	@DisplayName("FR-NT-06: badges")
	class Badges {

		@Test
		@DisplayName("the chat badge sums the unread messages of every conversation, next to the notification badge")
		void countsChatAndNotifications() {
			givenUnreadMessages(user, 3);
			publish(new AreaExpired(user, UUID.randomUUID(), Instant.now()));
			awaitRows(user, 1);

			assertThat(badges.countsOf(user)).isEqualTo(new BadgeCounts(3, 1));
		}

		@Test
		void aUserWithNothingHasZeroBadges() {
			assertThat(badges.countsOf(UUID.randomUUID())).isEqualTo(new BadgeCounts(0, 0));
		}

		@Test
		void aPushToAUserWithoutSessionsDoesNotFail() {
			badges.pushTo(user);
		}

	}

	@Nested
	@DisplayName("retention (SRS ch4)")
	class Retention {

		@Test
		@DisplayName("notifications older than notification.retention_months (6) are deleted, once a day")
		void deletesNotificationsOlderThanSixMonths() {
			Instant now = Instant.now();
			publish(new AreaExpired(user, UUID.randomUUID(), now));
			awaitRows(user, 1);

			assertThat(parameters.notificationRetentionMonths()).isEqualTo(6);
			assertThat(retention.runAt(now.plus(Duration.ofDays(150)))).isZero();
			assertThat(rowsOf(user)).hasSize(1);
			assertThat(retention.runAt(now.plus(Duration.ofDays(190)))).isGreaterThanOrEqualTo(1);
			assertThat(rowsOf(user)).isEmpty();
			assertThat(retention.runAt(now.plus(Duration.ofDays(190)))).isZero();
		}

		@Test
		void theScheduledRunWorks() {
			retention.run();
		}

	}

}
