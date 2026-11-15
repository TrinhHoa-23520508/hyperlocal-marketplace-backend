package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionCancelled;
import vn.edu.uit.nearby.nearby_backend.identity.DeviceSignedOut;
import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.application.admin.AuditLogService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.StoredRefreshToken;
import vn.edu.uit.nearby.nearby_backend.identity.domain.StoredRefreshToken.RefreshTokenFamily;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.RefreshTokenRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.AuthenticationFailedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTests {

	private static final String PASSWORD = "Secret123";

	private static final String CLIENT_IP = "203.0.113.5";

	private static final String FCM_TOKEN = "fcm-token-of-this-phone";

	private static final String DUMMY_HASH = "{bcrypt}dummy";

	private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";

	private static final PhoneNumber PHONE = PhoneNumber.of(UserFixtures.PHONE);

	private static final TokenPair TOKENS = new TokenPair("access", NOW.plus(Duration.ofMinutes(15)), "refresh",
			NOW.plus(Duration.ofDays(30)));

	@Mock
	UserRepository users;

	@Mock
	RefreshTokenRepository refreshTokens;

	@Mock
	UserSessions sessions;

	@Mock
	LoginAttemptGuard loginAttempts;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	AuditLogService auditLog;

	@Mock
	ApplicationEventPublisher events;

	private AuthServiceImpl authService;

	@BeforeEach
	void createService() {
		when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		authService = new AuthServiceImpl(users, refreshTokens, sessions, loginAttempts, passwordEncoder,
				new AccountAccessPolicy(auditLog, events, clock), events, clock);
	}

	@Test
	void correctPasswordForThePhoneNumberStartsASessionAndClearsFailures() {
		User user = UserFixtures.activeUser();
		when(users.findByPhoneE164(UserFixtures.PHONE)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, UserFixtures.PASSWORD_HASH)).thenReturn(true);
		when(sessions.start(user)).thenReturn(TOKENS);

		TokenPair tokens = authService.login(new LoginCommand("0912 345 678", PASSWORD, CLIENT_IP));

		assertThat(tokens).isEqualTo(TOKENS);
		verify(loginAttempts).clearFailures(new LoginAttempt(PHONE, CLIENT_IP));
	}

	@Test
	void verifiedEmailCanBeUsedToLogIn() {
		User user = UserFixtures.userWithVerifiedEmail();
		when(users.findByEmail(UserFixtures.EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, UserFixtures.PASSWORD_HASH)).thenReturn(true);
		when(sessions.start(user)).thenReturn(TOKENS);

		assertThat(authService.login(new LoginCommand(" An.Nguyen@Example.com", PASSWORD, CLIENT_IP)))
			.isEqualTo(TOKENS);
	}

	@Test
	void unverifiedEmailCannotBeUsedToLogInEvenWithTheRightPassword() {
		when(users.findByEmail(UserFixtures.EMAIL)).thenReturn(Optional.of(UserFixtures.userWithUnverifiedEmail()));
		when(passwordEncoder.matches(PASSWORD, DUMMY_HASH)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.EMAIL, PASSWORD, CLIENT_IP)))
			.isInstanceOf(AuthenticationFailedException.class)
			.extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("INVALID_CREDENTIALS");
		verify(loginAttempts).recordFailure(new LoginAttempt(EmailAddress.of(UserFixtures.EMAIL), CLIENT_IP));
		verifyNoInteractions(sessions);
	}

	@Test
	void wrongPasswordIsRecordedAsAFailedAttempt() {
		when(users.findByPhoneE164(UserFixtures.PHONE)).thenReturn(Optional.of(UserFixtures.activeUser()));
		when(passwordEncoder.matches(PASSWORD, UserFixtures.PASSWORD_HASH)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP)))
			.isInstanceOf(AuthenticationFailedException.class)
			.extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("INVALID_CREDENTIALS");
		verify(loginAttempts).recordFailure(new LoginAttempt(PHONE, CLIENT_IP));
		verifyNoInteractions(sessions);
	}

	@Test
	void unknownPhoneStillChecksAPasswordAndFailsTheSameWay() {
		when(users.findByPhoneE164(UserFixtures.PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP)))
			.isInstanceOf(AuthenticationFailedException.class)
			.hasMessage("Phone number, email or password is incorrect.");
		verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
		verify(loginAttempts).recordFailure(new LoginAttempt(PHONE, CLIENT_IP));
	}

	@Test
	void blockedIdentifierIsRejectedBeforeThePasswordIsChecked() {
		doThrow(new RateLimitExceededException("LOGIN_LOCKED", "locked", Duration.ofMinutes(10))).when(loginAttempts)
			.ensureNotBlocked(new LoginAttempt(PHONE, CLIENT_IP));

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP)))
			.isInstanceOf(RateLimitExceededException.class);
		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void bannedAccountIsToldWhyInsteadOfGettingTokens() {
		User user = givenLoginAccount(UserFixtures.bannedUser());
		when(auditLog.latestSanctionReason(user.getId())).thenReturn(Optional.of("Scam reports"));

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP)))
			.isInstanceOf(OperationNotAllowedException.class)
			.satisfies(ex -> {
				assertThat(codeOf(ex)).isEqualTo("ACCOUNT_BANNED");
				assertThat(((BusinessException) ex).getProperties())
					.containsExactlyEntriesOf(Map.of("reason", "Scam reports"));
			});
		verifyNoInteractions(sessions);
	}

	@Test
	void suspendedAccountIsToldUntilWhen() {
		Instant until = NOW.plus(Duration.ofDays(7));
		User user = givenLoginAccount(UserFixtures.suspendedUntil(until));
		when(auditLog.latestSanctionReason(user.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP)))
			.satisfies(ex -> {
				assertThat(codeOf(ex)).isEqualTo("ACCOUNT_SUSPENDED");
				assertThat(((BusinessException) ex).getProperties())
					.containsExactlyEntriesOf(Map.of("suspendedUntil", until));
			});
		verifyNoInteractions(sessions, events);
	}

	@Test
	void loginAfterTheSuspensionEndedLiftsItAndStartsASession() {
		User user = givenLoginAccount(UserFixtures.suspendedUntil(NOW));
		when(sessions.start(user)).thenReturn(TOKENS);

		assertThat(authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP))).isEqualTo(TOKENS);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		verify(events).publishEvent(new SanctionLifted(user.getId(), NOW));
	}

	@Test
	void loginDuringTheDeletionGracePeriodCancelsTheDeletion() {
		User user = UserFixtures.activeUser();
		user.requestDeletion(NOW.minus(Duration.ofDays(2)));
		givenLoginAccount(user);
		when(sessions.start(user)).thenReturn(TOKENS);

		assertThat(authService.login(new LoginCommand(UserFixtures.PHONE, PASSWORD, CLIENT_IP))).isEqualTo(TOKENS);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		verify(events).publishEvent(new AccountDeletionCancelled(user.getId(), NOW));
	}

	@Test
	void malformedPhoneIsRejectedBeforeAnyLookup() {
		assertThatThrownBy(() -> authService.login(new LoginCommand("12345", PASSWORD, CLIENT_IP)))
			.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(users, loginAttempts);
	}

	@Test
	void validRefreshTokenIsRevokedAndRotatedWithinItsFamily() {
		User user = UserFixtures.activeUser();
		StoredRefreshToken stored = storedToken(user);
		when(refreshTokens.revokeIfActive(stored.getId(), NOW, RevokeReason.ROTATED)).thenReturn(1);
		when(users.getRequired(stored.getUserId())).thenReturn(user);
		when(sessions.rotate(user, stored.family())).thenReturn(TOKENS);

		assertThat(authService.refresh(RAW_REFRESH_TOKEN)).isEqualTo(TOKENS);
		verify(refreshTokens, never()).revokeFamily(any(), any(), any());
	}

	@Test
	void unknownRefreshTokenIsRejected() {
		when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
			.isInstanceOf(AuthenticationFailedException.class)
			.extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("INVALID_REFRESH_TOKEN");
	}

	@Test
	void expiredRefreshTokenRequiresANewLoginWithoutRevokingTheFamily() {
		StoredRefreshToken stored = StoredRefreshToken.issue(RefreshTokenFamily.startFor(UUID.randomUUID()),
				TokenService.sha256Hex(RAW_REFRESH_TOKEN), NOW, NOW.minus(Duration.ofDays(30)));
		when(refreshTokens.findByTokenHash(TokenService.sha256Hex(RAW_REFRESH_TOKEN))).thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN)).extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("INVALID_REFRESH_TOKEN");
		verify(refreshTokens, never()).revokeFamily(any(), any(), any());
	}

	@Test
	void reusingARotatedTokenRevokesTheWholeFamily() {
		StoredRefreshToken stored = storedToken(UserFixtures.activeUser());
		ReflectionTestUtils.setField(stored, "revokedAt", NOW.minus(Duration.ofMinutes(1)));

		assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
			.isInstanceOf(AuthenticationFailedException.class)
			.extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("REFRESH_TOKEN_REUSED");
		verify(refreshTokens).revokeFamily(stored.getFamilyId(), NOW, RevokeReason.REUSE_DETECTED);
		verifyNoInteractions(sessions);
	}

	@Test
	void losingAConcurrentRefreshIsTreatedAsReuse() {
		StoredRefreshToken stored = storedToken(UserFixtures.activeUser());
		when(refreshTokens.revokeIfActive(stored.getId(), NOW, RevokeReason.ROTATED)).thenReturn(0);

		assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN)).extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("REFRESH_TOKEN_REUSED");
		verify(refreshTokens).revokeFamily(stored.getFamilyId(), NOW, RevokeReason.REUSE_DETECTED);
	}

	@Test
	void bannedAccountCannotRefresh() {
		User user = UserFixtures.bannedUser();
		StoredRefreshToken stored = storedToken(user);
		when(refreshTokens.revokeIfActive(stored.getId(), NOW, RevokeReason.ROTATED)).thenReturn(1);
		when(users.getRequired(stored.getUserId())).thenReturn(user);

		assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN)).extracting(AuthServiceImplTests::codeOf)
			.isEqualTo("ACCOUNT_BANNED");
	}

	@Test
	void logoutRevokesTheRefreshToken() {
		StoredRefreshToken stored = storedToken(UserFixtures.activeUser());

		authService.logout(RAW_REFRESH_TOKEN, null);

		verify(refreshTokens).revokeIfActive(stored.getId(), NOW, RevokeReason.LOGOUT);
		verifyNoInteractions(events);
	}

	@Test
	void logoutWithThePushTokenStopsPushesToThatDevice() {
		User user = UserFixtures.activeUser();
		storedToken(user);

		authService.logout(RAW_REFRESH_TOKEN, FCM_TOKEN);

		verify(events).publishEvent(new DeviceSignedOut(user.getId(), FCM_TOKEN));
	}

	@Test
	void logoutWithAnUnknownTokenIsAlreadyLoggedOut() {
		when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

		authService.logout(RAW_REFRESH_TOKEN, FCM_TOKEN);

		verify(refreshTokens, never()).revokeIfActive(any(), any(), any());
		verifyNoInteractions(events);
	}

	private User givenLoginAccount(User user) {
		when(users.findByPhoneE164(UserFixtures.PHONE)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, UserFixtures.PASSWORD_HASH)).thenReturn(true);
		return user;
	}

	private StoredRefreshToken storedToken(User user) {
		StoredRefreshToken stored = StoredRefreshToken.issue(RefreshTokenFamily.startFor(user.getId()),
				TokenService.sha256Hex(RAW_REFRESH_TOKEN), NOW.plus(Duration.ofDays(29)),
				NOW.minus(Duration.ofDays(1)));
		when(refreshTokens.findByTokenHash(TokenService.sha256Hex(RAW_REFRESH_TOKEN))).thenReturn(Optional.of(stored));
		return stored;
	}

	private static String codeOf(Throwable ex) {
		return ((BusinessException) ex).getCode();
	}

}
