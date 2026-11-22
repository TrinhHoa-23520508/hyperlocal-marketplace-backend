package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * The suspension lengths moderation may choose from (FR-MD-05).
 */
public enum SuspensionPeriod {

	ONE_DAY(1), ONE_WEEK(7), ONE_MONTH(30);

	private final int days;

	SuspensionPeriod(int days) {
		this.days = days;
	}

	/**
	 * @throws InvalidRequestException {@code INVALID_SUSPENSION_PERIOD} for any length
	 * other than 1, 7 or 30 days
	 */
	public static SuspensionPeriod ofDays(int days) {
		return Arrays.stream(values())
			.filter(period -> period.days == days)
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException(ErrorCodes.INVALID_SUSPENSION_PERIOD,
					"A suspension lasts 1, 7 or 30 days."));
	}

	public Instant endFrom(Instant start) {
		return start.plus(Duration.ofDays(days));
	}

}
