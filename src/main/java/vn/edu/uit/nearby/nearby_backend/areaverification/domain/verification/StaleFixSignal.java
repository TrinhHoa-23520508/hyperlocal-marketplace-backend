package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.time.Duration;
import java.util.Optional;

/**
 * Hard rule: the fix is older than {@code area.max_fix_age_s} when the request arrives;
 * exactly the limit still passes (FR-AR-05, appendix D "now − fix_time ≤ 120 s").
 */
public class StaleFixSignal implements LocationRiskSignal {

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		Duration age = Duration.between(context.reading().fix().fixTime(), context.receivedAt());
		if (age.compareTo(context.thresholds().maxFixAge()) <= 0) {
			return Optional.empty();
		}
		return Optional.of(RiskFinding.hard(RejectionReason.STALE_FIX, Long.toString(age.toSeconds())));
	}

}
