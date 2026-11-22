package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpired;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;

/**
 * UC-SY-05 hourly scan: every area whose {@code expires_at} has passed becomes EXPIRED
 * and {@link AreaExpired} is published, so an area is EXPIRED at most an hour after its
 * expiry (FR-AR-03 acceptance criterion).
 */
@Component
public class AreaExpiryJob {

	private final UserAreaRepository areas;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AreaExpiryJob(UserAreaRepository areas, ApplicationEventPublisher events, Clock clock) {
		this.areas = areas;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = AreaJobSchedules.HOURLY, zone = AreaJobSchedules.VIETNAM_ZONE)
	public void run() {
		runAt(clock.instant());
	}

	@Transactional
	public int runAt(Instant now) {
		int expired = 0;
		List<UserArea> due;
		do {
			due = areas.findReachedExpiry(now, Limit.of(AreaJobSchedules.BATCH_SIZE));
			due.forEach(area -> expire(area, now));
			areas.flush();
			expired += due.size();
		}
		while (due.size() == AreaJobSchedules.BATCH_SIZE);
		return expired;
	}

	private void expire(UserArea area, Instant now) {
		area.expire(now);
		events.publishEvent(new AreaExpired(area.getUserId(), area.getId(), now));
	}

}
