package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;

import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordResetToken;

public record PasswordResetTokenResponse(String resetToken, Instant expiresAt) {

	public static PasswordResetTokenResponse from(PasswordResetToken token) {
		return new PasswordResetTokenResponse(token.token(), token.expiresAt());
	}

}
