package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpiring;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;

/**
 * UC-SY-05, daily at 09:00: a VERIFIED area with at most
 * {@code area.renewal_reminder_days} left becomes EXPIRING and {@link AreaExpiring} is
 * published once per validity period, so Notification can send the renewal reminder.
 */
@Component
public class AreaRenewalReminderJob {

	private final UserAreaRepository areas;

	private final AreaParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AreaRenewalReminderJob(UserAreaRepository areas, AreaParameters parameters, ApplicationEventPublisher events,
			Clock clock) {
		this.areas = areas;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = AreaJobSchedules.DAILY_AT_09_00, zone = AreaJobSchedules.VIETNAM_ZONE)
	public void run() {
		runAt(clock.instant());
	}

	@Transactional
	public int runAt(Instant now) {
		Instant reminderLimit = now.plus(parameters.renewalReminderWindow());
		int reminded = 0;
		List<UserArea> due;
		do {
			due = areas.findDueForRenewalReminder(reminderLimit, Limit.of(AreaJobSchedules.BATCH_SIZE));
			due.forEach(area -> remind(area, now));
			areas.flush();
			reminded += due.size();
		}
		while (due.size() == AreaJobSchedules.BATCH_SIZE);
		return reminded;
	}

	private void remind(UserArea area, Instant now) {
		area.markExpiring(now);
		events.publishEvent(new AreaExpiring(area.getUserId(), area.getId(), area.getExpiresAt()));
	}

}
