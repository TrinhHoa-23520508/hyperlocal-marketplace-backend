package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.PasswordPolicy;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Password change by a logged-in user (FR-ID-08): the current session stays, every other
 * session is logged out.
 */
@Service
public class PasswordChangeService {

	private final UserRepository users;

	private final PasswordPolicy passwordPolicy;

	private final PasswordEncoder passwordEncoder;

	private final UserSessions sessions;

	private final Clock clock;

	public PasswordChangeService(UserRepository users, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
			UserSessions sessions, Clock clock) {
		this.users = users;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.clock = clock;
	}

	/**
	 * @throws InvalidRequestException {@code CURRENT_PASSWORD_INVALID} or
	 * {@code PASSWORD_REUSED}
	 * @throws WeakPasswordException when the new password breaks a rule
	 */
	@Transactional
	public void changePassword(ChangePasswordCommand command) {
		User user = users.getRequired(command.userId());
		if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
			throw new InvalidRequestException(ErrorCodes.CURRENT_PASSWORD_INVALID,
					"The current password is incorrect.");
		}
		if (passwordEncoder.matches(command.newPassword(), user.getPasswordHash())) {
			throw new InvalidRequestException(ErrorCodes.PASSWORD_REUSED,
					"The new password must differ from the current one.");
		}
		passwordPolicy.validate(command.newPassword(), user.emailAddress().orElse(null));
		user.changePassword(passwordEncoder.encode(command.newPassword()), clock.instant());
		endOtherSessions(user.getId(), command.currentSessionId());
	}

	private void endOtherSessions(UUID userId, @Nullable UUID currentSessionId) {
		if (currentSessionId == null) {
			sessions.endAll(userId, RevokeReason.PASSWORD_CHANGED);
			return;
		}
		sessions.endAllExcept(userId, currentSessionId, RevokeReason.PASSWORD_CHANGED);
	}

	/**
	 * @param currentSessionId session of the access token used for the request; when
	 * unknown every session is logged out
	 */
	public record ChangePasswordCommand(UUID userId, @Nullable UUID currentSessionId, String currentPassword,
			String newPassword) {
	}

}
