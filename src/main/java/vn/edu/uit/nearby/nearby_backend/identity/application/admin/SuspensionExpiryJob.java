package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

/**
 * Makes suspended accounts ACTIVE again once the suspension is over (FR-ID-19). Logging
 * in does the same right away, so the delay only affects tokens and listings.
 */
@Slf4j
@Component
public class SuspensionExpiryJob {

	private static final String EVERY_FIVE_MINUTES = "0 */5 * * * *";

	private final UserRepository users;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public SuspensionExpiryJob(UserRepository users, ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = EVERY_FIVE_MINUTES, zone = "UTC")
	public void liftExpiredSuspensions() {
		Instant now = clock.instant();
		List<User> expired = users.findByStatusAndSuspendedUntilLessThanEqual(AccountStatus.SUSPENDED, now);
		for (User user : expired) {
			user.liftSanction(now);
			events.publishEvent(new SanctionLifted(user.getId(), now));
		}
		if (!expired.isEmpty()) {
			log.info("Lifted {} expired suspensions", expired.size());
		}
	}

}
