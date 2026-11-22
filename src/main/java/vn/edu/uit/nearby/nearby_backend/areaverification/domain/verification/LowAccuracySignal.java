package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;

/**
 * Hard rule: the fix is less accurate than {@code area.accuracy_threshold_m}; exactly the
 * threshold still passes (FR-AR-05, "accuracy ≤ 100 m mới đạt").
 */
public class LowAccuracySignal implements LocationRiskSignal {

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		double accuracyMeters = context.reading().accuracyMeters();
		if (accuracyMeters <= context.thresholds().maxAccuracyMeters()) {
			return Optional.empty();
		}
		return Optional.of(RiskFinding.hard(RejectionReason.LOW_ACCURACY, Double.toString(accuracyMeters)));
	}

}
