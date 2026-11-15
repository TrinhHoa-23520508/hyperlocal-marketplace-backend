package vn.edu.uit.nearby.nearby_backend.identity.domain.otp;

import java.time.Duration;

/**
 * OTP limits from {@code app_parameters} ({@code otp.*}, FR-ID-02).
 */
public record OtpPolicy(Duration ttl, int maxAttempts, Duration resendInterval, int maxPerDay) {

	public static final int CODE_DIGITS = 6;

	public static final Duration DAILY_QUOTA_WINDOW = Duration.ofDays(1);

}
