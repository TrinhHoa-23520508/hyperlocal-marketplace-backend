package vn.edu.uit.nearby.nearby_backend.identity.application.port;

import java.util.Optional;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpChallenge;

/**
 * Short-lived storage of OTP challenges (FR-ID-02). Stores only the code hash and expires
 * each challenge at {@link OtpChallenge#expiresAt()}.
 */
public interface OtpChallengeStore {

	void save(OtpChallenge challenge);

	/**
	 * Atomically counts one verification attempt and returns the challenge with the
	 * decremented {@link OtpChallenge#attemptsLeft()}.
	 * @return empty when the challenge is unknown, used or expired
	 */
	Optional<OtpChallenge> recordAttempt(UUID challengeId);

	void delete(UUID challengeId);

}
