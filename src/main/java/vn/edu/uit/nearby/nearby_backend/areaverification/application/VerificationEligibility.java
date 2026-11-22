package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.LocationConsents;

/**
 * Preconditions of every verification and renewal (FR-AR-01, appendix D step 1): an
 * ACTIVE account with a verified phone, and PRECISE_LOCATION consent (FR-ID-15).
 */
@Component
class VerificationEligibility {

	private final AccountStanding accountStanding;

	private final LocationConsents locationConsents;

	VerificationEligibility(AccountStanding accountStanding, LocationConsents locationConsents) {
		this.accountStanding = accountStanding;
		this.locationConsents = locationConsents;
	}

	/**
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException
	 * {@code ACCESS_DENIED} or {@code LOCATION_CONSENT_REQUIRED}
	 */
	void require(UUID userId) {
		accountStanding.requireActiveWithVerifiedPhone(userId);
		locationConsents.requireConsent(userId);
	}

}
