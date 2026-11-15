package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpService;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountIdentifier;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.PasswordPolicy;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

	private final UserRepository users;

	private final OtpService otp;

	private final PasswordPolicy passwordPolicy;

	private final PasswordEncoder passwordEncoder;

	private final OneTimeTokenStore tokens;

	private final AccountEmailDispatcher emails;

	private final UserSessions sessions;

	private final IdentityParameters parameters;

	private final Clock clock;

	public PasswordResetServiceImpl(UserRepository users, OtpService otp, PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder, OneTimeTokenStore tokens, AccountEmailDispatcher emails,
			UserSessions sessions, IdentityParameters parameters, Clock clock) {
		this.users = users;
		this.otp = otp;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.emails = emails;
		this.sessions = sessions;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OtpChallengeIssued> requestReset(String identifier) {
		return switch (AccountIdentifier.parse(identifier)) {
			case PhoneNumber phone -> Optional.of(sendResetCode(phone));
			case EmailAddress email -> {
				sendResetLink(email);
				yield Optional.empty();
			}
		};
	}

	@Override
	public PasswordResetToken verifyResetCode(UUID challengeId, String phone, String code) {
		PhoneNumber phoneNumber = PhoneNumber.of(phone);
		otp.verify(challengeId, new OtpTarget(phoneNumber, OtpPurpose.RESET_PASSWORD), code);
		User user = users.findByPhoneE164(phoneNumber.value()).orElseThrow(PasswordResetServiceImpl::invalidToken);
		Duration ttl = parameters.passwordResetTtl();
		String token = tokens.issue(OneTimeTokenPurpose.PASSWORD_RESET,
				new OneTimeTokenSubject(user.getId(), phoneNumber.value()), ttl);
		return new PasswordResetToken(token, clock.instant().plus(ttl));
	}

	@Override
	@Transactional
	public void resetPassword(String token, String newPassword) {
		OneTimeTokenSubject subject = tokens.find(OneTimeTokenPurpose.PASSWORD_RESET, token)
			.orElseThrow(PasswordResetServiceImpl::invalidToken);
		User user = users.findById(subject.userId()).orElseThrow(PasswordResetServiceImpl::invalidToken);
		passwordPolicy.validate(newPassword, user.emailAddress().orElse(null));
		if (tokens.consume(OneTimeTokenPurpose.PASSWORD_RESET, token).isEmpty()) {
			throw invalidToken();
		}
		Instant now = clock.instant();
		user.changePassword(passwordEncoder.encode(newPassword), now);
		verifyEmailWhenResetByLink(user, subject, now);
		sessions.endAll(user.getId(), RevokeReason.PASSWORD_CHANGED);
	}

	private OtpChallengeIssued sendResetCode(PhoneNumber phone) {
		OtpTarget target = new OtpTarget(phone, OtpPurpose.RESET_PASSWORD);
		return users.existsByPhoneE164(phone.value()) ? otp.send(target) : otp.sendNothing(target);
	}

	private void sendResetLink(EmailAddress email) {
		users.findByEmail(email.value())
			.filter(User::hasVerifiedEmail)
			.ifPresent(user -> emails.sendPasswordReset(user, email));
	}

	// Opening a link sent to the address proves the user owns it (FR-ID-07).
	private static void verifyEmailWhenResetByLink(User user, OneTimeTokenSubject subject, Instant now) {
		if (AccountIdentifier.parse(subject.boundTo()) instanceof EmailAddress email) {
			user.verifyEmail(email, now);
		}
	}

	private static UnprocessableRequestException invalidToken() {
		return new UnprocessableRequestException(ErrorCodes.RESET_TOKEN_INVALID,
				"The password reset token is invalid or has expired; start the reset again.");
	}

}
