package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;

/**
 * Hard rule: reaching this fix from the user's previous accepted fix needs more than
 * {@code area.max_speed_kmh}; exactly the limit still passes. A first verification has
 * nothing to compare with and passes (FR-AR-05).
 */
public class ImpossibleSpeedSignal implements LocationRiskSignal {

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		double maxSpeedKmh = context.thresholds().maxSpeedKmh();
		return context.impliedSpeedKmh()
			.stream()
			.filter(speedKmh -> speedKmh > maxSpeedKmh)
			.mapToObj(speedKmh -> RiskFinding.hard(RejectionReason.IMPOSSIBLE_SPEED, DeviceSignalCode.IMPOSSIBLE_SPEED,
					Long.toString(Math.round(speedKmh))))
			.findFirst();
	}

}
