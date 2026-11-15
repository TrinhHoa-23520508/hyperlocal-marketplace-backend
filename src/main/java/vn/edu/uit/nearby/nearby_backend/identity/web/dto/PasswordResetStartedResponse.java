package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;

/**
 * @param channel {@code SMS}: enter the code sent by SMS; {@code EMAIL}: open the link
 * sent by email
 */
public record PasswordResetStartedResponse(String channel, @Nullable UUID challengeId, @Nullable Long expiresInSeconds,
		@Nullable Long resendAfterSeconds) {

	private static final String SMS = "SMS";

	private static final String EMAIL = "EMAIL";

	public static PasswordResetStartedResponse from(Optional<OtpChallengeIssued> smsChallenge) {
		return smsChallenge
			.map(challenge -> new PasswordResetStartedResponse(SMS, challenge.challengeId(),
					challenge.expiresIn().toSeconds(), challenge.resendAfter().toSeconds()))
			.orElseGet(() -> new PasswordResetStartedResponse(EMAIL, null, null, null));
	}

}
