package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;

/**
 * Hard rule: the OS reports the fix as a mock location (FR-AR-05).
 */
public class MockLocationSignal implements LocationRiskSignal {

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		if (!context.reading().isMockLocation()) {
			return Optional.empty();
		}
		return Optional.of(RiskFinding.hard(RejectionReason.MOCK_LOCATION, DeviceSignalCode.MOCK_FLAG, "true"));
	}

}
