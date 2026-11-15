package vn.edu.uit.nearby.nearby_backend.identity.domain.password;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;

/**
 * @param ownerEmail the account's email address, when it has one
 */
public record PasswordCandidate(String password, @Nullable EmailAddress ownerEmail) {

	public PasswordCandidate {
		password = password == null ? "" : password;
	}

}
