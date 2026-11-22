package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;

/**
 * Result of a verification or renewal. A rejection is a normal outcome, not an exception,
 * so the rejected attempt is committed (FR-AR-10: every attempt is stored).
 */
public sealed interface VerificationOutcome {

	/**
	 * @param isRenewal true when an existing area was renewed (UC-AR-02, or UC-AR-01 A4)
	 */
	record Verified(AreaView area, boolean isRenewal) implements VerificationOutcome {
	}

	/**
	 * @param roundedDistanceMeters distance to the area center rounded to 100 m, only
	 * when the renewal radius was the reason (FR-AR-04)
	 */
	record Rejected(UUID attemptId, List<RejectionReason> reasons,
			OptionalLong roundedDistanceMeters) implements VerificationOutcome {

		public Rejected {
			reasons = List.copyOf(reasons);
		}

	}

}
