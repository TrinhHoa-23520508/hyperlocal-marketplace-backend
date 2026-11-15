package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;
import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpService;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTests {

	private static final String TOKEN = "reset-token";

	private static final UUID CHALLENGE_ID = UUID.randomUUID();

	private static final OtpTarget RESET = new OtpTarget(PhoneNumber.of(UserFixtures.PHONE), OtpPurpose.RESET_PASSWORD);

	private static final OtpChallengeIssued ISSUED = new OtpChallengeIssued(CHALLENGE_ID, Duration.ofMinutes(5),
			Duration.ofMinutes(1));

	@Mock
	UserRepository users;

	@Mock
	OtpService otp;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	OneTimeTokenStore tokens;

	@Mock
	AccountEmailDispatcher emails;

	@Mock
	UserSessions sessions;

	private PasswordResetServiceImpl service() {
		return new PasswordResetServiceImpl(users, otp, PasswordPolicyFixture.standard(), passwordEncoder, tokens,
				emails, sessions, IdentityParametersFixture.seeded(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void registeredNumberReceivesAResetCode() {
		when(users.existsByPhoneE164(UserFixtures.PHONE)).thenReturn(true);
		when(otp.send(RESET)).thenReturn(ISSUED);

		assertThat(service().requestReset("0912345678")).contains(ISSUED);
		verify(otp, never()).sendNothing(any());
	}

	@Test
	void unknownNumberGetsTheSameAnswerButNoSms() {
		when(users.existsByPhoneE164(UserFixtures.PHONE)).thenReturn(false);
		when(otp.sendNothing(RESET)).thenReturn(ISSUED);

		assertThat(service().requestReset("0912345678")).contains(ISSUED);
		verify(otp, never()).send(any());
	}

	@Test
	void verifiedEmailReceivesAResetLink() {
		User user = UserFixtures.userWithVerifiedEmail();
		when(users.findByEmail(UserFixtures.EMAIL)).thenReturn(Optional.of(user));

		assertThat(service().requestReset("An.Nguyen@example.com")).isEmpty();
		verify(emails).sendPasswordReset(user, EmailAddress.of(UserFixtures.EMAIL));
	}

	@Test
	void unverifiedEmailReceivesNothing() {
		when(users.findByEmail(UserFixtures.EMAIL)).thenReturn(Optional.of(UserFixtures.userWithUnverifiedEmail()));

		assertThat(service().requestReset(UserFixtures.EMAIL)).isEmpty();
		verifyNoInteractions(emails);
	}

	@Test
	void correctResetCodeIsExchangedForASingleUseToken() {
		User user = UserFixtures.activeUser();
		when(users.findByPhoneE164(UserFixtures.PHONE)).thenReturn(Optional.of(user));
		when(tokens.issue(OneTimeTokenPurpose.PASSWORD_RESET, new OneTimeTokenSubject(user.getId(), UserFixtures.PHONE),
				Duration.ofMinutes(30)))
			.thenReturn(TOKEN);

		PasswordResetToken token = service().verifyResetCode(CHALLENGE_ID, "0912345678", "123456");

		verify(otp).verify(CHALLENGE_ID, RESET, "123456");
		assertThat(token.token()).isEqualTo(TOKEN);
		assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
	}

	@Test
	void resetByPhoneChangesThePasswordAndEndsEverySession() {
		User user = UserFixtures.activeUser();
		givenToken(user, UserFixtures.PHONE);
		when(passwordEncoder.encode("NewSecret456")).thenReturn("new-hash");

		service().resetPassword(TOKEN, "NewSecret456");

		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
		verify(sessions).endAll(user.getId(), RevokeReason.PASSWORD_CHANGED);
	}

	@Test
	void resetByEmailLinkAlsoVerifiesThatEmail() {
		User user = UserFixtures.userWithUnverifiedEmail();
		givenToken(user, UserFixtures.EMAIL);
		when(passwordEncoder.encode("NewSecret456")).thenReturn("new-hash");

		service().resetPassword(TOKEN, "NewSecret456");

		assertThat(user.hasVerifiedEmail()).isTrue();
	}

	@Test
	void unknownOrUsedTokenIsRejected() {
		when(tokens.find(OneTimeTokenPurpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().resetPassword(TOKEN, "NewSecret456"))
			.isInstanceOf(UnprocessableRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("RESET_TOKEN_INVALID");
	}

	@Test
	void weakNewPasswordKeepsTheTokenUsable() {
		User user = UserFixtures.activeUser();
		when(tokens.find(OneTimeTokenPurpose.PASSWORD_RESET, TOKEN))
			.thenReturn(Optional.of(new OneTimeTokenSubject(user.getId(), UserFixtures.PHONE)));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service().resetPassword(TOKEN, "short")).isInstanceOf(WeakPasswordException.class);
		verify(tokens, never()).consume(any(), any());
		verifyNoInteractions(sessions);
	}

	private void givenToken(User user, String boundTo) {
		OneTimeTokenSubject subject = new OneTimeTokenSubject(user.getId(), boundTo);
		when(tokens.find(OneTimeTokenPurpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.of(subject));
		when(tokens.consume(OneTimeTokenPurpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.of(subject));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
	}

}
