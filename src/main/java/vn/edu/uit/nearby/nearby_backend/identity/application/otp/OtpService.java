package vn.edu.uit.nearby.nearby_backend.identity.application.otp;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityProperties;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OtpChallengeStore;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpChallenge;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPolicy;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpTarget;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpVerification;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ServiceUnavailableException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.ratelimit.RateLimiter;
import vn.edu.uit.nearby.nearby_backend.shared.security.OneTimeCodeGenerator;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.shared.sms.SmsDeliveryException;
import vn.edu.uit.nearby.nearby_backend.shared.sms.SmsSender;

/**
 * Issues and verifies SMS one-time codes (FR-ID-02): 6 digits, hashed at rest, limited by
 * lifetime, attempts, resend interval and a daily quota per number.
 */
@Service
public class OtpService {

	private static final String REMAINING_ATTEMPTS = "remainingAttempts";

	private final OtpChallengeStore challenges;

	private final SmsSender smsSender;

	private final OneTimeCodeGenerator codeGenerator;

	private final RateLimiter rateLimiter;

	private final IdentityParameters parameters;

	private final IdentityProperties.Otp settings;

	private final Clock clock;

	public OtpService(OtpChallengeStore challenges, SmsSender smsSender, OneTimeCodeGenerator codeGenerator,
			RateLimiter rateLimiter, IdentityParameters parameters, IdentityProperties properties, Clock clock) {
		this.challenges = challenges;
		this.smsSender = smsSender;
		this.codeGenerator = codeGenerator;
		this.rateLimiter = rateLimiter;
		this.parameters = parameters;
		this.settings = properties.otp();
		this.clock = clock;
	}

	/**
	 * @throws RateLimitExceededException {@code OTP_RATE_LIMITED} inside the resend
	 * interval or over the daily quota
	 * @throws ServiceUnavailableException {@code SMS_UNAVAILABLE} when the gateway fails;
	 * the quota is not charged
	 */
	public OtpChallengeIssued send(OtpTarget target) {
		OtpPolicy policy = parameters.otpPolicy();
		requireWithinLimits(target, policy);
		UUID challengeId = UUID.randomUUID();
		String code = codeGenerator.numericCode(OtpPolicy.CODE_DIGITS);
		deliver(target, code);
		challenges.save(OtpChallenge.issue(challengeId, target, hashOf(challengeId, code), policy, clock.instant()));
		chargeQuota(target, policy);
		return new OtpChallengeIssued(challengeId, policy.ttl(), policy.resendInterval());
	}

	/**
	 * Answers exactly like {@link #send(OtpTarget)}, limits included, without sending
	 * anything: used when the number has no account, so the response does not reveal it
	 * (FR-ID-07).
	 */
	public OtpChallengeIssued sendNothing(OtpTarget target) {
		OtpPolicy policy = parameters.otpPolicy();
		requireWithinLimits(target, policy);
		chargeQuota(target, policy);
		return new OtpChallengeIssued(UUID.randomUUID(), policy.ttl(), policy.resendInterval());
	}

	/**
	 * Succeeds once per challenge; the challenge is removed when verified, expired or out
	 * of attempts.
	 * @throws UnprocessableRequestException {@code OTP_INVALID} with the remaining
	 * attempts, {@code OTP_EXPIRED} or {@code OTP_ATTEMPTS_EXCEEDED}
	 */
	public void verify(UUID challengeId, OtpTarget target, String code) {
		OtpVerification verification = challenges.recordAttempt(challengeId)
			.map(challenge -> challenge.verify(target, hashOf(challengeId, code), clock.instant()))
			.orElseGet(OtpVerification::expired);
		if (verification.isFinal()) {
			challenges.delete(challengeId);
		}
		throwUnlessVerified(verification);
	}

	private void requireWithinLimits(OtpTarget target, OtpPolicy policy) {
		requireNotExhausted(resendCooldownKey(target), 1);
		requireNotExhausted(dailyQuotaKey(target), policy.maxPerDay());
	}

	private void requireNotExhausted(String key, int limit) {
		if (rateLimiter.isExhausted(key, limit)) {
			throw new RateLimitExceededException(ErrorCodes.OTP_RATE_LIMITED,
					"Too many codes requested for this number, please wait.", rateLimiter.remainingWindow(key));
		}
	}

	private void deliver(OtpTarget target, String code) {
		try {
			smsSender.send(target.phone().value(), settings.smsContent(code));
		}
		catch (SmsDeliveryException ex) {
			throw new ServiceUnavailableException(ErrorCodes.SMS_UNAVAILABLE,
					"The verification code could not be sent, please try again later.");
		}
	}

	private void chargeQuota(OtpTarget target, OtpPolicy policy) {
		rateLimiter.tryAcquire(resendCooldownKey(target), 1, policy.resendInterval());
		rateLimiter.tryAcquire(dailyQuotaKey(target), policy.maxPerDay(), OtpPolicy.DAILY_QUOTA_WINDOW);
	}

	private String hashOf(UUID challengeId, String code) {
		return TokenService.hmacSha256Hex(settings.hashSecret(), challengeId + ":" + code.strip());
	}

	private static void throwUnlessVerified(OtpVerification verification) {
		switch (verification.outcome()) {
			case VERIFIED -> {
			}
			case INVALID -> throw new UnprocessableRequestException(ErrorCodes.OTP_INVALID, "The code is incorrect.",
					Map.of(REMAINING_ATTEMPTS, verification.attemptsLeft()));
			case EXPIRED -> throw new UnprocessableRequestException(ErrorCodes.OTP_EXPIRED,
					"The code has expired, please request a new one.");
			case ATTEMPTS_EXCEEDED -> throw new UnprocessableRequestException(ErrorCodes.OTP_ATTEMPTS_EXCEEDED,
					"Too many wrong codes, please request a new one.");
		}
	}

	private static String resendCooldownKey(OtpTarget target) {
		return RedisKeyPrefixes.OTP_RESEND_COOLDOWN + target.purpose() + ":" + target.phone().value();
	}

	private static String dailyQuotaKey(OtpTarget target) {
		return RedisKeyPrefixes.OTP_DAILY_QUOTA + target.phone().value();
	}

}
