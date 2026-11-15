package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.application.otp.OtpChallengeIssued;

public record OtpChallengeResponse(UUID challengeId, long expiresInSeconds, long resendAfterSeconds) {

	public static OtpChallengeResponse from(OtpChallengeIssued challenge) {
		return new OtpChallengeResponse(challenge.challengeId(), challenge.expiresIn().toSeconds(),
				challenge.resendAfter().toSeconds());
	}

}
