package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Instant;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Fresh client submissions for service tests. Fix times are relative to the real clock
 * because the services read the application {@code Clock}.
 */
final class AreaSubmissions {

	static final GeoPoint UIT = new GeoPoint(10.8700, 106.8031);

	static final GeoPoint DISTRICT_1 = new GeoPoint(10.7769, 106.7009);

	static final GeoPoint THU_DUC_MARKET = new GeoPoint(10.8506, 106.7720);

	static final GeoPoint DI_AN = new GeoPoint(10.9068, 106.7690);

	private AreaSubmissions() {
	}

	static LocationSubmission realFixAt(GeoPoint location) {
		return submission(new GpsReading(new GpsFix(location, Instant.now().minusSeconds(5)), 15, false));
	}

	static LocationSubmission mockFixAt(GeoPoint location) {
		return submission(new GpsReading(new GpsFix(location, Instant.now().minusSeconds(5)), 15, true));
	}

	private static LocationSubmission submission(GpsReading reading) {
		return new LocationSubmission(reading, DeviceSignalReport.UNKNOWN, UUID.randomUUID(), "1.0.0");
	}

}
