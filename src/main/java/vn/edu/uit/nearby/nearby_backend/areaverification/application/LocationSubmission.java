package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;

/**
 * What the client sends with every verification or renewal (FR-AR-01 input).
 *
 * @param idempotencyKey the request's Idempotency-Key, stored with the attempt
 * @param appVersion client version for diagnosis
 */
public record LocationSubmission(GpsReading reading, DeviceSignalReport device, UUID idempotencyKey,
		@Nullable String appVersion) {
}
