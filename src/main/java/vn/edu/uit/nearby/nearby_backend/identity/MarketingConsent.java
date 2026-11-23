package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.UUID;

/**
 * The PUSH_MARKETING consent (FR-ID-15). Notification asks it before enabling marketing
 * pushes (FR-NT-03), because identity owns consents.
 */
public interface MarketingConsent {

	/**
	 * @return true when the newest PUSH_MARKETING row is granted and not withdrawn; false
	 * for an unknown user
	 */
	boolean allowsPushMarketing(UUID userId);

}
