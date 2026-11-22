package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Kilometers per {@link RangeLevel}, read from {@code area.range_levels_km} (for example
 * {@code "2,4,6"}: NEAR, MEDIUM, FAR).
 */
public record RangeLevels(Map<RangeLevel, Integer> kilometers) {

	private static final int METERS_PER_KILOMETER = 1000;

	public RangeLevels {
		kilometers = Map.copyOf(kilometers);
	}

	/**
	 * @throws IllegalStateException unless the value lists one positive, strictly
	 * increasing whole number of kilometers per level
	 */
	public static RangeLevels parse(String commaSeparatedKilometers) {
		int[] values = parseNumbers(commaSeparatedKilometers);
		RangeLevel[] levels = RangeLevel.values();
		if (values.length != levels.length || values[0] <= 0 || !isStrictlyIncreasing(values)) {
			throw new IllegalStateException("area.range_levels_km must hold " + levels.length
					+ " increasing positive kilometers, got: " + commaSeparatedKilometers);
		}
		Map<RangeLevel, Integer> kilometers = new EnumMap<>(RangeLevel.class);
		for (int i = 0; i < levels.length; i++) {
			kilometers.put(levels[i], values[i]);
		}
		return new RangeLevels(kilometers);
	}

	public int radiusMetersOf(RangeLevel level) {
		return kilometers.get(level) * METERS_PER_KILOMETER;
	}

	private static int[] parseNumbers(String commaSeparatedKilometers) {
		try {
			return Arrays.stream(commaSeparatedKilometers.split(","))
				.map(String::strip)
				.mapToInt(Integer::parseInt)
				.toArray();
		}
		catch (NumberFormatException ex) {
			throw new IllegalStateException(
					"area.range_levels_km is not a list of numbers: " + commaSeparatedKilometers, ex);
		}
	}

	private static boolean isStrictlyIncreasing(int[] values) {
		for (int i = 1; i < values.length; i++) {
			if (values[i] <= values[i - 1]) {
				return false;
			}
		}
		return true;
	}

}
