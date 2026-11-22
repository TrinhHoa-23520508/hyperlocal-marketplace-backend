package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.AvatarDiscarded;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

/**
 * Anonymises accounts whose deletion grace period is over (FR-ID-16, UC-SY-12).
 */
@Slf4j
@Component
public class AccountAnonymisationJob {

	private static final String DAILY_AT_04_00 = "0 0 4 * * *";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final IdentityParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public AccountAnonymisationJob(UserRepository users, PasswordEncoder passwordEncoder, IdentityParameters parameters,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = DAILY_AT_04_00, zone = "UTC")
	public void anonymiseDueAccounts() {
		Instant now = clock.instant();
		List<User> due = users.findByStatusAndDeletionRequestedAtLessThanEqual(AccountStatus.PENDING_DELETION,
				now.minus(parameters.accountDeletionGrace()));
		for (User user : due) {
			user.anonymise(passwordEncoder.encode(UUID.randomUUID().toString()), now)
				.ifPresent(avatarKey -> events.publishEvent(new AvatarDiscarded(avatarKey)));
			events.publishEvent(new AccountDeleted(user.getId(), now));
		}
		log.info("Anonymised {} deleted accounts", due.size());
	}

}
