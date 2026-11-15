package vn.edu.uit.nearby.nearby_backend.identity.application.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OtpChallengeStore;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpChallenge;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ServiceUnavailableException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;
import vn.edu.uit.nearby.nearby_backend.shared.sms.SmsDeliveryException;
import vn.edu.uit.nearby.nearby_backend.shared.sms.SmsSender;

@ExtendWith(MockitoExtension.class)
class OtpServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");

	private static final String CODE = "042137";

	private static final OtpTarget REGISTER = new OtpTarget(PhoneNumber.of("0912345678"), OtpPurpose.REGISTER);

	private static final String COOLDOWN_KEY = "otp-cooldown:REGISTER:+84912345678";

	private static final String DAILY_KEY = "otp-daily:+84912345678";

	private final InMemoryChallenges challenges = new InMemoryChallenges();

	@Mock
	SmsSender smsSender;

	@Mock
	RateLimiter rateLimiter;

	private OtpService service() {
		return new OtpService(challenges, smsSender, digits -> CODE, rateLimiter, IdentityParametersFixture.seeded(),
				IdentityParametersFixture.PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void sendTextsTheCodeAndStoresOnlyItsHash() {
		OtpChallengeIssued issued = service().send(REGISTER);

		verify(smsSender).send("+84912345678", CODE + " la ma Nearby");
		OtpChallenge stored = challenges.saved.get(issued.challengeId());
		assertThat(stored.codeHash()).hasSize(64).doesNotContain(CODE);
		assertThat(stored.expiresAt()).isEqualTo(NOW.plusSeconds(300));
		assertThat(stored.attemptsLeft()).isEqualTo(5);
		assertThat(issued.expiresIn()).isEqualTo(Duration.ofSeconds(300));
		assertThat(issued.resendAfter()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void successfulSendStartsTheResendCooldownAndChargesTheDailyQuota() {
		service().send(REGISTER);

		verify(rateLimiter).tryAcquire(COOLDOWN_KEY, 1, Duration.ofSeconds(60));
		verify(rateLimiter).tryAcquire(DAILY_KEY, 5, Duration.ofDays(1));
	}

	@Test
	void sendingAgainInsideTheCooldownIsRateLimited() {
		when(rateLimiter.isExhausted(COOLDOWN_KEY, 1)).thenReturn(true);
		when(rateLimiter.remainingWindow(COOLDOWN_KEY)).thenReturn(Duration.ofSeconds(42));

		assertThatThrownBy(() -> service().send(REGISTER)).isInstanceOfSatisfying(RateLimitExceededException.class,
				ex -> {
					assertThat(ex.getCode()).isEqualTo("OTP_RATE_LIMITED");
					assertThat(ex.getRetryAfter()).contains(Duration.ofSeconds(42));
				});
		verifyNoInteractions(smsSender);
	}

	@Test
	void sixthCodeOfTheDayIsRateLimited() {
		when(rateLimiter.isExhausted(COOLDOWN_KEY, 1)).thenReturn(false);
		when(rateLimiter.isExhausted(DAILY_KEY, 5)).thenReturn(true);
		when(rateLimiter.remainingWindow(DAILY_KEY)).thenReturn(Duration.ofHours(3));

		assertThatThrownBy(() -> service().send(REGISTER)).isInstanceOf(RateLimitExceededException.class)
			.extracting(OtpServiceTests::codeOf)
			.isEqualTo("OTP_RATE_LIMITED");
		verifyNoInteractions(smsSender);
	}

	@Test
	void gatewayFailureIsReportedAsUnavailableWithoutChargingTheQuota() {
		doThrow(new SmsDeliveryException("down")).when(smsSender).send(anyString(), anyString());

		assertThatThrownBy(() -> service().send(REGISTER)).isInstanceOf(ServiceUnavailableException.class)
			.extracting(OtpServiceTests::codeOf)
			.isEqualTo("SMS_UNAVAILABLE");
		verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
		assertThat(challenges.saved).isEmpty();
	}

	@Test
	void sendNothingAnswersTheSameWayAndAppliesTheSameLimitsWithoutTexting() {
		OtpChallengeIssued issued = service().sendNothing(REGISTER);

		assertThat(issued.expiresIn()).isEqualTo(Duration.ofSeconds(300));
		assertThat(issued.resendAfter()).isEqualTo(Duration.ofSeconds(60));
		assertThat(challenges.saved).isEmpty();
		verifyNoInteractions(smsSender);
		verify(rateLimiter).tryAcquire(DAILY_KEY, 5, Duration.ofDays(1));
	}

	@Test
	void rightCodeVerifiesOnceAndRemovesTheChallenge() {
		UUID challengeId = service().send(REGISTER).challengeId();

		service().verify(challengeId, REGISTER, CODE);

		assertThat(challenges.saved).doesNotContainKey(challengeId);
		assertThatThrownBy(() -> service().verify(challengeId, REGISTER, CODE)).extracting(OtpServiceTests::codeOf)
			.isEqualTo("OTP_EXPIRED");
	}

	@Test
	void wrongCodeReportsTheRemainingAttempts() {
		UUID challengeId = service().send(REGISTER).challengeId();

		assertThatThrownBy(() -> service().verify(challengeId, REGISTER, "000000"))
			.isInstanceOfSatisfying(UnprocessableRequestException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("OTP_INVALID");
				assertThat(ex.getProperties()).containsEntry("remainingAttempts", 4);
			});
		assertThat(challenges.saved).containsKey(challengeId);
	}

	@Test
	void fifthWrongCodeCancelsTheChallenge() {
		UUID challengeId = service().send(REGISTER).challengeId();
		for (int attempt = 1; attempt < 5; attempt++) {
			assertThatThrownBy(() -> service().verify(challengeId, REGISTER, "000000"))
				.extracting(OtpServiceTests::codeOf)
				.isEqualTo("OTP_INVALID");
		}

		assertThatThrownBy(() -> service().verify(challengeId, REGISTER, "000000")).extracting(OtpServiceTests::codeOf)
			.isEqualTo("OTP_ATTEMPTS_EXCEEDED");
		assertThat(challenges.saved).doesNotContainKey(challengeId);
	}

	@Test
	void unknownChallengeIsTreatedAsExpired() {
		assertThatThrownBy(() -> service().verify(UUID.randomUUID(), REGISTER, CODE))
			.isInstanceOf(UnprocessableRequestException.class)
			.extracting(OtpServiceTests::codeOf)
			.isEqualTo("OTP_EXPIRED");
	}

	@Test
	void codeIsRejectedOnceItsLifetimeEnds() {
		UUID challengeId = service().send(REGISTER).challengeId();
		OtpService later = new OtpService(challenges, smsSender, digits -> CODE, rateLimiter,
				IdentityParametersFixture.seeded(), IdentityParametersFixture.PROPERTIES,
				Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC));

		assertThatThrownBy(() -> later.verify(challengeId, REGISTER, CODE)).extracting(OtpServiceTests::codeOf)
			.isEqualTo("OTP_EXPIRED");
		assertThat(challenges.saved).doesNotContainKey(challengeId);
	}

	private static String codeOf(Throwable ex) {
		return ((BusinessException) ex).getCode();
	}

	private static final class InMemoryChallenges implements OtpChallengeStore {

		private final Map<UUID, OtpChallenge> saved = new HashMap<>();

		@Override
		public void save(OtpChallenge challenge) {
			saved.put(challenge.id(), challenge);
		}

		@Override
		public Optional<OtpChallenge> recordAttempt(UUID challengeId) {
			return Optional.ofNullable(saved.computeIfPresent(challengeId,
					(id, challenge) -> new OtpChallenge(id, challenge.phone(), challenge.purpose(),
							challenge.codeHash(), challenge.expiresAt(), challenge.attemptsLeft() - 1)));
		}

		@Override
		public void delete(UUID challengeId) {
			saved.remove(challengeId);
		}

	}

}
