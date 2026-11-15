package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import vn.edu.uit.nearby.nearby_backend.shared.error.AuthenticationFailedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

public interface AuthService {

	/**
	 * Logs in with the phone number or a verified email address (FR-ID-03).
	 * @throws AuthenticationFailedException {@code INVALID_CREDENTIALS} for an unknown
	 * identifier, an unverified email or a wrong password, without telling which
	 * @throws RateLimitExceededException {@code LOGIN_LOCKED} after too many failures
	 */
	TokenPair login(LoginCommand command);

	/**
	 * Rotates the refresh token: the presented token is revoked and a new pair is issued.
	 * @throws AuthenticationFailedException {@code REFRESH_TOKEN_REUSED} when an already
	 * rotated token is presented; its whole family is revoked
	 */
	TokenPair refresh(String refreshToken);

	/**
	 * Revokes the refresh token. Unknown, expired or revoked tokens are already logged
	 * out.
	 */
	void logout(String refreshToken);

}
