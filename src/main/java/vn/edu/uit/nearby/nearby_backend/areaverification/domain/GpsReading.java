package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

/**
 * One fix sent by the client for a verification or renewal (FR-AR-01 input).
 *
 * @param accuracyMeters horizontal accuracy reported by the OS, at least 0
 * @param isMockLocation the OS mock-location flag
 */
public record GpsReading(GpsFix fix, double accuracyMeters, boolean isMockLocation) {

	public GpsReading {
		if (!Double.isFinite(accuracyMeters) || accuracyMeters < 0) {
			throw new IllegalArgumentException("Accuracy must be a number of meters >= 0: " + accuracyMeters);
		}
	}

}
