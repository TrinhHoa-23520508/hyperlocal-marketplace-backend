package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;

/**
 * One anti-spoofing check of FR-AR-05. Adding a check means adding an implementation;
 * {@link AreaVerificationPolicy} never changes.
 */
public interface LocationRiskSignal {

	Optional<RiskFinding> inspect(VerificationContext context);

}
