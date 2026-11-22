package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

import java.time.Instant;
import java.util.OptionalDouble;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridCell;

/**
 * What was measured for an attempt.
 *
 * @param fixCell grid cell of the reported fix, kept after the precise point is purged
 * @param distanceMeters distance from the fix to the area center, for renewals
 * @param receivedAt server time of the request
 */
public record AttemptEvidence(GpsReading reading, GridCell fixCell, OptionalDouble impliedSpeedKmh,
		@Nullable Double distanceMeters, Instant receivedAt) {
}
