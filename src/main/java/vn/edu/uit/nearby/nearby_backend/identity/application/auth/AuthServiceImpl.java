package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import vn.edu.uit.nearby.nearby_backend.identity.DeviceSignedOut;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountIdentifier;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.StoredRefreshToken;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.RefreshTokenRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.AuthenticationFailedException;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final UserSessions sessions;

	private final LoginAttemptGuard loginAttempts;

	private final PasswordEncoder passwordEncoder;

	private final AccountAccessPolicy accessPolicy;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	// Checked when the identifier is unknown, so an unknown account and a wrong password
	// take the same time and cannot be told apart.
	private final String unknownUserPasswordHash;

	public AuthServiceImpl(UserRepository users, RefreshTokenRepository refreshTokens, UserSessions sessions,
			LoginAttemptGuard loginAttempts, PasswordEncoder passwordEncoder, AccountAccessPolicy accessPolicy,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.sessions = sessions;
		this.loginAttempts = loginAttempts;
		this.passwordEncoder = passwordEncoder;
		this.accessPolicy = accessPolicy;
		this.events = events;
		this.clock = clock;
		this.unknownUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Override
	@Transactional
	public TokenPair login(LoginCommand command) {
		AccountIdentifier identifier = AccountIdentifier.parse(command.identifier());
		LoginAttempt attempt = new LoginAttempt(identifier, command.clientIp());
		loginAttempts.ensureNotBlocked(attempt);
		User user = authenticate(attempt, command.password());
		loginAttempts.clearFailures(attempt);
		accessPolicy.admitLogin(user);
		return sessions.start(user);
	}

	@Override
	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	public TokenPair refresh(String refreshToken) {
		StoredRefreshToken stored = refreshTokens.findByTokenHash(TokenService.sha256Hex(refreshToken))
			.orElseThrow(AuthServiceImpl::invalidRefreshToken);
		Instant now = clock.instant();
		if (stored.isExpiredAt(now)) {
			throw invalidRefreshToken();
		}
		if (stored.isRevoked() || refreshTokens.revokeIfActive(stored.getId(), now, RevokeReason.ROTATED) == 0) {
			revokeStolenFamily(stored, now);
		}
		User user = users.getRequired(stored.getUserId());
		accessPolicy.ensureNotSanctioned(user);
		return sessions.rotate(user, stored.family());
	}

	@Override
	@Transactional
	public void logout(String refreshToken, @Nullable String fcmToken) {
		refreshTokens.findByTokenHash(TokenService.sha256Hex(refreshToken)).ifPresent(stored -> {
			refreshTokens.revokeIfActive(stored.getId(), clock.instant(), RevokeReason.LOGOUT);
			if (fcmToken != null) {
				events.publishEvent(new DeviceSignedOut(stored.getUserId(), fcmToken));
			}
		});
	}

	private User authenticate(LoginAttempt attempt, String password) {
		Optional<User> user = findLoginAccount(attempt.identifier());
		String passwordHash = user.map(User::getPasswordHash).orElse(unknownUserPasswordHash);
		boolean isPasswordCorrect = passwordEncoder.matches(password, passwordHash);
		if (user.isEmpty() || !isPasswordCorrect) {
			loginAttempts.recordFailure(attempt);
			throw new AuthenticationFailedException(ErrorCodes.INVALID_CREDENTIALS,
					"Phone number, email or password is incorrect.");
		}
		return user.get();
	}

	private Optional<User> findLoginAccount(AccountIdentifier identifier) {
		return switch (identifier) {
			case PhoneNumber phone -> users.findByPhoneE164(phone.value());
			case EmailAddress email -> users.findByEmail(email.value()).filter(User::hasVerifiedEmail);
		};
	}

	private void revokeStolenFamily(StoredRefreshToken stored, Instant now) {
		refreshTokens.revokeFamily(stored.getFamilyId(), now, RevokeReason.REUSE_DETECTED);
		log.warn("Security event: reuse of rotated refresh token detected for user {}, session {} revoked",
				stored.getUserId(), stored.getFamilyId());
		throw new AuthenticationFailedException(ErrorCodes.REFRESH_TOKEN_REUSED,
				"This session is no longer valid, please log in again.");
	}

	private static AuthenticationFailedException invalidRefreshToken() {
		return new AuthenticationFailedException(ErrorCodes.INVALID_REFRESH_TOKEN,
				"The refresh token is invalid or expired, please log in again.");
	}

}
