package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.time.Duration;
import java.util.Optional;

/**
 * Soft signal "lệch giờ" (FR-AR-05): the fix claims to be from the future by more than
 * {@code area.max_fix_age_s}, so the device clock is off or the time was edited. The SRS
 * gives no separate limit; the fix-age window is reused in the other direction.
 */
public class TimeDriftSignal implements LocationRiskSignal {

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		Duration aheadOfServer = Duration.between(context.receivedAt(), context.reading().fix().fixTime());
		if (aheadOfServer.compareTo(context.thresholds().maxFixAge()) <= 0) {
			return Optional.empty();
		}
		return Optional.of(RiskFinding.soft(DeviceSignalCode.TIME_DRIFT, Long.toString(aheadOfServer.toSeconds())));
	}

}
