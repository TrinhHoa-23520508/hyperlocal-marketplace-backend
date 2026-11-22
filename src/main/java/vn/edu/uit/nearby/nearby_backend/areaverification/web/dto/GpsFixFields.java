package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.LocationSubmission;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * The fix and device fields that the verification and renewal requests share (FR-AR-01
 * input), and their conversion to a {@link LocationSubmission}.
 */
public interface GpsFixFields {

	Double latitude();

	Double longitude();

	Double accuracyMeters();

	Instant fixTime();

	Boolean mockLocation();

	@Nullable DeviceSignalsPayload deviceSignals();

	@Nullable String appVersion();

	default LocationSubmission toSubmission(UUID idempotencyKey) {
		GpsReading reading = new GpsReading(new GpsFix(new GeoPoint(latitude(), longitude()), fixTime()),
				accuracyMeters(), mockLocation());
		DeviceSignalReport device = deviceSignals() == null ? DeviceSignalReport.UNKNOWN : deviceSignals().toReport();
		return new LocationSubmission(reading, device, idempotencyKey, appVersion());
	}

}
