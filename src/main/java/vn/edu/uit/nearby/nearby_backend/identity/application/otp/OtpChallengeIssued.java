package vn.edu.uit.nearby.nearby_backend.identity.application.otp;

import java.time.Duration;
import java.util.UUID;

/**
 * @param challengeId sent back with the code to verify it
 * @param resendAfter wait before another code can be requested for the same purpose
 */
public record OtpChallengeIssued(UUID challengeId, Duration expiresIn, Duration resendAfter) {

}
