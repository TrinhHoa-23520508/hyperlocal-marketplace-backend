package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;

public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<UUID> id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt) {
			return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
		}
		return Optional.empty();
	}

	/**
	 * @return the refresh token family the access token was issued for; empty for tokens
	 * issued outside a login session
	 */
	public static Optional<UUID> sessionId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt) {
			return Optional.ofNullable(jwt.getToken().getClaimAsString(SecurityConstants.SESSION_CLAIM))
				.map(UUID::fromString);
		}
		return Optional.empty();
	}

	public static UUID requireId() {
		return id().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
	}

}
