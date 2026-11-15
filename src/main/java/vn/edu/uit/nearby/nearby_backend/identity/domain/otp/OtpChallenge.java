package vn.edu.uit.nearby.nearby_backend.identity.domain.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;

/**
 * A one-time code sent to a phone number for one purpose (FR-ID-02). Only the hash of the
 * code is kept.
 *
 * @param attemptsLeft wrong entries still allowed after the attempt being checked has
 * been counted; negative once the limit was already used up
 */
public record OtpChallenge(UUID id, PhoneNumber phone, OtpPurpose purpose, String codeHash, Instant expiresAt,
		int attemptsLeft) {

	public static OtpChallenge issue(UUID id, OtpTarget target, String codeHash, OtpPolicy policy, Instant now) {
		return new OtpChallenge(id, target.phone(), target.purpose(), codeHash, now.plus(policy.ttl()),
				policy.maxAttempts());
	}

	public Duration timeToLive(Instant now) {
		return Duration.between(now, expiresAt);
	}

	/**
	 * Checks one entry. The caller has already counted this attempt in
	 * {@link #attemptsLeft}, so concurrent guesses cannot exceed the limit.
	 */
	public OtpVerification verify(OtpTarget target, String presentedCodeHash, Instant now) {
		if (!now.isBefore(expiresAt)) {
			return OtpVerification.expired();
		}
		if (attemptsLeft < 0) {
			return OtpVerification.attemptsExceeded();
		}
		if (isFor(target) && hashesMatch(presentedCodeHash)) {
			return OtpVerification.verified();
		}
		return attemptsLeft == 0 ? OtpVerification.attemptsExceeded() : OtpVerification.invalid(attemptsLeft);
	}

	private boolean isFor(OtpTarget target) {
		return phone.equals(target.phone()) && purpose == target.purpose();
	}

	private boolean hashesMatch(String presentedCodeHash) {
		return MessageDigest.isEqual(codeHash.getBytes(StandardCharsets.UTF_8),
				presentedCodeHash.getBytes(StandardCharsets.UTF_8));
	}

}
