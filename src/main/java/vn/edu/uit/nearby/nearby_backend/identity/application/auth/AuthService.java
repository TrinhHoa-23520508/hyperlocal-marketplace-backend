package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.error.AuthenticationFailedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

public interface AuthService {

	/**
	 * Logs in with the phone number or a verified email address (FR-ID-03).
	 * @throws AuthenticationFailedException {@code INVALID_CREDENTIALS} for an unknown
	 * identifier, an unverified email or a wrong password, without telling which
	 * @throws RateLimitExceededException {@code LOGIN_LOCKED} after too many failures for
	 * the identifier or the client IP
	 * @throws OperationNotAllowedException {@code ACCOUNT_SUSPENDED} or
	 * {@code ACCOUNT_BANNED} with the reason, for the right password only
	 */
	TokenPair login(LoginCommand command);

	/**
	 * Rotates the refresh token: the presented token is revoked and a new pair is issued.
	 * @throws AuthenticationFailedException {@code REFRESH_TOKEN_REUSED} when an already
	 * rotated token is presented; its whole family is revoked
	 */
	TokenPair refresh(String refreshToken);

	/**
	 * Revokes the refresh token and stops pushes to the device's FCM token. Unknown,
	 * expired or revoked tokens are already logged out.
	 */
	void logout(String refreshToken, @Nullable String fcmToken);

}
