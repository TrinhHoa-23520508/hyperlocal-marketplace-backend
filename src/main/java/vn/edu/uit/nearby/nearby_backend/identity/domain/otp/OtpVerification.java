package vn.edu.uit.nearby.nearby_backend.identity.domain.otp;

/**
 * @param attemptsLeft wrong entries still allowed; meaningful only for
 * {@link Outcome#INVALID}
 */
public record OtpVerification(Outcome outcome, int attemptsLeft) {

	public enum Outcome {

		VERIFIED, INVALID, EXPIRED, ATTEMPTS_EXCEEDED

	}

	static OtpVerification verified() {
		return new OtpVerification(Outcome.VERIFIED, 0);
	}

	static OtpVerification invalid(int attemptsLeft) {
		return new OtpVerification(Outcome.INVALID, attemptsLeft);
	}

	public static OtpVerification expired() {
		return new OtpVerification(Outcome.EXPIRED, 0);
	}

	static OtpVerification attemptsExceeded() {
		return new OtpVerification(Outcome.ATTEMPTS_EXCEEDED, 0);
	}

	public boolean isVerified() {
		return outcome == Outcome.VERIFIED;
	}

	/**
	 * @return true when the challenge can never be verified again and should be removed
	 */
	public boolean isFinal() {
		return outcome != Outcome.INVALID;
	}

}
