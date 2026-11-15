package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.util.regex.Pattern;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Vietnamese mobile number normalised to E.164 (FR-ID-01). Accepts the national form
 * {@code 0912345678}, {@code 84912345678} and {@code +84912345678}, with spaces, dots or
 * dashes between digits.
 */
public record PhoneNumber(String value) implements AccountIdentifier {

	private static final String COUNTRY_CODE = "+84";

	private static final String COUNTRY_CODE_DIGITS = "84";

	private static final String NATIONAL_TRUNK_PREFIX = "0";

	private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");

	private static final Pattern VIETNAM_MOBILE_E164 = Pattern.compile("^\\+84[35789][0-9]{8}$");

	public PhoneNumber {
		value = toE164(value == null ? "" : SEPARATORS.matcher(value).replaceAll(""));
		if (!VIETNAM_MOBILE_E164.matcher(value).matches()) {
			throw new InvalidRequestException(ErrorCodes.INVALID_PHONE,
					"The phone number is not a valid Vietnamese mobile number.");
		}
	}

	public static PhoneNumber of(String value) {
		return new PhoneNumber(value);
	}

	private static String toE164(String digits) {
		if (digits.startsWith(NATIONAL_TRUNK_PREFIX)) {
			return COUNTRY_CODE + digits.substring(NATIONAL_TRUNK_PREFIX.length());
		}
		if (digits.startsWith(COUNTRY_CODE_DIGITS)) {
			return "+" + digits;
		}
		return digits;
	}

}
