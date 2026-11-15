package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

public interface RegistrationService {

	/**
	 * Sends the registration code by SMS (FR-ID-02). Sent whether or not the number is
	 * registered: that is revealed only after the code is verified.
	 * @throws InvalidRequestException {@code INVALID_PHONE}
	 */
	OtpChallengeIssued sendRegistrationCode(String phone);

	/**
	 * Creates an ACTIVE account with a verified phone number, records the TERMS and
	 * PRIVACY consents and starts a session (FR-ID-01).
	 * @throws UnprocessableRequestException {@code CONSENT_REQUIRED} or an OTP error
	 * @throws ConflictException {@code POLICY_VERSION_STALE}, or
	 * {@code PHONE_ALREADY_REGISTERED} once the code is verified
	 * @throws WeakPasswordException listing every violated password rule
	 */
	RegisteredAccount register(RegisterCommand command);

}
