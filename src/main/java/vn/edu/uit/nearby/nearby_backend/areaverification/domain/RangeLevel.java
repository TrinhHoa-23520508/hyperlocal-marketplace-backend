package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.util.Arrays;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * How far around an area the feed reaches and listings can be contacted (FR-AR-08,
 * FR-AR-09). The distances come from {@code area.range_levels_km} through
 * {@link RangeLevels}.
 */
public enum RangeLevel {

	NEAR, MEDIUM, FAR;

	public static final RangeLevel DEFAULT = MEDIUM;

	/**
	 * @throws InvalidRequestException {@code INVALID_RANGE} for a value outside the set
	 */
	public static RangeLevel parse(String value) {
		return Arrays.stream(values())
			.filter(level -> level.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException(ErrorCodes.INVALID_RANGE,
					"Range level must be one of " + Arrays.toString(values()) + "."));
	}

}
