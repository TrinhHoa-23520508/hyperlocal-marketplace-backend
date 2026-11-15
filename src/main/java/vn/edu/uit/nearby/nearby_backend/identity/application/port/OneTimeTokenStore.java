package vn.edu.uit.nearby.nearby_backend.identity.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Single-use tokens for email links and password resets. Only a hash of the token is
 * stored.
 */
public interface OneTimeTokenStore {

	/**
	 * @return the raw token to give to the user
	 */
	String issue(OneTimeTokenPurpose purpose, OneTimeTokenSubject subject, Duration ttl);

	Optional<OneTimeTokenSubject> find(OneTimeTokenPurpose purpose, String token);

	/**
	 * Atomically reads and deletes the token, so it can be used only once.
	 */
	Optional<OneTimeTokenSubject> consume(OneTimeTokenPurpose purpose, String token);

}
