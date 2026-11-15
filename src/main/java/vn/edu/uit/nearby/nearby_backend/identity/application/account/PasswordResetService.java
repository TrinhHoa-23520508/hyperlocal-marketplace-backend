package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.util.Optional;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

public interface PasswordResetService {

	/**
	 * Starts a reset (FR-ID-07). For a phone number an SMS code is sent only when an
	 * account uses the number, but the answer is the same either way. For an email
	 * address a link is sent only when an account verified it.
	 * @return the SMS challenge for a phone number; empty for an email address
	 */
	Optional<OtpChallengeIssued> requestReset(String identifier);

	/**
	 * Exchanges a correct SMS code for a single-use reset token.
	 * @throws UnprocessableRequestException an OTP error
	 */
	PasswordResetToken verifyResetCode(UUID challengeId, String phone, String code);

	/**
	 * Sets the new password and logs out every session of the account.
	 * @throws UnprocessableRequestException {@code RESET_TOKEN_INVALID} for an unknown,
	 * used or expired token
	 * @throws WeakPasswordException when the new password breaks a rule; the token stays
	 * valid so the user can try another password
	 */
	void resetPassword(String token, String newPassword);

}
