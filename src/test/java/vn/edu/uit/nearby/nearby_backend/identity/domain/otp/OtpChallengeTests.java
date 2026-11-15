package vn.edu.uit.nearby.nearby_backend.identity.domain.otp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpVerification.Outcome;

class OtpChallengeTests {

	private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");

	private static final OtpPolicy POLICY = new OtpPolicy(Duration.ofSeconds(300), 5, Duration.ofSeconds(60), 5);

	private static final OtpTarget REGISTER = new OtpTarget(PhoneNumber.of("0912345678"), OtpPurpose.REGISTER);

	private static final String HASH = "hash-of-123456";

	@Test
	void issuedChallengeExpiresAfterTheTtlWithAllAttempts() {
		OtpChallenge challenge = issued();

		assertThat(challenge.expiresAt()).isEqualTo(NOW.plusSeconds(300));
		assertThat(challenge.attemptsLeft()).isEqualTo(5);
		assertThat(challenge.timeToLive(NOW)).isEqualTo(Duration.ofSeconds(300));
	}

	@Test
	void rightCodeForTheRightTargetIsVerified() {
		OtpVerification verification = afterAttempts(1).verify(REGISTER, HASH, NOW.plusSeconds(10));

		assertThat(verification.isVerified()).isTrue();
		assertThat(verification.isFinal()).isTrue();
	}

	@Test
	void wrongCodeReportsTheAttemptsLeft() {
		OtpVerification verification = afterAttempts(1).verify(REGISTER, "wrong", NOW);

		assertThat(verification.outcome()).isEqualTo(Outcome.INVALID);
		assertThat(verification.attemptsLeft()).isEqualTo(4);
		assertThat(verification.isFinal()).isFalse();
	}

	@Test
	void fifthWrongCodeCancelsTheChallenge() {
		OtpVerification verification = afterAttempts(5).verify(REGISTER, "wrong", NOW);

		assertThat(verification.outcome()).isEqualTo(Outcome.ATTEMPTS_EXCEEDED);
		assertThat(verification.isFinal()).isTrue();
	}

	@Test
	void rightCodeOnTheLastAllowedAttemptStillVerifies() {
		assertThat(afterAttempts(5).verify(REGISTER, HASH, NOW).isVerified()).isTrue();
	}

	@Test
	void attemptBeyondTheLimitIsRejectedEvenWithTheRightCode() {
		assertThat(afterAttempts(6).verify(REGISTER, HASH, NOW).outcome()).isEqualTo(Outcome.ATTEMPTS_EXCEEDED);
	}

	@Test
	void codeIsRejectedExactlyAtTheEndOfItsLifetime() {
		OtpChallenge challenge = afterAttempts(1);

		assertThat(challenge.verify(REGISTER, HASH, NOW.plusSeconds(299)).isVerified()).isTrue();
		assertThat(challenge.verify(REGISTER, HASH, NOW.plusSeconds(300)).outcome()).isEqualTo(Outcome.EXPIRED);
	}

	@Test
	void codeSentForAnotherPurposeDoesNotVerify() {
		OtpTarget reset = new OtpTarget(REGISTER.phone(), OtpPurpose.RESET_PASSWORD);

		assertThat(afterAttempts(1).verify(reset, HASH, NOW).outcome()).isEqualTo(Outcome.INVALID);
	}

	@Test
	void codeSentToAnotherNumberDoesNotVerify() {
		OtpTarget otherPhone = new OtpTarget(PhoneNumber.of("0987654321"), OtpPurpose.REGISTER);

		assertThat(afterAttempts(1).verify(otherPhone, HASH, NOW).outcome()).isEqualTo(Outcome.INVALID);
	}

	private static OtpChallenge issued() {
		return OtpChallenge.issue(UUID.randomUUID(), REGISTER, HASH, POLICY, NOW);
	}

	private static OtpChallenge afterAttempts(int attempts) {
		OtpChallenge challenge = issued();
		return new OtpChallenge(challenge.id(), challenge.phone(), challenge.purpose(), challenge.codeHash(),
				challenge.expiresAt(), challenge.attemptsLeft() - attempts);
	}

}
