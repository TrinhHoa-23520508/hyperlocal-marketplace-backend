package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatParameters;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;

/**
 * Retention (SRS ch4): notifications are kept {@code notification.retention_months}
 * months (6) and then deleted, once a day. Safe to run twice.
 */
@Slf4j
@Component
public class NotificationRetentionJob {

	static final String DAILY_AT_03_00 = "0 0 3 * * *";

	static final String VIETNAM_ZONE = "Asia/Ho_Chi_Minh";

	private final NotificationRepository notifications;

	private final ChatParameters parameters;

	private final Clock clock;

	NotificationRetentionJob(NotificationRepository notifications, ChatParameters parameters, Clock clock) {
		this.notifications = notifications;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = DAILY_AT_03_00, zone = VIETNAM_ZONE)
	public void run() {
		runAt(clock.instant());
	}

	/**
	 * @return how many notifications were deleted
	 */
	@Transactional
	public int runAt(Instant now) {
		Instant cutoff = now.atZone(ZoneId.of(VIETNAM_ZONE))
			.minusMonths(parameters.notificationRetentionMonths())
			.toInstant();
		int deleted = notifications.deleteCreatedBefore(cutoff);
		if (deleted > 0) {
			log.info("Deleted {} notifications older than {}", deleted, cutoff);
		}
		return deleted;
	}

}
