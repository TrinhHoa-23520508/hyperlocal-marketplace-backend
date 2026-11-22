package vn.edu.uit.nearby.nearby_backend.identity.application.consent;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;

/**
 * The newest consent row of one purpose; {@code granted} is false when there is none or
 * it was withdrawn.
 */
public record ConsentStatus(ConsentPurpose purpose, boolean granted, @Nullable String policyVersion,
		@Nullable Instant grantedAt, @Nullable Instant withdrawnAt) {

	static ConsentStatus of(ConsentPurpose purpose, @Nullable UserConsent consent) {
		if (consent == null) {
			return new ConsentStatus(purpose, false, null, null, null);
		}
		return new ConsentStatus(purpose, consent.isActive(), consent.getPolicyVersion(), consent.getGrantedAt(),
				consent.getWithdrawnAt());
	}

}
