package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionCancelled;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.application.admin.AuditLogService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.LoginReactivation;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * Decides whether an authenticated account may get tokens (FR-ID-19). A sanctioned
 * account is told why and until when instead of the generic login error.
 */
@Component
public class AccountAccessPolicy {

	private final AuditLogService auditLog;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public AccountAccessPolicy(AuditLogService auditLog, ApplicationEventPublisher events, Clock clock) {
		this.auditLog = auditLog;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Ends an expired suspension or a pending deletion, then rejects a sanctioned
	 * account.
	 * @throws OperationNotAllowedException {@code ACCOUNT_SUSPENDED} or
	 * {@code ACCOUNT_BANNED}
	 */
	public void admitLogin(User user) {
		Instant now = clock.instant();
		user.reactivateOnLogin(now).ifPresent(reactivation -> publish(user, reactivation, now));
		ensureNotSanctioned(user);
	}

	public void ensureNotSanctioned(User user) {
		if (!user.isSanctioned()) {
			return;
		}
		Map<String, Object> details = new HashMap<>();
		auditLog.latestSanctionReason(user.getId()).ifPresent(reason -> details.put("reason", reason));
		if (user.getStatus() == AccountStatus.SUSPENDED) {
			details.put("suspendedUntil", user.getSuspendedUntil());
			throw new OperationNotAllowedException(ErrorCodes.ACCOUNT_SUSPENDED, "This account is suspended.", details);
		}
		throw new OperationNotAllowedException(ErrorCodes.ACCOUNT_BANNED, "This account is banned.", details);
	}

	private void publish(User user, LoginReactivation reactivation, Instant now) {
		events.publishEvent(switch (reactivation) {
			case SUSPENSION_ENDED -> new SanctionLifted(user.getId(), now);
			case DELETION_CANCELLED -> new AccountDeletionCancelled(user.getId(), now);
		});
	}

}
