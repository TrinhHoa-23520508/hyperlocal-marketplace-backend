package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import org.jspecify.annotations.Nullable;

/**
 * The private name the user gives an area, such as "Nhà" or "Công ty" (UC-AR-01 step 9;
 * {@code user_areas.label varchar(30) NOT NULL}).
 */
public record AreaLabel(String value) {

	public static final int MAX_LENGTH = 30;

	private static final String DEFAULT_LABEL_PREFIX = "Khu vực ";

	public AreaLabel(@Nullable String value) {
		String stripped = value == null ? "" : value.strip();
		if (stripped.isEmpty() || stripped.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Area label must be 1-" + MAX_LENGTH + " characters.");
		}
		this.value = stripped;
	}

	/**
	 * Name given until the user picks one; UC-AR-01 asks for the label only after the
	 * area is verified (step 9).
	 */
	public static AreaLabel defaultFor(int slot) {
		return new AreaLabel(DEFAULT_LABEL_PREFIX + slot);
	}

}
