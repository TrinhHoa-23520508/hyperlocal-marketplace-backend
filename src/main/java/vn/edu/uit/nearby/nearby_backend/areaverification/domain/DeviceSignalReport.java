package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import org.jspecify.annotations.Nullable;

/**
 * Device signals the client read (FR-AR-05 soft signals). A null value means the client
 * could not read it; it is treated as unknown, never as a finding.
 */
public record DeviceSignalReport(@Nullable Boolean isEmulator, @Nullable Boolean isRooted,
		@Nullable Boolean hasDeveloperOptions, @Nullable Boolean hasMockLocationApp) {

	public static final DeviceSignalReport UNKNOWN = new DeviceSignalReport(null, null, null, null);

}
