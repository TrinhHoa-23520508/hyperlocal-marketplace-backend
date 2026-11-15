package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.uit.nearby.nearby_backend.identity.UserRegistered;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.TokenPair;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTests {

	private static final String CLIENT_IP = "203.0.113.7";

	private static final UUID CHALLENGE_ID = UUID.randomUUID();

	private static final OtpTarget REGISTER = new OtpTarget(PhoneNumber.of(UserFixtures.PHONE), OtpPurpose.REGISTER);

	private static final TokenPair TOKENS = new TokenPair("access", NOW.plus(Duration.ofMinutes(15)), "refresh",
			NOW.plus(Duration.ofDays(30)));

	@Mock
	UserRepository users;

	@Mock
	UserConsentRepository consents;

	@Mock
	OtpService otp;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	UserSessions sessions;

	@Mock
	RateLimiter rateLimiter;

	@Mock
	ApplicationEventPublisher events;

	private RegistrationServiceImpl service() {
		return new RegistrationServiceImpl(users, consents, otp, PasswordPolicyFixture.standard(), passwordEncoder,
				sessions, rateLimiter, IdentityParametersFixture.seeded(), events, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void registrationCodeIsSentToTheNormalisedNumber() {
		service().sendRegistrationCode("091 234 5678");

		verify(otp).send(REGISTER);
	}

	@Test
	void malformedNumberGetsNoCode() {
		assertThatThrownBy(() -> service().sendRegistrationCode("12345")).isInstanceOf(InvalidRequestException.class)
			.extracting(RegistrationServiceImplTests::codeOf)
			.isEqualTo("INVALID_PHONE");
		verifyNoInteractions(otp);
	}

	@Test
	void registrationCreatesAnActiveVerifiedAccountWithConsentsAndASession() {
		givenNewAccountIsSaved();
		when(sessions.start(any(User.class))).thenReturn(TOKENS);

		RegisteredAccount account = service().register(command(new PolicyAcceptance("1.0", "1.0", false)));

		User saved = savedUser();
		assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(saved.hasVerifiedPhone()).isTrue();
		assertThat(saved.getDisplayName()).isEqualTo("An Nguyen");
		assertThat(saved.getPasswordHash()).isEqualTo("hash");
		assertThat(account.phone()).isEqualTo(UserFixtures.PHONE);
		assertThat(account.tokens()).isEqualTo(TOKENS);
		assertThat(savedConsents()).extracting(UserConsent::getPurpose)
			.containsExactly(ConsentPurpose.TERMS, ConsentPurpose.PRIVACY);
		verify(otp).verify(CHALLENGE_ID, REGISTER, "123456");
		verify(events).publishEvent(new UserRegistered(saved.getId(), NOW));
		verify(rateLimiter).acquireOrThrow("registrations:" + CLIENT_IP, 10, Duration.ofHours(1));
	}

	@Test
	void pushMarketingConsentIsRecordedOnlyWhenAccepted() {
		givenNewAccountIsSaved();

		service().register(command(new PolicyAcceptance("1.0", "1.0", true)));

		assertThat(savedConsents()).extracting(UserConsent::getPurpose)
			.containsExactly(ConsentPurpose.TERMS, ConsentPurpose.PRIVACY, ConsentPurpose.PUSH_MARKETING);
	}

	@Test
	void missingConsentIsRejectedBeforeTheCodeIsUsed() {
		assertThatThrownBy(() -> service().register(command(new PolicyAcceptance("1.0", null, false))))
			.isInstanceOf(UnprocessableRequestException.class)
			.extracting(RegistrationServiceImplTests::codeOf)
			.isEqualTo("CONSENT_REQUIRED");
		verifyNoInteractions(otp, users);
	}

	@Test
	void consentToAnOutdatedPolicyVersionIsRejected() {
		assertThatThrownBy(() -> service().register(command(new PolicyAcceptance("0.9", "1.0", false))))
			.isInstanceOf(ConflictException.class)
			.extracting(RegistrationServiceImplTests::codeOf)
			.isEqualTo("POLICY_VERSION_STALE");
		verifyNoInteractions(otp);
	}

	@Test
	void weakPasswordIsRejectedBeforeTheCodeIsUsed() {
		RegisterCommand weak = new RegisterCommand(UserFixtures.PHONE, CHALLENGE_ID, "123456", "short", "An",
				new PolicyAcceptance("1.0", "1.0", false), CLIENT_IP);

		assertThatThrownBy(() -> service().register(weak)).isInstanceOf(WeakPasswordException.class);
		verifyNoInteractions(otp);
	}

	@Test
	void wrongCodeCreatesNoAccount() {
		doThrow(new UnprocessableRequestException("OTP_INVALID", "wrong")).when(otp)
			.verify(CHALLENGE_ID, REGISTER, "123456");

		assertThatThrownBy(() -> service().register(command(new PolicyAcceptance("1.0", "1.0", false))))
			.extracting(RegistrationServiceImplTests::codeOf)
			.isEqualTo("OTP_INVALID");
		verify(users, never()).save(any());
	}

	@Test
	void registeredNumberIsReportedOnlyAfterTheCodeIsVerified() {
		when(users.existsByPhoneE164(UserFixtures.PHONE)).thenReturn(true);

		assertThatThrownBy(() -> service().register(command(new PolicyAcceptance("1.0", "1.0", false))))
			.isInstanceOf(ConflictException.class)
			.extracting(RegistrationServiceImplTests::codeOf)
			.isEqualTo("PHONE_ALREADY_REGISTERED");
		verify(otp).verify(CHALLENGE_ID, REGISTER, "123456");
		verify(users, never()).save(any());
	}

	@Test
	void tooManyRegistrationsFromOneIpAreRejectedFirst() {
		doThrow(new RateLimitExceededException(Duration.ofMinutes(30))).when(rateLimiter)
			.acquireOrThrow(anyString(), anyInt(), any());

		assertThatThrownBy(() -> service().register(command(new PolicyAcceptance("1.0", "1.0", false))))
			.isInstanceOf(RateLimitExceededException.class);
		verifyNoInteractions(otp, users);
	}

	private void givenNewAccountIsSaved() {
		when(users.existsByPhoneE164(UserFixtures.PHONE)).thenReturn(false);
		when(passwordEncoder.encode("Secret123")).thenReturn("hash");
		when(users.save(any(User.class))).then(AdditionalAnswers.returnsFirstArg());
	}

	private static RegisterCommand command(PolicyAcceptance policies) {
		return new RegisterCommand("0912345678", CHALLENGE_ID, "123456", "Secret123", "  An Nguyen ", policies,
				CLIENT_IP);
	}

	private User savedUser() {
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		return saved.getValue();
	}

	@SuppressWarnings("unchecked")
	private List<UserConsent> savedConsents() {
		ArgumentCaptor<List<UserConsent>> saved = ArgumentCaptor.forClass(List.class);
		verify(consents).saveAll(saved.capture());
		return saved.getValue();
	}

	private static String codeOf(Throwable ex) {
		return ((BusinessException) ex).getCode();
	}

}
