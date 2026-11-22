package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;

/**
 * Everything the anti-spoofing checks look at for one attempt.
 *
 * @param previousAcceptedFix the user's last accepted fix, null for a first verification
 * @param receivedAt server time the request arrived
 */
public record VerificationContext(GpsReading reading, DeviceSignalReport device, @Nullable GpsFix previousAcceptedFix,
		Instant receivedAt, AntiSpoofingThresholds thresholds) {

	public Optional<GpsFix> lastAcceptedFix() {
		return Optional.ofNullable(previousAcceptedFix);
	}

	/**
	 * Speed implied since the previous accepted fix, stored on every attempt
	 * ({@code area_verifications.implied_speed_kmh}); empty when there is none.
	 */
	public OptionalDouble impliedSpeedKmh() {
		return lastAcceptedFix().map(previous -> OptionalDouble.of(reading.fix().impliedSpeedKmhSince(previous)))
			.orElseGet(OptionalDouble::empty);
	}

}
