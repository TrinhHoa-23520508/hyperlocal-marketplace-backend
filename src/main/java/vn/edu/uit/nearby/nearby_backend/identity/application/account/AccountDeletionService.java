package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionBlocker;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionRequested;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * Account deletion requested by its owner (FR-ID-16): the account waits in
 * PENDING_DELETION for the grace period, during which logging in cancels the request.
 */
@Service
public class AccountDeletionService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final List<AccountDeletionBlocker> blockers;

	private final UserSessions sessions;

	private final IdentityParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public AccountDeletionService(UserRepository users, PasswordEncoder passwordEncoder,
			List<AccountDeletionBlocker> blockers, UserSessions sessions, IdentityParameters parameters,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.blockers = List.copyOf(blockers);
		this.sessions = sessions;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @return when the account will be anonymised
	 * @throws OperationNotAllowedException {@code CANNOT_DELETE_ADMIN}
	 * @throws InvalidRequestException {@code CURRENT_PASSWORD_INVALID}
	 * @throws ConflictException {@code ACCOUNT_DELETION_BLOCKED} listing the open
	 * obligations in {@code blockers}, or {@code ACCOUNT_STATUS_CONFLICT} unless the
	 * account is ACTIVE
	 */
	@Transactional
	public Instant requestDeletion(UUID userId, String password) {
		User user = users.getRequired(userId);
		if (user.isAdmin()) {
			throw new OperationNotAllowedException(ErrorCodes.CANNOT_DELETE_ADMIN,
					"Administrator accounts cannot be deleted.");
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new InvalidRequestException(ErrorCodes.CURRENT_PASSWORD_INVALID, "The password is incorrect.");
		}
		requireNoPendingObligations(userId);
		Instant now = clock.instant();
		user.requestDeletion(now);
		sessions.endAll(userId, RevokeReason.ACCOUNT_DELETION);
		Instant deleteAfter = now.plus(parameters.accountDeletionGrace());
		events.publishEvent(new AccountDeletionRequested(userId, deleteAfter));
		return deleteAfter;
	}

	private void requireNoPendingObligations(UUID userId) {
		List<String> obligations = blockers.stream()
			.flatMap(blocker -> blocker.pendingObligations(userId).stream())
			.toList();
		if (!obligations.isEmpty()) {
			throw new ConflictException(ErrorCodes.ACCOUNT_DELETION_BLOCKED,
					"Finish or settle these first, then try again.", Map.of("blockers", obligations));
		}
	}

}
