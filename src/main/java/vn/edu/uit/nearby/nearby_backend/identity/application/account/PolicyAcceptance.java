package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import org.jspecify.annotations.Nullable;

/**
 * Policy versions the user accepted on the registration screen; {@code null} means not
 * accepted.
 */
public record PolicyAcceptance(@Nullable String termsVersion, @Nullable String privacyVersion,
		boolean acceptsPushMarketing) {

}
