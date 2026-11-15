package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.util.UUID;

/**
 * @param challengeId the OTP challenge sent to {@code phone} for registration
 */
public record RegisterCommand(String phone, UUID challengeId, String otp, String password, String displayName,
		PolicyAcceptance policies, String clientIp) {

}
