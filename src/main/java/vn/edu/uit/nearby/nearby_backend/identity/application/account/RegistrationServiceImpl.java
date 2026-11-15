package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.UserRegistered;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentChannel;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentGrant;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.PasswordPolicy;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

@Service
public class RegistrationServiceImpl implements RegistrationService {

	private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

	private final UserRepository users;

	private final UserConsentRepository consents;

	private final OtpService otp;

	private final PasswordPolicy passwordPolicy;

	private final PasswordEncoder passwordEncoder;

	private final UserSessions sessions;

	private final RateLimiter rateLimiter;

	private final IdentityParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public RegistrationServiceImpl(UserRepository users, UserConsentRepository consents, OtpService otp,
			PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, UserSessions sessions,
			RateLimiter rateLimiter, IdentityParameters parameters, ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.consents = consents;
		this.otp = otp;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.rateLimiter = rateLimiter;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public OtpChallengeIssued sendRegistrationCode(String phone) {
		return otp.send(new OtpTarget(PhoneNumber.of(phone), OtpPurpose.REGISTER));
	}

	@Override
	@Transactional
	public RegisteredAccount register(RegisterCommand command) {
		rateLimiter.acquireOrThrow(RedisKeyPrefixes.REGISTRATIONS_BY_IP + command.clientIp(),
				parameters.registrationsPerHourPerIp(), REGISTRATION_WINDOW);
		PhoneNumber phone = PhoneNumber.of(command.phone());
		requireCurrentPolicies(command.policies());
		passwordPolicy.validate(command.password(), null);
		otp.verify(command.challengeId(), new OtpTarget(phone, OtpPurpose.REGISTER), command.otp());
		requireUnregistered(phone);
		Instant now = clock.instant();
		User user = users
			.save(User.register(phone, passwordEncoder.encode(command.password()), command.displayName().strip(), now));
		consents.saveAll(consentsOf(user, command.policies(), now));
		events.publishEvent(new UserRegistered(user.getId(), now));
		return new RegisteredAccount(user.getId(), phone.value(), sessions.start(user));
	}

	private void requireCurrentPolicies(PolicyAcceptance policies) {
		if (policies.termsVersion() == null || policies.privacyVersion() == null) {
			throw new UnprocessableRequestException(ErrorCodes.CONSENT_REQUIRED,
					"You must accept the Terms of Service and the Privacy Policy.");
		}
		boolean isCurrent = Objects.equals(policies.termsVersion(), parameters.termsVersion())
				&& Objects.equals(policies.privacyVersion(), parameters.privacyVersion());
		if (!isCurrent) {
			throw new ConflictException(ErrorCodes.POLICY_VERSION_STALE,
					"The policies have been updated; please review and accept the current versions.");
		}
	}

	private void requireUnregistered(PhoneNumber phone) {
		if (users.existsByPhoneE164(phone.value())) {
			throw new ConflictException(ErrorCodes.PHONE_ALREADY_REGISTERED,
					"This phone number already has an account. Log in or reset your password.");
		}
	}

	private static List<UserConsent> consentsOf(User user, PolicyAcceptance policies, Instant now) {
		List<UserConsent> granted = new ArrayList<>();
		granted.add(grant(user, ConsentPurpose.TERMS, policies.termsVersion(), now));
		granted.add(grant(user, ConsentPurpose.PRIVACY, policies.privacyVersion(), now));
		if (policies.acceptsPushMarketing()) {
			granted.add(grant(user, ConsentPurpose.PUSH_MARKETING, policies.privacyVersion(), now));
		}
		return granted;
	}

	private static UserConsent grant(User user, ConsentPurpose purpose, String policyVersion, Instant now) {
		return UserConsent.grant(user.getId(), new ConsentGrant(purpose, policyVersion, ConsentChannel.ANDROID), now);
	}

}
